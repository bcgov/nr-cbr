package ca.bc.gov.nrs.cbr.specification.v1;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One structure as the Inspections Due reports list it, with its site and district — what
 * {@link InspectionsDueReportSpecifications#lines} selects; the Detailed also prints the year
 * built and where the site is. Its special access and equipment stay
 * codes: their descriptions are read once, beside the query.
 */
public record InspectionsDueLine(
    Long structureId,
    String structureName,
    String structureTypeClassCode,
    LocalDate nextPlannedInspectionDate,
    String specialEquipmentCode,
    String siteId,
    String forestFileId,
    String roadSectionId,
    String roadSectionName,
    BigDecimal kilometres,
    BigDecimal userKm,
    String crossingName,
    String specialAccessCode,
    Long districtOrgUnitNo,
    String districtName,
    Integer yearBuilt,
    BigDecimal latitude,
    BigDecimal longitude) {
}
