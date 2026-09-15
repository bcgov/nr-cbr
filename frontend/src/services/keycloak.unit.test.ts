import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/env', () => ({
  env: {
    VITE_KEYCLOAK_URL: 'https://dev.loginproxy.gov.bc.ca/auth/realms/standard',
    VITE_KEYCLOAK_CLIENT_ID: 'cbr-app',
    VITE_BASE_PATH: '',
  },
}));

const signinRedirect = vi.fn();
const signinSilent = vi.fn();
const signoutRedirect = vi.fn();
const removeUser = vi.fn();
const getUser = vi.fn();

vi.mock('oidc-client-ts', () => ({
  UserManager: vi.fn().mockImplementation((settings: unknown) => ({
    settings,
    signinRedirect,
    signinSilent,
    signoutRedirect,
    removeUser,
    getUser,
  })),
  WebStorageStateStore: vi.fn().mockImplementation((opts: unknown) => opts),
}));

import type { User } from 'oidc-client-ts';

/**
 * keycloak.ts keeps three module-level singletons — the UserManager, `renewInFlight` and
 * `lastRenewAt` — and the last of those is a 5-second debounce so a page load firing several
 * requests does not mint several tokens. That state is correct in production and poisonous across
 * tests: a renewal in one leaves the debounce armed for the next, which then silently skips its own.
 * Loading the module fresh per test isolates all three.
 */
const loadKeycloak = async () => {
  vi.resetModules();
  return import('./keycloak');
};

const nowSeconds = () => Math.floor(Date.now() / 1000);
const userExpiringIn = (seconds: number) =>
  ({ access_token: 'tok', expires_at: nowSeconds() + seconds }) as User;

beforeEach(() => {
  vi.clearAllMocks();
  getUser.mockResolvedValue(null);
});

describe('UserManager settings', () => {
  it('configures PKCE against the issuer, with tokens in sessionStorage', async () => {
    const { getUserManager } = await loadKeycloak();
    const manager = getUserManager() as unknown as { settings: Record<string, unknown> };

    expect(manager.settings).toMatchObject({
      authority: 'https://dev.loginproxy.gov.bc.ca/auth/realms/standard',
      client_id: 'cbr-app',
      response_type: 'code',
      scope: 'openid profile email',
      // Renewal is driven explicitly so an API call and the idle-timeout keepalive share one code
      // path rather than racing a background timer.
      automaticSilentRenew: false,
      monitorSession: false,
    });
  });

  it('is created once and reused', async () => {
    const { getUserManager } = await loadKeycloak();

    expect(getUserManager()).toBe(getUserManager());
  });

  it('derives both redirect URIs from the window origin, not configuration', async () => {
    const { REDIRECT_URI, POST_LOGOUT_REDIRECT_URI } = await loadKeycloak();
    // This is what lets ONE built image serve PR previews, TEST and PROD. Each of these exact URLs
    // still has to be registered on the CSS integration.
    expect(REDIRECT_URI).toBe(`${window.location.origin}/authCallback`);
    expect(POST_LOGOUT_REDIRECT_URI).toBe(window.location.origin);
  });
});

describe('needsRenewal', () => {
  it('treats a missing user or a missing token as needing renewal', async () => {
    const { needsRenewal } = await loadKeycloak();
    expect(needsRenewal(null)).toBe(true);
    expect(needsRenewal(undefined)).toBe(true);
    expect(needsRenewal({} as User)).toBe(true);
  });

  it('is true inside the margin and false outside it', async () => {
    const { needsRenewal, REFRESH_MARGIN_SECONDS } = await loadKeycloak();
    expect(needsRenewal(userExpiringIn(REFRESH_MARGIN_SECONDS - 5))).toBe(true);
    expect(needsRenewal(userExpiringIn(REFRESH_MARGIN_SECONDS + 60))).toBe(false);
  });

  it('treats an already-expired token as needing renewal', async () => {
    const { needsRenewal } = await loadKeycloak();
    expect(needsRenewal(userExpiringIn(-30))).toBe(true);
  });

  it('does not renew when expiry is unknown', async () => {
    const { needsRenewal } = await loadKeycloak();
    // No expires_at means we cannot say it is stale; renewing on every call would hammer the realm.
    expect(needsRenewal({ access_token: 'tok' } as User)).toBe(false);
  });
});

