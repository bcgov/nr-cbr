import {
  EMPTY_COMMON_CRITERIA,
  type ReportFormat,
  type StructureInventoryCommonCriteria,
} from '../types';

export type { ReportFormat, StructureInventorySort } from '../types';

/**
 * The Structure Inventory – Detailed report's criteria (legacy CBR007).
 *
 * <p>Field-for-field from legacy's `structure_inventory_detailed.jsp` — the Summary's criteria less
 * its six construction filters (type/class, superstructure, deck, culvert type and material,
 * opening width) — and, as on the Summary, less the Area filter and the Report Favourites: the
 * criteria the two share, and no more. Every criterion is optional: legacy runs the report with
 * none set.
 */
export type StructureInventoryDetailedCriteria = StructureInventoryCommonCriteria;

/** As legacy: PDF to print, CSV to work with, KMZ to see on a map. */
export const FORMATS: readonly ReportFormat[] = ['CSV', 'KMZ', 'PDF'];

/** Legacy's defaults: nothing set, sorted by project, branch and user km. */
export const EMPTY_CRITERIA: StructureInventoryDetailedCriteria = EMPTY_COMMON_CRITERIA;
