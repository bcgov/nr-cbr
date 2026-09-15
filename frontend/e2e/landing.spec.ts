import { expect, test } from '@playwright/test';

/**
 * The unauthenticated landing page — everything reachable without a session.
 *
 * <p>These run against a real browser and a real build, so they cover what the component tests
 * cannot: that the SPA boots at all, that the Keycloak client is configured (an unset
 * `VITE_KEYCLOAK_URL` throws "No authority or metadataUrl configured" the moment a login button is
 * pressed), and that the router serves the public route set to an anonymous visitor.
 *
 * <p>Sign-in itself is not exercised — see the note in playwright.config.ts about the `setup`
 * project. The assertions below stop at the redirect to the realm, which is the last thing under
 * this application's control.
 */
test.describe('landing page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('renders the application shell', async ({ page }) => {
    await expect(page).toHaveTitle(/CBR/i);
    await expect(page.getByTestId('landing-title')).toBeVisible();
    await expect(page.getByTestId('landing-subtitle')).toBeVisible();
  });

  test('offers both identity providers', async ({ page }) => {
    // Business BCeID is selected on the CSS integration but pending approval, so its hint currently
    // falls through to IDIR. The button is still offered — it starts working with no code change.
    await expect(page.getByTestId('landing-button__idir')).toBeVisible();
    await expect(page.getByTestId('landing-button__bceid')).toBeVisible();
  });

  test('the cover image loads', async ({ page }) => {
    // Guards the asset pipeline: a broken import still renders an <img>, just one with no pixels.
    const image = page.getByAltText('Landing cover');
    await expect(image).toBeVisible();
    await expect
      .poll(() => image.evaluate((node: HTMLImageElement) => node.naturalWidth))
      .toBeGreaterThan(0);
  });

  test('fonts do not 404', async ({ page }) => {
    // Carbon ships @font-face URLs prefixed `~@ibm/plex`, a webpack convention Vite does not
    // implement; unhandled, the dev server answers them with index.html and the browser reports
    // "OTS parsing error: invalid sfntVersion". Emission is disabled in styles/_overrides.scss.
    const fontRequests: string[] = [];
    page.on('request', (request) => {
      if (request.resourceType() === 'font') fontRequests.push(request.url());
    });

    await page.reload();

    expect(fontRequests.filter((url) => url.includes('~@ibm'))).toEqual([]);
  });

  test('"Request access" names the roles a person can ask for', async ({ page }) => {
    await page.getByTestId('landing-request-access').click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    // CBR has no region or client scoping, so the role is the only thing to choose — and the
    // requester is not the person who provisions it, hence business names rather than CBR_* strings.
    await expect(dialog).toContainText('Read only');
    await expect(dialog).toContainText('Inspector Level 1');
    await expect(dialog).toContainText('Inspector Level 2');
    await expect(dialog).toContainText('Professional Engineer');
    await expect(dialog).toContainText('Administrator');
  });

  test('signing in redirects to the BC Gov realm with the IDIR broker hint', async ({ page }) => {
    // The end of what this app controls. Also the cheapest possible check that the Keycloak client
    // is configured: with VITE_KEYCLOAK_URL unset, oidc-client-ts throws before any navigation.
    await page.getByTestId('landing-button__idir').click();

    await page.waitForURL(/loginproxy\.gov\.bc\.ca/, { timeout: 15_000 });

    const url = new URL(page.url());
    expect(url.pathname).toContain('/protocol/openid-connect/auth');
    expect(url.searchParams.get('response_type')).toBe('code');
    // PKCE, public client, no secret.
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
    // azureidir, not idir: the integration selects IDIR - MFA, which federates via Azure AD.
    expect(url.searchParams.get('kc_idp_hint')).toBe('azureidir');
    expect(url.searchParams.get('redirect_uri')).toContain('/authCallback');
  });
});
