import { expect, test } from '@playwright/test';

/**
 * Smoke test: the app shell renders and the unauthenticated landing page is reachable.
 *
 * Deliberately does not sign in. nr-frep's suite authenticates programmatically with
 * E2E_IDIR_USER / E2E_IDIR_PASSWORD; wire the same here once CBR has a FAM client and protected
 * screens worth asserting on.
 */
test('landing page renders', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/CBR/i);
});
