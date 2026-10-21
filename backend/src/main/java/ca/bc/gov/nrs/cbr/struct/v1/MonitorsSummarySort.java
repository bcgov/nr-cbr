package ca.bc.gov.nrs.cbr.struct.v1;

/** The Monitors Summary's "Sort By" — legacy's two orders, both ascending. */
public enum MonitorsSummarySort {
  PROJECT_BRANCH_KM("Project Number, Branch, KM (Asc)"),
  ROAD_NAME_STRUCTURE("Road Name, Structure Id");

  private final String label;

  MonitorsSummarySort(String label) {
    this.label = label;
  }

  /** As the report's criteria line prints it — legacy's wording. */
  public String label() {
    return label;
  }
}
