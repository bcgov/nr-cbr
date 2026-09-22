package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * One road section, as the site form reads it.
 *
 * @param forestFileId      "Project File ID#" on the form
 * @param roadSectionId     "Br." on the form
 * @param forestServiceRoad the section's name — what the form displays. May be null: the view
 *                          carries sections whose name upstream is blank
 * @param orgUnitNo         the org unit the road implies, which the form writes into Forest
 *                          District. <b>It is the road's {@code FOREST_REGION}</b> — legacy's
 *                          {@code OracleRoadSegmentDAO} maps that column to {@code ORG_UNIT_NO}
 *                          and {@code SiteAction} assigns it to the district. Carried faithfully;
 *                          see {@code CbrRoadSectionEntity#forestRegion}
 */
public record RoadSectionResponse(
    String forestFileId, String roadSectionId, String forestServiceRoad, Long orgUnitNo) {}
