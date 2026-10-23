package ca.bc.gov.nrs.cbr.struct.v1;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/**
 * The criteria of the Replacement Structures Summary report (legacy CBR009), as query parameters.
 * Every one optional.
 *
 * <p>Estimated Load Restriction ({@code FULL_LOG_HAUL_TRFFC_RPLCMNT_DT}) and Estimated Replacement
 * ({@code LIGHT_VEHICLE_TRFFC_RPLCMNT_DT}) are ranges of years, {@code yyyy}, both ends included,
 * as on the Structure Inventory reports. {@code capitalRoad} true asks for capital roads only.
 */
@Builder
public record ReplacementStructuresCriteria(
    String orgUnit,
    String managementOrgUnit,
    String siteStatusCode,
    String siteTypeCode,
    String businessAreaOrgUnit,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String loadRestrictionYearStart,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String loadRestrictionYearEnd,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String replacementYearStart,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String replacementYearEnd,
    Boolean capitalRoad,
    String clientNumber,
    String clientLocationCode) {}
