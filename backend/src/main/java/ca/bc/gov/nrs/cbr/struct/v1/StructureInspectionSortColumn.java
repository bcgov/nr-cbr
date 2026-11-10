package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The structure page's Inspections tab columns a user can sort by, one per header —
 * {@code sortBy} on {@code GET /api/v1/structures/{structureId}/inspections}.
 *
 * <p>New in CBR: legacy's table has fixed headers and one fixed order — newest first, an undated
 * one before them all. That order is still what the tab answers with when no column is chosen, and
 * it breaks every tie when one is.
 */
public enum StructureInspectionSortColumn {
  /** Inspection Date. */
  INSPECTION_DATE,
  /** Inspection Type — by its description, which is what the column shows. */
  TYPE,
  /** Site # — the site the structure stood at when it was inspected. */
  SITE_ID,
  /** Inspection Report Status — the current status's description. */
  STATUS,
  /** Reviewed Date. */
  REVIEWED_DATE,
  /** Reviewed By — the reviewer's first name, then last, as the column shows them. */
  REVIEWED_BY,
  /** Inspector Name. */
  INSPECTOR_NAME
}
