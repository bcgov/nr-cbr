package ca.bc.gov.nrs.cbr.struct.v1;

import lombok.Builder;

/**
 * The Repairs Summary report's criteria (legacy CBR005), as query parameters. Every one optional.
 *
 * <p>Repair Status, Repair Priority and Structural Safety take a code or one of legacy's groups of
 * them: {@code OUT} (Outstanding — Suggested, Required or Carried Forward), {@code SOR} (Suggested
 * or Required), and for priority and safety any codes joined with {@code &}, e.g. {@code U&H&M}.
 */
@Builder
public record RepairsSummaryCriteria(
    String structureName,
    String siteId,
    String siteStatusCode,
    String siteTypeCode,
    String clientNumber,
    String clientLocationCode,
    String repairStatusCode,
    String repairPriorityCode,
    String repairClassCode,
    String orgUnit,
    String businessAreaOrgUnit,
    String managementOrgUnit,
    String forestServiceRoad,
    String forestFileId,
    String roadSectionId,
    Boolean downrated,
    Boolean mostRecentInspections,
    RepairsSummarySort sortBy) {

  /** Outstanding: suggested, required or carried forward — legacy's default. */
  public static final String OUTSTANDING = "OUT";

  /** Suggested or required. */
  public static final String SUGGESTED_OR_REQUIRED = "SOR";

  /** What separates the codes of a group of priorities or of classes. */
  public static final String GROUP_SEPARATOR = "&";

  /** The order chosen, or legacy's default: project, branch and kilometre. */
  public RepairsSummarySort sortOrDefault() {
    return sortBy == null ? RepairsSummarySort.PROJECT_BRANCH_KM : sortBy;
  }
}
