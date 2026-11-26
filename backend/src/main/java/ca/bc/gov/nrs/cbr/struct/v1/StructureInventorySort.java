package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Structure Inventory reports' "Sort By" — legacy's four orders, every one ascending.
 *
 * <p>{@link #STRUCTURE_ID} is labelled "Structure Id" on the form, as legacy's is, but sorts on the
 * structure's name ({@code CROSSING_STRUCTURE_NAME}), as legacy's {@code STRUCTURE_NAME_SORT} does:
 * the number users know a structure by.
 *
 * <p>No road-name order: the road name is LRMOPS's, and CBR's database has none to sort on
 * (2026-11-25).
 */
public enum StructureInventorySort {
  PROJECT_BRANCH_USER_KM("Project Number, Branch, User KM (Asc)"),
  SITE_ID("Site Id (Asc)"),
  STRUCTURE_ID("Structure Name (Asc)");

  private final String label;

  StructureInventorySort(String label) {
    this.label = label;
  }

  /** As the report's criteria line prints it — legacy's wording. */
  public String label() {
    return label;
  }
}
