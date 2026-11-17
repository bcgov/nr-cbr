import { join } from 'node:path';

/**
 * Playwright helpers, trimmed to what the CBR suite needs today.
 *
 * `baseURL` is resolved from E2E_BASE_URL (or BASE_URL) so the same suite runs against local dev, a
 * PR preview and TEST — CI passes the deployed host in (see .github/workflows/reusable-tests.yml).
 */
export const baseURL = process.env.E2E_BASE_URL ?? process.env.BASE_URL ?? 'http://localhost:3000';

/**
 * Where the signed-in browser state is persisted so tests start already authenticated.
 *
 * Unused until an `auth.setup.ts` performs the sign-in — see the note in playwright.config.ts.
 */
export const STORAGE_STATE = join(process.cwd(), 'e2e', '.auth', 'user.json');
