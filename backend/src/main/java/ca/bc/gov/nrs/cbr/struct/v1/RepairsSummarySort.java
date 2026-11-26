package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Repairs Summary's "Sort By" — legacy's orders, every one ascending.
 *
 * <p>No road-name order: the road name is LRMOPS's, and CBR's database has none to sort on
 * (2026-11-25).
 */
public enum RepairsSummarySort {
  PROJECT_BRANCH_KM("Project Number, Branch, KM (Asc)"),
  SITE_ID("Site Id");

  private final String label;

  RepairsSummarySort(String label) {
    this.label = label;
  }

  /** As the report's criteria line prints it — legacy's wording. */
  public String label() {
    return label;
  }
}
