package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Structure Search results columns a user can sort by, one per header — {@code sortBy} on
 * {@code GET /api/v1/structures/search}.
 *
 * <p>New in CBR, as on Site Search: legacy's table has one fixed order. That order is still what a
 * search answers with when no column is chosen, and it breaks every tie when one is.
 */
public enum StructureSortColumn {
  /** Structure #. */
  STRUCTURE_NAME,
  /** Site #. */
  SITE_ID,
  /** District Code — the site's org unit code. */
  DISTRICT,
  /** Forest Service Road — the road section's name. */
  FOREST_SERVICE_ROAD,
  /** KM — the site's point of commencement distance. */
  KILOMETRES,
  /** Crossing Name. */
  CROSSING_NAME,
  /** Type/Class — by its description, which is what the column shows. */
  TYPE_CLASS,
  /** Project File ID#-Br. — the file, then the branch. */
  PROJECT_FILE,
  /**
   * Maintainer — by client number, then location: the column leads with them. The name comes from
   * {@code V_CLIENT_PUBLIC} after the page is read, so it cannot order the page.
   */
  MAINTAINER
}
