package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Monitors Summary's order — legacy's first, ascending. Its other, Road Name, Structure Id, is
 * gone: the road name is LRMOPS's, and CBR's database has none to sort on (2026-11-25). One order
 * left, so the screen no longer offers a choice; the parameter stays so a saved request still works.
 */
public enum MonitorsSummarySort {
  PROJECT_BRANCH_KM("Project Number, Branch, KM (Asc)");

  private final String label;

  MonitorsSummarySort(String label) {
    this.label = label;
  }

  /** As the report's criteria line prints it — legacy's wording. */
  public String label() {
    return label;
  }
}
