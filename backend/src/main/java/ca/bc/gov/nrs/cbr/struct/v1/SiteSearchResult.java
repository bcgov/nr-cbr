package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * One row of the Site Search results table.
 *
 * <p>The seven columns the legacy `site_search.jsp` renders, in its order. Field names match the
 * frontend's `SiteSearchResult` so the JSON maps across without a translation step.
 *
 * @param id                            {@code CROSSING_SITE.CROSSING_SITE_ID} — links to the detail screen
 * @param orgUnitCode                   shown in the District Code column
 * @param orgUnitName                   the column's tooltip; the code alone does not identify a district
 * @param forestServiceRoad             the road the crossing sits on
 * @param pointOfCommencementDistance   the KM column
 * @param crossingName                  free-text name, may be blank
 * @param forestFileId                  rendered with {@code roadSectionId} as "Project File ID#-Br."
 * @param roadSectionId                 the "Br." half of that pair
 * @param crossingSiteStatusDescription the decoded status, not the code
 */
public record SiteSearchResult(
    String id,
    String orgUnitCode,
    String orgUnitName,
    String forestServiceRoad,
    String pointOfCommencementDistance,
    String crossingName,
    String forestFileId,
    String roadSectionId,
    String crossingSiteStatusDescription
) {}
