import { act, fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { Link, RouterProvider, createMemoryRouter, useNavigate } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { UnsavedChangesProvider } from '@/context/unsavedChanges/UnsavedChangesProvider';

import UnsavedChangesGuard from './index';

import { WORK_SAVED } from '@/context/unsavedChanges/workSaved';
import { useUnsavedChangesPrompt } from '@/hooks/useUnsavedChangesPrompt';

/** A screen that declares unsaved work, as a real page does, and offers a way off itself. */
const Editor = ({ dirty }: { dirty: boolean }) => {
  useUnsavedChangesPrompt(dirty);
  return (
    <>
      {/* Mounted here as Layout mounts it for every screen. */}
      <UnsavedChangesGuard />
      <p>Editor</p>
      <Link to="/elsewhere">Go elsewhere</Link>
    </>
  );
};

/**
 * A screen that saves and leaves in one step, as Add Site does: it marks itself clean and navigates
 * in the same handler, before the guard has seen the clean flag.
 */
const SavingEditor = () => {
  const [saved, setSaved] = useState(false);
  const navigate = useNavigate();
  useUnsavedChangesPrompt(!saved);
  return (
    <>
      <UnsavedChangesGuard />
      <p>Editor</p>
      <button
        type="button"
        onClick={() => {
          setSaved(true);
          navigate('/elsewhere', { state: WORK_SAVED });
        }}
      >
        Save
      </button>
    </>
  );
};

const renderApp = async (dirty: boolean, editor = <Editor dirty={dirty} />) => {
  const router = createMemoryRouter(
    [
      { path: '/', element: editor },
      { path: '/elsewhere', element: <p>Elsewhere</p> },
    ],
    { initialEntries: ['/'] },
  );
  await act(async () => {
    render(
      // Above the router, where the real provider sits — the flag has to outlive the navigation
      // it is blocking.
      <UnsavedChangesProvider>
        <RouterProvider router={router} />
      </UnsavedChangesProvider>,
    );
  });
};

/**
 * Whether the dialog is actually open.
 *
 * <p>Carbon keeps a closed modal mounted and toggles `is-visible` on it, and the stylesheet that
 * acts on that class is not loaded here — so neither `toBeInTheDocument` nor `toBeVisible` can
 * tell the two states apart. The class is the honest signal.
 */
const dialogIsOpen = () =>
  document.querySelector('.cds--modal')?.classList.contains('is-visible') ?? false;

const leavePage = async () => {
  await act(async () => {
    fireEvent.click(screen.getByText('Go elsewhere'));
  });
};

describe('UnsavedChangesGuard', () => {
  it('lets a screen leave without asking once it has saved, even in the same step', async () => {
    // Add Site's Save: success, then straight to the stored site. The screen's "clean" arrives a
    // render later than the navigation, so the navigation says so itself.
    await renderApp(true, <SavingEditor />);

    await act(async () => {
      fireEvent.click(screen.getByText('Save'));
    });

    expect(screen.getByText('Elsewhere')).toBeInTheDocument();
    expect(dialogIsOpen()).toBe(false);
  });

  it('lets a screen with nothing to lose be left without a word', async () => {
    await renderApp(false);

    await leavePage();

    expect(screen.getByText('Elsewhere')).toBeInTheDocument();
    expect(dialogIsOpen()).toBe(false);
  });

  it('stops a navigation that would discard unsaved work', async () => {
    // The whole point of doing this at the router rather than on a Cancel button: this link is one
    // nobody wired a confirmation to, and it is still caught.
    await renderApp(true);

    await leavePage();

    expect(dialogIsOpen()).toBe(true);
    expect(screen.getByText('Leave without saving?')).toBeInTheDocument();
    expect(screen.getByText('Editor')).toBeInTheDocument();
    expect(screen.queryByText('Elsewhere')).not.toBeInTheDocument();
  });

  it('stays put, with the page intact, when the user says so', async () => {
    await renderApp(true);
    await leavePage();

    await act(async () => {
      fireEvent.click(screen.getByText('Stay on this page'));
    });

    expect(screen.getByText('Editor')).toBeInTheDocument();
    expect(dialogIsOpen()).toBe(false);
  });

  it('completes the navigation the user asked for once they confirm', async () => {
    await renderApp(true);
    await leavePage();

    await act(async () => {
      fireEvent.click(screen.getByText('Leave'));
    });

    expect(screen.getByText('Elsewhere')).toBeInTheDocument();
  });

  it('warns the browser about closing the tab as well', async () => {
    // useBlocker cannot see a tab close or a reload; beforeunload is the only thing that can.
    await renderApp(true);

    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
  });

  it('leaves the tab alone when there is nothing to lose', async () => {
    await renderApp(false);

    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(false);
  });
});
