package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Inspections Conducted reports' "Sort By": the Summary's four orders and the Detail's two of
 * its own (both have Inspection Date). The Summary's are legacy's as they were — Forest Service
 * Road and Project File ID# descending, though their labels do not say so.
 */
public enum InspectionsConductedSort {
  FOREST_SERVICE_ROAD("Forest Service Road"),
  FOREST_FILE_ID("Project File ID#"),
  USER_KM("User Km"),
  INSPECTION_DATE("Inspection Date"),
  PROJECT_BRANCH_USER_KM("Project Number, Branch, User KM (Asc)"),
  ROAD_NAME_USER_KM("Road Name, User KM (Asc)");

  private final String label;

  InspectionsConductedSort(String label) {
    this.label = label;
  }

  /** As the reports' criteria lines print it — legacy's wording. */
  public String label() {
    return label;
  }
}
