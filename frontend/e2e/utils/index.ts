import { join } from 'node:path';

/**
 * Playwright helpers. Ported from nr-frep, trimmed to what the CBR suite needs today.
 *
 * `baseURL` is resolved from BASE_URL so the same suite runs against local dev, a PR preview and
 * TEST — CI passes the deployed host in (see .github/workflows/reusable-tests.yml).
 */
export const baseURL = process.env.E2E_BASE_URL ?? process.env.BASE_URL ?? 'http://localhost:3000';

/**
 * Where the signed-in browser state is persisted so tests start already authenticated.
 *
 * Unused until CBR has a FAM client and an `auth.setup.ts` that performs the IDIR sign-in — see the
 * note in playwright.config.ts. Defined now because the config references it and nr-frep's
 * setup-project wiring drops straight in once the client exists.
 */
export const STORAGE_STATE = join(process.cwd(), 'e2e', '.auth', 'user.json');
