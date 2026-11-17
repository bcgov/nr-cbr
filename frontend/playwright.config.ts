import { defineConfig, devices } from '@playwright/test';

import { baseURL } from './e2e/utils';

/**
 * Playwright E2E config.
 *
 * Targets E2E_BASE_URL (or BASE_URL), defaulting to http://localhost:3000 — start `npm run dev`
 * first, or point it at a deployed host. The suite covers unauthenticated surfaces only; see the
 * note on `projects` below.
 */
export default defineConfig({
  timeout: 180_000,
  testDir: './e2e',
  // Serial execution. Kept for when a signed-in session is shared via storageState: parallel
  // workers would race the one Keycloak refresh token and leave some contexts stuck on the
  // `<Loading>` overlay. The unauthenticated suite would run in parallel today.
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

  // Signed-in screens are not covered yet. The usual shape is a `setup` project (e2e/auth.setup.ts)
  // that signs in through the BC Gov Keycloak realm with E2E_IDIR_USER / E2E_IDIR_PASSWORD, saves
  // the session to STORAGE_STATE, and is a dependency of the main project. To add it: write
  // e2e/auth.setup.ts, add the `setup` project below, and set `storageState: STORAGE_STATE` plus
  // `dependencies: ['setup']` on chromium.
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
