package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Monitoring tab's columns a user can sort by, one per header — {@code sortBy} on
 * {@code GET /api/v1/structures/{structureId}/monitors}.
 *
 * <p>New in CBR: legacy's table has fixed headers and one fixed order. That order is still what
 * the tab answers with when no column is chosen, and it breaks every tie when one is.
 */
public enum MonitorSortColumn {
  /** Monitor Number. */
  NUMBER,
  /** Status — by its description, which is what the column shows. */
  STATUS,
  /** User Audits — by its latest step: completed, else required, else suggested. */
  USER_AUDITS,
  /** Inspection Date — the inspection that raised the item. */
  INSPECTION_DATE,
  /** Description. */
  DESCRIPTION,
  /** Monitoring Frequency — its description, then the comment shown after it. */
  FREQUENCY
}
