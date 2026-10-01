package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Inspection Search results columns a user can sort by, one per header — {@code sortBy} on
 * {@code GET /api/v1/inspections/search}.
 *
 * <p>Replaces legacy's "Sort by" radio, whose two fixed orderings this screen no longer offers. Its
 * default — structure name, then the newest inspection first — is still what a search answers with
 * when no header is chosen, and it breaks every tie when one is.
 */
public enum InspectionSortColumn {
  /** Id — the inspection's own number. */
  INSPECTION_ID,
  /** District Code — the site's org unit code. */
  DISTRICT,
  /** Forest Service Road — the road section's name. */
  FOREST_SERVICE_ROAD,
  /** Inspection Date. */
  INSPECTION_DATE,
  /** Site # — the site the structure stood at when it was inspected. */
  SITE_ID,
  /** Structure Name. */
  STRUCTURE_NAME,
  /** KM — the site's point of commencement distance. */
  KILOMETRES,
  /** Crossing Name. */
  CROSSING_NAME,
  /** Project File ID#-Br. — the file, then the branch. */
  PROJECT_FILE,
  /** Status — the current status's description, which is what the column shows. */
  STATUS
}
