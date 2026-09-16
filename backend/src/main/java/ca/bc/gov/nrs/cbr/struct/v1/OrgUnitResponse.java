package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * An org-unit entry for the Forest District and Management Area dropdowns.
 *
 * <p>Two fields because those are the two the legacy form binds —
 * {@code <html:options collection="forestDistricts" property="orgUnitNo"
 * labelProperty="orgUnitName"/>}. The district <em>code</em> ("DCK") is not here: it is shown in
 * the search results table, where it comes from the matched site's own row, not from this list.
 *
 * @param orgUnitNo   the org-unit number — the submitted value, e.g. {@code "1809"}
 * @param orgUnitName the display name, e.g. {@code "Chilliwack Natural Resource District"}
 */
public record OrgUnitResponse(String orgUnitNo, String orgUnitName) {}
