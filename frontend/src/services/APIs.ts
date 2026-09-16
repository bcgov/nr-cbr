import { ConfigurationService } from './configuration.service';
import { SiteSearchService } from './siteSearch.service';

import type { APIConfig } from '@/config/api/types';

import { ensureSessionFresh } from '@/context/auth/refreshSession';
import { env } from '@/env';
import { ensureFreshUser } from '@/services/keycloak';

const basePath = (env.VITE_BASE_PATH ?? '').replace(/\/$/, '');

export const BackendApiConfig: APIConfig = {
  BASE: env.VITE_BACKEND_URL || `${basePath}/api`,
  VERSION: '0',
  // The backend runs stateless with CSRF disabled and `allowCredentials: false`, so nothing is
  // carried in a cookie — the bearer token below is the whole of the authentication.
  WITH_CREDENTIALS: false,
  CREDENTIALS: 'omit',
  TOKEN: undefined,
  USERNAME: undefined,
  PASSWORD: undefined,
  HEADERS: undefined,
  ENCODE_PATH: undefined,
};

/**
 * Resolve the access token for the current session.
 *
 * <p>Renews a near-expiry token first, so a request fired after the page has been open a while does
 * not race a 401; if the session is fully expired, `ensureSessionFresh` signs out and redirects to
 * login. Returns an empty string when there is no session at all, so the request goes
 * unauthenticated and the backend answers 401 — which `catchErrorCodes` turns back into a sign-out
 * — rather than this throwing somewhere with no context.
 */
BackendApiConfig.TOKEN = async () => {
  try {
    await ensureSessionFresh();
    const current = await ensureFreshUser();
    return current?.access_token ?? '';
  } catch {
    return '';
  }
};

/**
 * The backend API clients, one per controller.
 *
 * <p>Constructed once at module load and shared, so every call goes through the same axios instance
 * and the same token resolver. nr-frep's `APIs.ts` in miniature — it will grow a service per
 * controller as the screens land.
 */
const API = {
  configuration: new ConfigurationService(BackendApiConfig),
  siteSearch: new SiteSearchService(BackendApiConfig),
} as const;

export default API;
