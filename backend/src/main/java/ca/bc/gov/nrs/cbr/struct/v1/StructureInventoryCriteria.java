package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The criteria the Structure Inventory reports share — the Summary's and the Detailed's forms
 * alike — so the filters, the order and the criteria line are written once for both.
 *
 * <p>The Summary adds its construction filters ({@link StructureInventorySummaryCriteria}); the
 * Detailed has these alone ({@link StructureInventoryDetailedCriteria}).
 */
public interface StructureInventoryCriteria {

  /** Designated Maintainer — the site's client. */
  String clientNumber();

  String clientLocationCode();

  String siteStatusCode();

  /** "Downrated Structure?" — legacy's {@code downloadRated} as the reports define it. */
  Boolean downrated();

  String siteTypeCode();

  String kiloStart();

  String kiloEnd();

  /** Forest District. */
  String orgUnit();

  /** BCTS Business Area. */
  String businessAreaOrgUnit();

  String managementOrgUnit();

  String forestFileId();

  /** "Br." — the road section. */
  String roadSectionId();

  /** The year range on {@code YEAR_BUILT}. */
  String yearBuiltStart();

  String yearBuiltEnd();

  String specialAccessCode();

  Boolean capitalRoad();

  /** From the bridge record. */
  String portableSuperstructureStatusCode();

  /** "Estimated Load Restriction" — {@code FULL_LOG_HAUL_TRFFC_RPLCMNT_DT}. */
  String loadRestrictionYearStart();

  String loadRestrictionYearEnd();

  /** "Estimated Replacement" — {@code LIGHT_VEHICLE_TRFFC_RPLCMNT_DT}. */
  String replacementYearStart();

  String replacementYearEnd();

  StructureInventorySort sortBy();

  /** The order asked for, or legacy's default: project, branch, user km. */
  default StructureInventorySort sortOrDefault() {
    return sortBy() == null ? StructureInventorySort.PROJECT_BRANCH_USER_KM : sortBy();
  }
}