describe('ensureFreshUser', () => {
  it('returns the current user untouched when the token is fresh', async () => {
    const { ensureFreshUser } = await loadKeycloak();
    const fresh = userExpiringIn(600);
    getUser.mockResolvedValue(fresh);

    await expect(ensureFreshUser()).resolves.toBe(fresh);
    expect(signinSilent).not.toHaveBeenCalled();
  });

  it('returns null when there is no session, without attempting a renewal', async () => {
    const { ensureFreshUser } = await loadKeycloak();
    getUser.mockResolvedValue(null);

    await expect(ensureFreshUser()).resolves.toBeNull();
    expect(signinSilent).not.toHaveBeenCalled();
  });

  it('renews a stale token and returns the new user', async () => {
    const { ensureFreshUser } = await loadKeycloak();
    const renewed = userExpiringIn(600);
    getUser.mockResolvedValue(userExpiringIn(5));
    signinSilent.mockResolvedValue(renewed);

    await expect(ensureFreshUser()).resolves.toBe(renewed);
    expect(signinSilent).toHaveBeenCalledTimes(1);
  });

  it('returns null rather than throwing when the refresh token has expired', async () => {
    const { ensureFreshUser } = await loadKeycloak();
    // The caller decides whether that means "sign out" or "let the request 401" — this must not
    // surface as an unhandled rejection.
    getUser.mockResolvedValue(userExpiringIn(5));
    signinSilent.mockRejectedValue(new Error('refresh token expired'));

    await expect(ensureFreshUser()).resolves.toBeNull();
  });

  it('shares one renewal between concurrent callers', async () => {
    const { ensureFreshUser } = await loadKeycloak();
    // A page load fires several requests at once; each must not mint its own token.
    getUser.mockResolvedValue(userExpiringIn(5));
    signinSilent.mockResolvedValue(userExpiringIn(600));

    await Promise.all([ensureFreshUser(), ensureFreshUser(), ensureFreshUser()]);

    expect(signinSilent).toHaveBeenCalledTimes(1);
  });
});

describe('forceRenew', () => {
  it('renews unconditionally, even on a fresh token', async () => {
    const { forceRenew } = await loadKeycloak();
    const renewed = userExpiringIn(900);
    signinSilent.mockResolvedValue(renewed);

    await expect(forceRenew()).resolves.toBe(renewed);
    expect(signinSilent).toHaveBeenCalledTimes(1);
  });

  it('propagates the failure so the caller can treat it as a real expiry', async () => {
    const { forceRenew } = await loadKeycloak();
    signinSilent.mockRejectedValue(new Error('refresh token expired'));

    await expect(forceRenew()).rejects.toThrow('refresh token expired');
  });
});

describe('signIn', () => {
  it.each([
    ['idir', 'azureidir'],
    ['bceid', 'bceidbusiness'],
  ] as const)('sends %s straight to the %s broker', async (provider, hint) => {
    const { signIn } = await loadKeycloak();
    // azureidir, not idir: the CSS integration selects IDIR - MFA, which federates via Azure AD.
    // An unrecognised kc_idp_hint is silently ignored, so a wrong value still appears to work.
    await signIn(provider);

    expect(signinRedirect).toHaveBeenCalledWith({ extraQueryParams: { kc_idp_hint: hint } });
  });
});

describe('signOutRedirect', () => {
  it('ends the session at the realm', async () => {
    const { signOutRedirect } = await loadKeycloak();
    signoutRedirect.mockResolvedValue(undefined);

    await signOutRedirect();

    expect(signoutRedirect).toHaveBeenCalledTimes(1);
    // NOT removeUser first: oidc-client-ts reads id_token_hint off the stored user to tell Keycloak
    // which session to end, and removes it itself once the redirect is under way.
    expect(removeUser).not.toHaveBeenCalled();
  });

  it('clears the local session when the redirect itself fails', async () => {
    const { signOutRedirect, POST_LOGOUT_REDIRECT_URI } = await loadKeycloak();
    // Offline, or the realm unreachable — the user should at least be signed out on this device.
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign, origin: window.location.origin });
    signoutRedirect.mockRejectedValue(new Error('offline'));
    removeUser.mockResolvedValue(undefined);

    await signOutRedirect();

    expect(removeUser).toHaveBeenCalledTimes(1);
    expect(assign).toHaveBeenCalledWith(POST_LOGOUT_REDIRECT_URI);
    vi.unstubAllGlobals();
  });
});

describe('clearLocalSession', () => {
  it('removes the stored user', async () => {
    const { clearLocalSession } = await loadKeycloak();
    removeUser.mockResolvedValue(undefined);

    await clearLocalSession();

    expect(removeUser).toHaveBeenCalledTimes(1);
  });

  it('swallows a storage failure rather than breaking sign-out', async () => {
    const { clearLocalSession } = await loadKeycloak();
    // Private windows and blocked site data both throw from the storage accessor itself.
    removeUser.mockRejectedValue(new Error('storage unavailable'));

    await expect(clearLocalSession()).resolves.toBeUndefined();
  });
});
