import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const completeSignIn = vi.fn();
const navigate = vi.fn();

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: () => ({ completeSignIn }),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigate };
});

import AuthCallbackPage from './index';

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

beforeEach(() => {
  vi.clearAllMocks();
});

describe('AuthCallbackPage', () => {
  it('shows a loading state while the authorization code is exchanged', () => {
    completeSignIn.mockReturnValue(new Promise(() => {}));

    render(<AuthCallbackPage />);

    expect(screen.getByTestId('auth-callback-loading')).toBeTruthy();
  });

  it('exchanges the code and replaces the callback URL with home', async () => {
    completeSignIn.mockResolvedValue(undefined);

    render(<AuthCallbackPage />);
    await flush();

    expect(completeSignIn).toHaveBeenCalledTimes(1);
    // `replace`, not push: the callback URL still carries ?code=&state=, and the Back button must
    // not return to it — the code is single-use and the second exchange fails.
    expect(navigate).toHaveBeenCalledWith('/', { replace: true });
  });

  it('exchanges the code only once even when mounted twice', async () => {
    // React StrictMode mounts every component twice in development. Without the ref guard the
    // second mount spends an already-redeemed code and sign-in fails — in dev only, which is a
    // memorable way to lose an afternoon.
    completeSignIn.mockResolvedValue(undefined);

    const { rerender } = render(<AuthCallbackPage />);
    rerender(<AuthCallbackPage />);
    await flush();

    expect(completeSignIn).toHaveBeenCalledTimes(1);
  });

  it('shows a recoverable error instead of navigating when the exchange fails', async () => {
    completeSignIn.mockRejectedValue(new Error('invalid_grant'));

    render(<AuthCallbackPage />);
    // The error is React state set from a rejected promise, so wait for the re-render rather than
    // for the microtask queue alone.
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(screen.getByText('Sign-in could not be completed')).toBeTruthy();
    // A way back to a working page — not a dead end on a URL whose code is already spent.
    expect(screen.getByRole('link', { name: 'Return to CBR' })).toBeTruthy();
    expect(navigate).not.toHaveBeenCalled();
  });
});
