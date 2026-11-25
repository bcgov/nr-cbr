package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Inspections Conducted reports' "Sort By": the Summary's four orders and the Detail's two of
 * its own (both have Inspection Date). The Summary's are legacy's as they were — Forest Service
 * Road and Project File ID# descending, though their labels do not say so.
 *
 * <p>No road-name orders (legacy's Forest Service Road, and the Detail's Road Name, User KM): the
 * road name is LRMOPS's, and CBR's database has none to sort on (2026-11-25).
 */
public enum InspectionsConductedSort {
  FOREST_FILE_ID("Project File ID#"),
  USER_KM("User Km"),
  INSPECTION_DATE("Inspection Date"),
  PROJECT_BRANCH_USER_KM("Project Number, Branch, User KM (Asc)");

  private final String label;

  InspectionsConductedSort(String label) {
    this.label = label;
  }

  /** As the reports' criteria lines print it — legacy's wording. */
  public String label() {
    return label;
  }
}
