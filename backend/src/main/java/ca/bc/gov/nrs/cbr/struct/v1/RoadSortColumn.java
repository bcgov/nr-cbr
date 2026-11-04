package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The road search dialog's columns a user can sort by, one per header — {@code sortBy} on
 * {@code GET /api/v1/roads/search}.
 *
 * <p>New in CBR: legacy's dialog has fixed headers and one fixed order — road name, then file,
 * then branch. That order is still what the search answers with when no column is chosen, and it
 * breaks every tie when one is.
 */
public enum RoadSortColumn {
  /** Forest Service Road — the section's name. */
  FOREST_SERVICE_ROAD,
  /** Project File ID#. */
  PROJECT_FILE,
  /** Br. — the road section. */
  BRANCH,
  /** Tenure Type. */
  TENURE_TYPE,
  /** Client Name — the tenure holder's. Legacy's order alone when the holder cannot be read. */
  CLIENT_NAME,
  /** Client # — the tenure holder's. Legacy's order alone when the holder cannot be read. */
  CLIENT_NUMBER
}
