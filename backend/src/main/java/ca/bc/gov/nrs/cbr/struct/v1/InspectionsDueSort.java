package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Inspections Due reports' order: within the forest district, then by branch, kilometre and
 * user kilometre — legacy's {@code createSearch}.
 *
 * <p>Legacy's other order, Forest Service Road, is gone: the road name is LRMOPS's, and CBR's
 * database has none to sort on (2026-11-25). One order left, so the screen no longer offers a
 * choice; the parameter stays so a saved request still works.
 */
public enum InspectionsDueSort {
  FOREST_FILE_ID("Project File ID#");

  private final String label;

  InspectionsDueSort(String label) {
    this.label = label;
  }

  /** As the reports' criteria lines print it — legacy's wording. */
  public String label() {
    return label;
  }
}
