package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * An org-unit entry for the Forest District and Management Area dropdowns.
 *
 * <p>The legacy form binds two of these — {@code <html:options collection="forestDistricts"
 * property="orgUnitNo" labelProperty="orgUnitName"/>} — and shows the name alone. The code is
 * carried as well so the dropdown can read "DCK - Chilliwack Natural Resource District", which is
 * how nr-frep renders an org unit and how the search results table renders one. A user picking a
 * district in the form and reading one in the results should be looking at the same string.
 *
 * <p>{@code orgUnitNo} stays the submitted value: it is what {@code CROSSING_SITE.ORG_UNIT_NO}
 * holds, and the code is not unique across the whole org hierarchy.
 *
 * @param orgUnitNo   the org-unit number — the submitted value, e.g. {@code "1809"}
 * @param orgUnitCode the short code, e.g. {@code "DCK"}
 * @param orgUnitName the display name, e.g. {@code "Chilliwack Natural Resource District"}
 */
public record OrgUnitResponse(String orgUnitNo, String orgUnitCode, String orgUnitName) {}
