import {
  EMPTY_COMMON_CRITERIA,
  type ReportFormat,
  type StructureInventoryCommonCriteria,
} from '../types';

export { SORT_OPTIONS, type ReportFormat, type StructureInventorySort } from '../types';

/**
 * The Structure Inventory – Summary report's criteria (legacy CBR008).
 *
 * <p>Field-for-field from legacy's `structure_inventory_summary.jsp`, less two: the Area filter (the
 * retired ministry Areas, left out as on the search pages) and Save/Delete Report Favourite, left
 * out for now. The criteria the Detailed shares, then the Summary's own construction filters.
 * Every criterion is optional — legacy runs the report with none set.
 */
export type StructureInventorySummaryCriteria = StructureInventoryCommonCriteria & {
  structureTypeClassCode: string;
  /** From the culvert record, so it only ever matches a culvert. */
  culvertTypeCode: string;
  /** From the bridge record, so it only ever matches a bridge. */
  superstructureTypeCode: string;
  /** From the culvert record. */
  culvertMaterialCode: string;
  /** From the bridge record. */
  deckTypeCode: string;
  /** The culvert's opening width, in whole millimetres. */
  openingWidthMin: string;
  openingWidthMax: string;
};

/** PDF to print, CSV to work with — a button each. Legacy offered PDF, Word and Excel. */
export const FORMATS: readonly ReportFormat[] = ['CSV', 'PDF'];

/** Legacy's defaults: nothing set, sorted by project, branch and user km. */
export const EMPTY_CRITERIA: StructureInventorySummaryCriteria = {
  ...EMPTY_COMMON_CRITERIA,
  structureTypeClassCode: '',
  culvertTypeCode: '',
  superstructureTypeCode: '',
  culvertMaterialCode: '',
  deckTypeCode: '',
  openingWidthMin: '',
  openingWidthMax: '',
};
