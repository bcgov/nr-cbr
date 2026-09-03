import { defineConfig, devices } from '@playwright/test';

import { baseURL } from './e2e/utils';

/**
 * Playwright E2E config — runs against deployed DEV by default.
 *
 * Auth flow:
 *   1. `npm run e2e:login` runs the `setup` project headed, parks at the IDIR
 *      login page, and saves cookies + localStorage to e2e/.auth/user.json
 *      once you successfully sign in.
 *   2. All other projects start from that storageState so each test boots
 *      already-authenticated.
 *
 * Override the target with E2E_BASE_URL (e.g. http://localhost:3000 for local).
 */
export default defineConfig({
  timeout: 180_000,
  testDir: './e2e',
  // Serial execution. We share one Cognito refresh token via storageState
  // across runs; parallel workers race that refresh and intermittently leave
  // some contexts stuck on the white `<Loading>` overlay. Bump back up later
  // once we have a way to mint per-worker auth (or if we move to a mock
  // strategy that doesn't touch Cognito at all).
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [['line'], ['list', { printSteps: true }], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  // nr-frep runs a `setup` project (e2e/auth.setup.ts) that signs in through IDIR with
  // E2E_IDIR_USER / E2E_IDIR_PASSWORD and saves the session to STORAGE_STATE, then makes the main
  // project depend on it. That is the right shape for CBR too, but it cannot run until CBR has a
  // FAM/Cognito client — so the suite currently covers only unauthenticated surfaces. To restore
  // it: add e2e/auth.setup.ts, re-add the `setup` project below, and set
  // `storageState: STORAGE_STATE` plus `dependencies: ['setup']` on chromium.
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
