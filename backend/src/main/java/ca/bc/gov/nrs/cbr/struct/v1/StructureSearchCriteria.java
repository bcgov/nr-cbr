package ca.bc.gov.nrs.cbr.struct.v1;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/**
 * The criteria the Structure Search screen offers, bound from query parameters.
 *
 * <p>Field-for-field with the legacy {@code structure_search.jsp} form and
 * {@code StructureSearchForm.createSearch()}, and with the frontend's
 * {@code StructureSearchCriteria}, so a criterion keeps one name from the input box to the
 * {@code WHERE} clause. Bound as one object, for the reasons set out on
 * {@link SiteSearchCriteria}.
 *
 * <p><b>Designated Maintainer</b> is three fields, as on Site Search: a client number and location
 * picked from the client lookup, or a name typed into it that matches no single client.
 */
@Builder
public record StructureSearchCriteria(
    /** "Structure #", though the value is {@code CROSSING_STRUCTURE_NAME}. */
    String structureName,
    /** "Downrated Structure?" — a design rating above the current one, or no current one. */
    Boolean downrated,
    String structureTypeClassCode,
    /** "Portable Structure?" — a one-way filter, as every toggle here is. */
    Boolean portableStructure,
    String superstructureTypeCode,
    String structureCurbTypeCode,
    /** "Culvert Type" — {@code ENGINEERED_CULVERT_TYPE_CODE}. */
    String culvertTypeCode,
    /** "Incomplete Data?" */
    Boolean incomplete,
    String siteId,
    String siteStatusCode,
    String forestFileId,
    /** "Br." — the road section. */
    String roadSectionId,
    String siteTypeCode,
    String forestServiceRoad,
    @Pattern(regexp = SiteSearchCriteria.DECIMAL, message = INVALID_KILOMETRES)
    String kiloStart,
    @Pattern(regexp = SiteSearchCriteria.DECIMAL, message = INVALID_KILOMETRES)
    String kiloEnd,
    String crossingName,
    @Pattern(regexp = SiteSearchCriteria.DECIMAL, message = INVALID_USER_KILOMETRES)
    String userKmStart,
    @Pattern(regexp = SiteSearchCriteria.DECIMAL, message = INVALID_USER_KILOMETRES)
    String userKmEnd,
    /** Forest District. */
    String orgUnit,
    /**
     * "Include Archived Structure?" Off — the default — keeps to {@code ACTIVE_IND = 'Y'}; on drops
     * the filter. The one toggle that widens rather than narrows.
     */
    Boolean includeArchived,
    String managementOrgUnit,
    String clientNumber,
    String clientLocationCode,
    /** Designated Maintainer, by name rather than client number. */
    String primaryUserName,
    /** "Estimated Load Restriction" from, a year — {@code FULL_LOG_HAUL_TRFFC_RPLCMNT_DT}. */
    @Pattern(regexp = YEAR, message = INVALID_YEAR)
    String loadRestrictionYearStart,
    @Pattern(regexp = YEAR, message = INVALID_YEAR)
    String loadRestrictionYearEnd,
    /** "Estimated Replacement" from, a year — {@code LIGHT_VEHICLE_TRFFC_RPLCMNT_DT}. */
    @Pattern(regexp = YEAR, message = INVALID_YEAR)
    String replacementYearStart,
    @Pattern(regexp = YEAR, message = INVALID_YEAR)
    String replacementYearEnd,
    /** "Estimated Closure" from, a year — {@code ESTIMATED_CLOSURE_DATE}. */
    @Pattern(regexp = YEAR, message = INVALID_YEAR)
    String closureYearStart,
    @Pattern(regexp = YEAR, message = INVALID_YEAR)
    String closureYearEnd,
    /** "Year Superstructure Installed" from — {@code YEAR_BUILT}. */
    @Pattern(regexp = YEAR, message = INVALID_YEAR)
    String yearBuiltStart,
    @Pattern(regexp = YEAR, message = INVALID_YEAR)
    String yearBuiltEnd,
    String specialAccessCode,
    String specialEquipmentCode
) {

  /** An unset value, or a four-digit year — the columns are {@code NUMBER(4)}. */
  public static final String YEAR = "^$|^\\d{4}$";

  static final String INVALID_YEAR = "Enter a year as yyyy, e.g. 2030";
  static final String INVALID_KILOMETRES = "Kilometres must be a number, e.g. 12.5";
  static final String INVALID_USER_KILOMETRES = "User Kilometres must be a number, e.g. 12.5";
}
