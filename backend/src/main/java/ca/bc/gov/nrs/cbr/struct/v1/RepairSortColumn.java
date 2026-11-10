package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The Repairs tab's columns a user can sort by, one per header — {@code sortBy} on
 * {@code GET /api/v1/structures/{structureId}/repairs}.
 *
 * <p>New in CBR: legacy's table has fixed headers and one fixed order. That order is still what
 * the tab answers with when no column is chosen, and it breaks every tie when one is.
 */
public enum RepairSortColumn {
  /** Repair Number. */
  NUMBER,
  /** Status — by its description, which is what the column shows. */
  STATUS,
  /** Repair Type — by its description, which is what the column shows. */
  TYPE,
  /** User Audits — by its latest step: completed, else required, else suggested. */
  USER_AUDITS,
  /** Inspection Date — the inspection that raised the repair. */
  INSPECTION_DATE,
  /** Priority — by its rank in {@code REPAIR_PRIORITY_CODE_ORDER}, as the default order. */
  PRIORITY,
  /** Completed Date. */
  COMPLETED_DATE,
  /** Estimate ($). */
  ESTIMATE,
  /** Actual ($). */
  ACTUAL_COST,
  /** Quantity. */
  QUANTITY,
  /** Description. */
  DESCRIPTION
}
