package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * One row of the Site Search results table.
 *
 * <p>The seven columns the legacy `site_search.jsp` renders, in its order. Field names match the
 * frontend's `SiteSearchResult` so the JSON maps across without a translation step.
 *
 * <p>The status is carried twice, as code and description, and both are used: the description is
 * the label, the code decides the colour of the pill it sits in. Deriving a colour from the
 * description would key the UI to text the business can reword in a code table at any time.
 *
 * @param id                            {@code CROSSING_SITE.CROSSING_SITE_ID} — links to the detail screen
 * @param orgUnitCode                   shown in the District Code column
 * @param orgUnitName                   the column's tooltip; the code alone does not identify a district
 * @param forestServiceRoad             the road the crossing sits on
 * @param pointOfCommencementDistance   the KM column
 * @param crossingName                  free-text name, may be blank
 * @param forestFileId                  rendered with {@code roadSectionId} as "Project File ID#-Br."
 * @param roadSectionId                 the "Br." half of that pair
 * @param crossingSiteStatusCode        the raw status code — {@code ACT}, {@code PP}, {@code BAR}…
 * @param crossingSiteStatusDescription the decoded status, which is what the column shows
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
    String crossingSiteStatusCode,
    String crossingSiteStatusDescription
) {}
