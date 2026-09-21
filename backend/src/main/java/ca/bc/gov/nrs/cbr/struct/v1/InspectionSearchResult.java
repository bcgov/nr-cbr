package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * One row of the Inspection Search results table.
 *
 * <p>The ten columns the legacy table renders, plus the status code, which legacy reads but does not
 * print. Field names match the frontend's `InspectionSearchResult` so the JSON maps across without a
 * translation step.
 *
 * <p>The code earns its place because two of the row's behaviours turn on it and neither is
 * derivable from the description: an {@code OFL} row is not a link, and it is the only row that
 * offers delete.
 *
 * @param id                                 {@code STRUCTURE_INSPECTION.INSPECTION_ID}
 * @param inspectionDate                     ISO {@code yyyy-MM-dd}; the table prints it as {@code yyyy/MM/dd}
 * @param inspectionReportStatusCode         the raw code — decides the pill colour, the link and delete
 * @param inspectionReportStatusDescription  the decoded status, which is what the pill reads
 * @param siteAtTimeOfInspection             where the structure stood when inspected, which is not necessarily where it stands now
 * @param structureName                      {@code CROSSING_STRUCTURE.CROSSING_STRUCTURE_NAME}
 * @param orgUnitCode                        shown in the District Code column
 * @param orgUnitName                        the column's tooltip in legacy, a suffix here
 * @param forestServiceRoad                  {@code CBR_ROAD_SECTION_VW.ROAD_SECT_NAME}
 * @param pointOfCommencementDistance        the KM column
 * @param crossingName                       free-text name of the crossing, may be blank
 * @param forestFileId                       rendered with {@code roadSectionId} as "Project File ID#-Br."
 * @param roadSectionId                      the "Br." half of that pair
 */
public record InspectionSearchResult(
    String id,
    String inspectionDate,
    String inspectionReportStatusCode,
    String inspectionReportStatusDescription,
    String siteAtTimeOfInspection,
    String structureName,
    String orgUnitCode,
    String orgUnitName,
    String forestServiceRoad,
    String pointOfCommencementDistance,
    String crossingName,
    String forestFileId,
    String roadSectionId
) {}
