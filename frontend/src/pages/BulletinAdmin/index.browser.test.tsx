import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import BulletinAdminPage, { REQUIRED, TOO_LONG } from './index';

const bulletinApi = vi.hoisted(() => ({
  bulletin: vi.fn(),
  saveBulletin: vi.fn(),
  previewBulletin: vi.fn(),
}));

vi.mock('@/services/APIs', () => ({
  default: { bulletin: bulletinApi },
}));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const display = vi.hoisted(() => vi.fn());
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));

const SAVED = {
  message: 'Road <b>closed</b>',
  updatedBy: 'IDIR\\ADMIN',
  updatedAt: '2026-10-20T09:30:00',
};

/** Drafts and what the server keeps of them — the cleaning itself is the server's, tested there. */
const PREVIEWS: Record<string, string> = {
  'Bridge <i>open</i><script>x</script>': 'Bridge <i>open</i>',
};

const renderPage = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/admin/bulletin-message']}>
        <BulletinAdminPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const byId = (field: string) => screen.getByTestId(`bulletin-admin-${field}`);
const type = (value: string) => fireEvent.change(byId('message'), { target: { value } });

beforeEach(() => {
  display.mockReset();
  bulletinApi.bulletin.mockReset();
  bulletinApi.saveBulletin.mockReset();
  bulletinApi.previewBulletin.mockReset();
  bulletinApi.bulletin.mockResolvedValue(SAVED);
  // The server's answers for the drafts these cases type; any other comes back as it went.
  bulletinApi.previewBulletin.mockImplementation((message: string) =>
    Promise.resolve({ message: PREVIEWS[message] ?? message }),
  );
});

describe('BulletinAdminPage', () => {
  it('starts from the saved message, and says who saved it', async () => {
    renderPage();

    expect(await screen.findByTestId('bulletin-admin-message')).toHaveValue('Road <b>closed</b>');
    expect(byId('updated')).toHaveTextContent('Last saved by IDIR\\ADMIN on Oct 20, 2026 9:30 AM');
  });

  it('saves the message, then shows what the server kept', async () => {
    bulletinApi.saveBulletin.mockResolvedValue({ ...SAVED, message: 'New' });
    renderPage();
    await screen.findByTestId('bulletin-admin-message');

    type('New <script>x</script>');
    fireEvent.click(byId('save'));

    await waitFor(() =>
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: 'Bulletin saved' }),
      ),
    );
    expect(bulletinApi.saveBulletin).toHaveBeenCalledWith('New <script>x</script>');
    expect(byId('message')).toHaveValue('New');
  });

  it('asks for a message, and one that fits, beside the box, and saves nothing until then', async () => {
    renderPage();
    await screen.findByTestId('bulletin-admin-message');

    type('  ');
    expect(screen.queryByText(REQUIRED)).not.toBeInTheDocument();
    fireEvent.blur(byId('message'));
    expect(screen.getByText(REQUIRED)).toBeInTheDocument();

    type('x'.repeat(2001));
    fireEvent.click(byId('save'));

    expect(await screen.findByText(TOO_LONG)).toBeInTheDocument();
    expect(bulletinApi.saveBulletin).not.toHaveBeenCalled();
  });

  it("shows the server's refusal beside the box", async () => {
    bulletinApi.saveBulletin.mockRejectedValue({
      status: 400,
      body: { detail: 'x', fieldErrors: { message: 'Bulletin Message is required.' } },
    });
    renderPage();
    await screen.findByTestId('bulletin-admin-message');

    type('<script>x</script>');
    fireEvent.click(byId('save'));

    expect(await screen.findByText('Bulletin Message is required.')).toBeInTheDocument();
    expect(display).not.toHaveBeenCalled();
  });

  it('puts back the saved message on Cancel', async () => {
    renderPage();
    await screen.findByTestId('bulletin-admin-message');

    type('Draft');
    fireEvent.click(byId('cancel'));

    expect(byId('message')).toHaveValue('Road <b>closed</b>');
  });

  it('starts empty when there is no bulletin, and says when it could not be loaded', async () => {
    bulletinApi.bulletin.mockResolvedValue({ message: '', updatedBy: null, updatedAt: null });
    const { unmount } = renderPage();

    expect(await screen.findByTestId('bulletin-admin-message')).toHaveValue('');
    expect(screen.queryByTestId('bulletin-admin-updated')).not.toBeInTheDocument();
    unmount();

    bulletinApi.bulletin.mockRejectedValue(new Error('down'));
    renderPage();

    expect(await screen.findByTestId('bulletin-admin-load-error')).toBeInTheDocument();
    expect(screen.queryByTestId('bulletin-admin-form')).not.toBeInTheDocument();
  });

  it('previews the draft beside the box, as the server would keep it, once typing pauses', async () => {
    renderPage();

    expect((await screen.findByTestId('bulletin-admin-preview-bulletin-message')).innerHTML).toBe(
      'Road <b>closed</b>',
    );

    type('Bridge <i>open</i><script>x</script>');

    await waitFor(() =>
      expect(screen.getByTestId('bulletin-admin-preview-bulletin-message').innerHTML).toBe(
        'Bridge <i>open</i>',
      ),
    );
    expect(bulletinApi.previewBulletin).toHaveBeenLastCalledWith(
      'Bridge <i>open</i><script>x</script>',
    );
    expect(bulletinApi.saveBulletin).not.toHaveBeenCalled();
  });

  it('asks for no preview of an empty draft, and says when one is unavailable', async () => {
    renderPage();
    await screen.findByTestId('bulletin-admin-preview-bulletin-message');
    bulletinApi.previewBulletin.mockClear();

    type('');
    expect(await screen.findByText('Nothing to preview yet.')).toBeInTheDocument();

    bulletinApi.previewBulletin.mockRejectedValue(new Error('down'));
    type('New');
    expect(await screen.findByTestId('bulletin-admin-preview-error')).toBeInTheDocument();
    expect(bulletinApi.previewBulletin).not.toHaveBeenCalledWith('');
  });

  it('labels the preview as the message box is, and makes it at least as tall', async () => {
    renderPage();
    const box = await screen.findByTestId('bulletin-admin-message');

    await waitFor(() => expect(byId('preview').style.minBlockSize).toBe(`${box.offsetHeight}px`));
    expect(screen.getByText('Preview')).toHaveClass('cds--label');
  });
});
