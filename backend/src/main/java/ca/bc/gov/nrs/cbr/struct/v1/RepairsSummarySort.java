package ca.bc.gov.nrs.cbr.struct.v1;

/** The Repairs Summary's "Sort By" — legacy's three orders, every one ascending. */
public enum RepairsSummarySort {
  PROJECT_BRANCH_KM("Project Number, Branch, KM (Asc)"),
  ROAD_NAME_USER_KM("Road Name, KM User"),
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
