package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * An Area for the reports' Area dropdown — one of the ministry's three old forest regions, which
 * {@code CBR_ORG_UNIT} hard-codes and rolls each district up into (legacy's
 * {@code oldForestRegions}).
 *
 * <p>Carries its current districts, so the form can narrow the Forest District list to them once
 * an Area is picked, as legacy's did.
 *
 * @param orgUnitNo   the Area's org-unit number — the submitted value, e.g. {@code "1833"}
 * @param orgUnitCode the short code
 * @param orgUnitName the display name
 * @param districts   the org-unit numbers of the current forest districts in it, as the Forest
 *                    District dropdown's values
 */
public record AreaResponse(
    String orgUnitNo, String orgUnitCode, String orgUnitName, List<String> districts) {

  public AreaResponse {
    districts = districts == null ? List.of() : List.copyOf(districts);
  }
}
