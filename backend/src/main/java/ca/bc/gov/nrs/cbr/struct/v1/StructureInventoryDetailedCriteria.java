package ca.bc.gov.nrs.cbr.struct.v1;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/**
 * The Structure Inventory – Detailed report's criteria (legacy CBR007), bound from query
 * parameters.
 *
 * <p>Field-for-field with legacy's {@code StructureInventoryDetailReportForm} and with the
 * frontend's {@code StructureInventoryDetailedCriteria}: the Summary's criteria less its
 * construction filters. Every criterion is optional — with none set, the report covers every
 * active structure in the province, as legacy's does.
 */
@Builder
public record StructureInventoryDetailedCriteria(
    String clientNumber,
    String clientLocationCode,
    String siteStatusCode,
    Boolean downrated,
    String siteTypeCode,
    @Pattern(regexp = SiteSearchCriteria.DECIMAL, message = StructureSearchCriteria.INVALID_KILOMETRES)
    String kiloStart,
    @Pattern(regexp = SiteSearchCriteria.DECIMAL, message = StructureSearchCriteria.INVALID_KILOMETRES)
    String kiloEnd,
    String orgUnit,
    String businessAreaOrgUnit,
    String managementOrgUnit,
    String forestFileId,
    String roadSectionId,
    /** "Year Built" on the form — {@code YEAR_BUILT}. */
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String yearBuiltStart,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String yearBuiltEnd,
    String specialAccessCode,
    Boolean capitalRoad,
    String portableSuperstructureStatusCode,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String loadRestrictionYearStart,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String loadRestrictionYearEnd,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String replacementYearStart,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String replacementYearEnd,
    /** Legacy's default when unset: project, branch, user km. */
    StructureInventorySort sortBy
) implements StructureInventoryCriteria {}
