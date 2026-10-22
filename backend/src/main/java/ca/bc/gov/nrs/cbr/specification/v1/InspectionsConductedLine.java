package ca.bc.gov.nrs.cbr.specification.v1;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One inspection as the Inspections Conducted – Summary lists it, with its structure and site —
 * what {@link InspectionsConductedReportSpecifications#lines} selects. Its type and current status
 * stay codes: their descriptions are read once, beside the query.
 */
public record InspectionsConductedLine(
    Long structureId,
    String structureName,
    String siteId,
    String roadSectionName,
    BigDecimal kilometres,
    BigDecimal userKm,
    String crossingName,
    Long inspectionId,
    LocalDate inspectionDate,
    String inspectionTypeCode,
    String reportStatusCode) {
}
