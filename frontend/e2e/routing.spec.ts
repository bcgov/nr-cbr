import { expect, test } from '@playwright/test';

/**
 * Routing an anonymous visitor sees, including the two routes that only exist because of the
 * Keycloak migration.
 */
test.describe('public routing', () => {
  test('an unknown path renders Not Found rather than a blank page', async ({ page }) => {
    await page.goto('/no-such-page');

    await expect(page.getByText(/not found/i).first()).toBeVisible();
  });

  test('a protected path sends an anonymous visitor to the landing page', async ({ page }) => {
    // Not a 404: the route exists, the visitor simply has no session. The public route set has no
    // /inventory entry, so the catch-all must not swallow it into Not Found.
    await page.goto('/inventory/site-search');

    await expect(page.getByTestId('landing-title')).toBeVisible();
  });

  test('/authCallback is served by the app, not the catch-all', async ({ page }) => {
    // ORDER IS LOAD-BEARING. The callback arrives as /authCallback?code=…&state=…, and the `*`
    // entry sits directly below it in the public table. If the catch-all ever wins, sign-in fails
    // with a Not Found page and an authorization code that has already been spent — a failure that
    // looks like a Keycloak problem and is not.
    await page.goto('/authCallback?code=fake-code&state=fake-state');

    // The exchange will fail (the code is not real). What matters is which page handled it: the
    // callback page, which shows either its loading state or its own error — never Not Found.
    await expect(page.getByText(/not found/i)).toHaveCount(0);
    await expect(
      page.getByTestId('auth-callback-loading').or(page.getByRole('alert')),
    ).toBeVisible();
  });

  test('the runtime config file is served', async ({ page }) => {
    // index.html loads /config.js before the bundle, and env.ts merges window.config over the
    // build-time vars. A missing file 404s silently and every VITE_* value falls back — which is
    // how "No authority or metadataUrl configured" happens in a deployed environment.
    const response = await page.goto('/config.js');

    expect(response?.status()).toBe(200);
    expect(await response?.text()).toContain('window.config');
  });
});
