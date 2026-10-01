package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Site Search results columns a user can sort by, one per header — {@code sortBy} on
 * {@code GET /api/v1/sites/search}.
 *
 * <p>New in CBR: legacy's results table has fixed headers and one fixed order. That order is still
 * what a search answers with when no column is chosen, and it breaks every tie when one is — see
 * {@code SiteSearchSpecifications.fetchAndOrder}.
 */
public enum SiteSortColumn {
  /** Site #. */
  SITE_ID,
  /** District Code — the org unit's code. */
  DISTRICT,
  /** Forest Service Road — the road section's name. */
  FOREST_SERVICE_ROAD,
  /** KM — the point of commencement distance. */
  KILOMETRES,
  /** Crossing Name. */
  CROSSING_NAME,
  /** Project File ID#-Br. — the file, then the branch. */
  PROJECT_FILE,
  /** Status — by its description, which is what the column shows. */
  STATUS
}
