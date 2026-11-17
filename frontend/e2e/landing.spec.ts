import { expect, test, type Page } from '@playwright/test';

/**
 * The unauthenticated landing page — everything reachable without a session.
 *
 * <p>These run against a real browser and a real build, so they cover what the component tests
 * cannot: that the SPA boots at all, that the Keycloak client is configured (an unset
 * `VITE_KEYCLOAK_URL` throws "No authority or metadataUrl configured" the moment a login button is
 * pressed), and that the router serves the public route set to an anonymous visitor.
 *
 * <p>Sign-in itself is not exercised — see the note in playwright.config.ts. The assertions below
 * stop at the authorization request to the realm, which is the last thing under this
 * application's control.
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
    // The link is only rendered when VITE_ACCESS_REQUEST_EMAIL is configured. Deployed environments
    // set it from the ACCESS_REQUEST_EMAIL GitHub variable; a local .env usually leaves it blank.
    await expect(page.getByTestId('landing-title')).toBeVisible();
    const link = page.getByTestId('landing-request-access');
    test.skip((await link.count()) === 0, 'VITE_ACCESS_REQUEST_EMAIL is not set on this target');

    await link.click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('link')).toHaveAttribute('href', /^mailto:.+@.+/);
    // CBR has no region or client scoping, so the role is the only thing to choose — and the
    // requester is not the person who provisions it, hence business names rather than CBR_* strings.
    await expect(dialog).toContainText('Read only');
    await expect(dialog).toContainText('Inspector Level 1');
    await expect(dialog).toContainText('Inspector Level 2');
    await expect(dialog).toContainText('Professional Engineer');
    await expect(dialog).toContainText('Administrator');
  });

  /**
   * Waits for the authorization request to the BC Gov realm and returns its URL.
   *
   * <p>The request is asserted on rather than the page URL: with a `kc_idp_hint` Keycloak forwards
   * straight on to the identity provider (Azure AD for IDIR), so the browser never settles on
   * loginproxy. Also the cheapest check that the Keycloak client is configured: with
   * VITE_KEYCLOAK_URL unset, oidc-client-ts throws before any navigation.
   */
  const signInRequest = async (page: Page, button: string): Promise<URL> => {
    const request = page.waitForRequest(
      (req) =>
        req.isNavigationRequest() &&
        /loginproxy\.gov\.bc\.ca\/.*\/protocol\/openid-connect\/auth/.test(req.url()),
      { timeout: 15_000 },
    );
    await page.getByTestId(button).click();
    return new URL((await request).url());
  };

  test('signing in with IDIR goes to the BC Gov realm with the IDIR broker hint', async ({
    page,
  }) => {
    const url = await signInRequest(page, 'landing-button__idir');

    expect(url.searchParams.get('response_type')).toBe('code');
    // PKCE, public client, no secret.
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
    // azureidir, not idir: the integration selects IDIR - MFA, which federates via Azure AD.
    expect(url.searchParams.get('kc_idp_hint')).toBe('azureidir');
    expect(url.searchParams.get('redirect_uri')).toContain('/authCallback');
  });

  test('signing in with Business BCeID goes to the BC Gov realm with the BCeID broker hint', async ({
    page,
  }) => {
    const url = await signInRequest(page, 'landing-button__bceid');

    expect(url.searchParams.get('response_type')).toBe('code');
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
    expect(url.searchParams.get('kc_idp_hint')).toBe('bceidbusiness');
    expect(url.searchParams.get('redirect_uri')).toContain('/authCallback');
  });
});
