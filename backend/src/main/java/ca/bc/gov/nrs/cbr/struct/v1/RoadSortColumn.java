package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The road search dialog's columns a user can sort by, one per header — {@code sortBy} on
 * {@code GET /api/v1/roads/search}.
 *
 * <p>With none chosen the order is road name, then Project File ID#, then Br., which also breaks
 * every tie when one is.
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
  /** Client Name — the tenure holder's. */
  CLIENT_NAME
}
