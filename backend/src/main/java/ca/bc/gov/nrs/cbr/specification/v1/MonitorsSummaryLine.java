package ca.bc.gov.nrs.cbr.specification.v1;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One monitoring item as the Monitors Summary prints it, with the structure, site and inspection
 * it sits under — what {@link MonitorsSummaryReportSpecifications#lines} selects. The status stays
 * a code, the maintainer a number: their names are read once, beside the query.
 */
public record MonitorsSummaryLine(
    Long structureId,
    String structureName,
    String siteId,
    String siteTypeCode,
    String forestFileId,
    String roadSectionId,
    String crossingName,
    String roadSectionName,
    BigDecimal kilometres,
    String clientNumber,
    Long inspectionId,
    LocalDate inspectionDate,
    String inspectionComment,
    Long monitorNumber,
    String monitoringStatusCode,
    String description) {
}
