/**
 * Route paths referenced from more than one place.
 *
 * <p>Lives here rather than in `routes/routePaths.tsx` to avoid a cycle: routePaths imports every
 * page component, so a page importing back from it is circular — and the symptom is not an obvious
 * one. Vite resolves the cycle by leaving one module half-initialised, and the first thing to touch
 * it throws `Cannot access 'LandingPage' before initialization` at import time, failing the whole
 * module rather than the line that caused it.
 */

/**
 * Where a signed-in user lands: from `/`, from the OAuth callback once the session exists, and from
 * the header brand link.
 *
 * <p>CBR has no dashboard or home screen, so this points at the first real page. Revisit if one is
 * added.
 */
export const LANDING_AFTER_LOGIN = '/inventory/site-search';
