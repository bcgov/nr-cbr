package ca.bc.gov.nrs.cbr.struct.v1;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/**
 * The Structure Inventory – Summary report's criteria (legacy CBR008), bound from query parameters.
 *
 * <p>Field-for-field with legacy's {@code StructureInventorySummaryReportForm} and with the
 * frontend's {@code StructureInventorySummaryCriteria}. Every criterion is optional: with none set,
 * the report lists every active structure in the province, as legacy's does. Legacy's Area filter
 * (the retired ministry Areas) is not carried over.
 */
@Builder
public record StructureInventorySummaryCriteria(
    /** Designated Maintainer — the site's client. */
    String clientNumber,
    String clientLocationCode,
    String siteStatusCode,
    /**
     * "Downrated Structure?" — legacy's {@code downloadRated} as the reports define it: rated below
     * its design, or at 0, or not at all, or marked for review; at an active or barricaded site; and
     * a bridge or a wood-log culvert.
     */
    Boolean downrated,
    String siteTypeCode,
    String structureTypeClassCode,
    @Pattern(regexp = SiteSearchCriteria.DECIMAL, message = StructureSearchCriteria.INVALID_KILOMETRES)
    String kiloStart,
    @Pattern(regexp = SiteSearchCriteria.DECIMAL, message = StructureSearchCriteria.INVALID_KILOMETRES)
    String kiloEnd,
    /** Forest District. */
    String orgUnit,
    /** BCTS Business Area. */
    String businessAreaOrgUnit,
    String managementOrgUnit,
    String forestFileId,
    /** "Br." — the road section. */
    String roadSectionId,
    /** "Year Superstructure Installed" — {@code YEAR_BUILT}. */
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String yearBuiltStart,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String yearBuiltEnd,
    String specialAccessCode,
    Boolean capitalRoad,
    /** From the bridge record. */
    String portableSuperstructureStatusCode,
    /** From the culvert record. */
    String culvertTypeCode,
    /** From the bridge record. */
    String superstructureTypeCode,
    /** From the culvert record. */
    String culvertMaterialCode,
    /** From the bridge record. */
    String deckTypeCode,
    /** The culvert's opening width, whole millimetres — {@code OPENING_WIDTH}. */
    @Pattern(regexp = OPENING_WIDTH, message = INVALID_OPENING_WIDTH)
    String openingWidthMin,
    @Pattern(regexp = OPENING_WIDTH, message = INVALID_OPENING_WIDTH)
    String openingWidthMax,
    /** "Estimated Load Restriction" — {@code FULL_LOG_HAUL_TRFFC_RPLCMNT_DT}. */
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String loadRestrictionYearStart,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String loadRestrictionYearEnd,
    /** "Estimated Replacement" — {@code LIGHT_VEHICLE_TRFFC_RPLCMNT_DT}. */
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String replacementYearStart,
    @Pattern(regexp = StructureSearchCriteria.YEAR, message = StructureSearchCriteria.INVALID_YEAR)
    String replacementYearEnd,
    /** Legacy's default when unset: project, branch, user km. */
    StructureInventorySort sortBy
) {

  /** An unset value, or whole millimetres — legacy's {@code ######}. */
  public static final String OPENING_WIDTH = "^$|^\\d{1,6}$";

  static final String INVALID_OPENING_WIDTH = "Opening width must be whole millimetres, e.g. 1200";

  /** The order asked for, or legacy's default. */
  public StructureInventorySort sortOrDefault() {
    return sortBy == null ? StructureInventorySort.PROJECT_BRANCH_USER_KM : sortBy;
  }
}
