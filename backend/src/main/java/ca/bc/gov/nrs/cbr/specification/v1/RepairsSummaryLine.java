package ca.bc.gov.nrs.cbr.specification.v1;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One repair as the Repairs Summary prints it — in the PDF or the CSV — with the structure, site
 * and inspection it sits under: what {@link RepairsSummaryReportSpecifications#lines} selects. Codes stay codes: their
 * descriptions are read once, beside the query.
 */
public record RepairsSummaryLine(
    Long structureId,
    String structureName,
    String typeClassCode,
    BigDecimal currentLoadRating,
    LocalDate nextPlannedInspectionDate,
    String siteId,
    String forestFileId,
    String roadSectionId,
    String crossingName,
    String districtName,
    String roadSectionName,
    BigDecimal kilometres,
    BigDecimal userKm,
    BigDecimal longitude,
    BigDecimal latitude,
    String clientNumber,
    String clientLocationCode,
    Long inspectionId,
    LocalDate inspectionDate,
    String inspectionComment,
    String pengComment,
    Long repairNumber,
    String repairStatusCode,
    String repairPriorityCode,
    String repairTypeCode,
    Long repairQuantity,
    Long estimate,
    String description) {
}
