package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * What creating a site answers with: the number it was stored under, and nothing else.
 *
 * <p><b>Not the stored site.</b> The caller already holds everything it sent; the one thing it does
 * not know is the key, because the server upper-cases it ({@code INSERT_SITE} writes
 * {@code UPPER(P_SITE_ID)}). Returning the whole record meant reading the row straight back —
 * a second query, plus the maintainer lookup that goes with it — to tell the browser what it had
 * just said, and the screen it navigates to reads the site for itself anyway.
 *
 * <p>A record rather than a bare {@code String}: Spring sends a lone String through the plain-text
 * converter, which is not what a JSON client is asking for.
 *
 * @param siteId the {@code CROSSING_SITE_ID} as stored — upper-cased, whatever case was sent
 */
public record SiteCreatedResponse(String siteId) {}
