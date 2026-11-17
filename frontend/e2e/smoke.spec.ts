import { expect, test } from '@playwright/test';

/**
 * Smoke test: the app shell renders and the unauthenticated landing page is reachable.
 *
 * Deliberately does not sign in — see the note in playwright.config.ts on adding an auth setup
 * project with E2E_IDIR_USER / E2E_IDIR_PASSWORD.
 */
test('landing page renders', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/CBR/i);
});
