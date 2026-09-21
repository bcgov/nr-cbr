import { render, screen } from '@testing-library/react';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import GlobalErrorPage from './index';

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

/**
 * What a user sees when a route throws.
 *
 * <p>Driven through a real router rather than by rendering the component alone, because
 * `useRouteError` only has an error to return when react-router put one there — rendering it
 * directly would test the empty case and pass.
 */
const Boom = () => {
  throw new Error("Cannot read properties of null (reading 'trim')");
};

const renderAfterAThrow = () => {
  const router = createMemoryRouter(
    [{ path: '/', element: <Boom />, errorElement: <GlobalErrorPage /> }],
    { initialEntries: ['/'] },
  );
  return render(<RouterProvider router={router} />);
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('GlobalErrorPage', () => {
  it('tells the user what happened instead of showing them the exception', async () => {
    // React-router's own screen makes the exception message the heading — "Cannot read properties
    // of null" — which means nothing to the person reading it and sounds like data loss.
    vi.spyOn(console, 'error').mockImplementation(() => {});
    renderAfterAThrow();

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByText(/Nothing you were looking at has been changed/)).toBeInTheDocument();
    expect(screen.queryByText(/Unexpected Application Error/)).toBeNull();
    expect(screen.queryByText(/Hey developer/)).toBeNull();
  });

  it('offers a way out, because the page that threw is already gone', async () => {
    // The router has unmounted the route, so the side nav went with it. Without these the screen
    // is a dead end and the only exit is the browser's own back button.
    vi.spyOn(console, 'error').mockImplementation(() => {});
    renderAfterAThrow();

    expect(await screen.findByTestId('global-error-back')).toBeInTheDocument();
    expect(screen.getByTestId('global-error-home')).toBeInTheDocument();
  });

  it('logs the error once, so a deployed failure leaves a trace', async () => {
    const logged = vi.spyOn(console, 'error').mockImplementation(() => {});
    renderAfterAThrow();
    await screen.findByText('Something went wrong');

    expect(
      logged.mock.calls.filter(([message]) => message === 'Unhandled route error'),
    ).toHaveLength(1);
  });
});
