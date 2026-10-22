package ca.bc.gov.nrs.cbr.specification.v1;

import java.math.BigDecimal;

/**
 * One structure as the Replacement Structures Summary lists it, with its site — what
 * {@link ReplacementStructuresReportSpecifications#lines} selects. Its org units and maintainer
 * stay numbers: their names are read once, beside the query.
 *
 * @param endOfDesignLife {@code ORIGINAL_REPLACEMENT_DATE}, a year
 * @param loadRestrictionYear {@code FULL_LOG_HAUL_TRFFC_RPLCMNT_DT}, a year
 * @param replacementYear {@code LIGHT_VEHICLE_TRFFC_RPLCMNT_DT}, a year
 * @param closureYear {@code ESTIMATED_CLOSURE_DATE}, a year
 */
public record ReplacementStructureLine(
    Long structureId,
    String structureName,
    String structureTypeClassCode,
    BigDecimal designLoadRating,
    BigDecimal currentLoadRating,
    Integer endOfDesignLife,
    Long estimatedReplacementCost,
    Integer loadRestrictionYear,
    Integer replacementYear,
    Integer closureYear,
    String siteId,
    String forestFileId,
    String roadSectionId,
    String roadSectionName,
    BigDecimal kilometres,
    String crossingName,
    String clientNumber,
    String capitalRoadInd,
    String siteStatusCode,
    Long districtOrgUnitNo,
    Long regionOrgUnitNo,
    Long managementOrgUnitNo) {}
