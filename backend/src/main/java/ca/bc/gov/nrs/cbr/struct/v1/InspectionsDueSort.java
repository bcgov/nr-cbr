package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Inspections Due reports' "Sort By": legacy's two, each within the forest district and then
 * by branch, kilometre and user kilometre — legacy's {@code createSearch}.
 */
public enum InspectionsDueSort {
  FOREST_SERVICE_ROAD("Forest Service Road"),
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
