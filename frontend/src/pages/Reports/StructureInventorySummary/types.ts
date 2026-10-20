import type { ReportFormat, StructureInventorySort } from '../types';

export { SORT_OPTIONS, type ReportFormat, type StructureInventorySort } from '../types';

/**
 * The Structure Inventory – Summary report's criteria (legacy CBR008).
 *
 * <p>Field-for-field from legacy's `structure_inventory_summary.jsp`, less two: the Area filter (the
 * retired ministry Areas, left out as on the search pages) and Save/Delete Report Favourite, left
 * out for now. Every criterion is optional — legacy runs the report with none set.
 */
export type StructureInventorySummaryCriteria = {
  /** Designated Maintainer — a client picked from the lookup. */
  clientNumber: string;
  clientLocationCode: string;
  siteStatusCode: string;
  /** Downrated Structure? */
  downrated: boolean;
  siteTypeCode: string;
  structureTypeClassCode: string;
  kiloStart: string;
  kiloEnd: string;
  /** Forest District. Changing it clears {@link managementOrgUnit}. */
  orgUnit: string;
  businessAreaOrgUnit: string;
  managementOrgUnit: string;
  forestFileId: string;
  /** "Br." — the road section, beside Project File ID#. */
  roadSectionId: string;
  /** Year Superstructure Installed, yyyy. */
  yearBuiltStart: string;
  yearBuiltEnd: string;
  specialAccessCode: string;
  capitalRoad: boolean;
  portableSuperstructureStatusCode: string;
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
  /** Estimated Load Restriction, yyyy — legacy's "full log haul" replacement year. */
  loadRestrictionYearStart: string;
  loadRestrictionYearEnd: string;
  /** Estimated Replacement, yyyy — legacy's "light vehicle" replacement year. */
  replacementYearStart: string;
  replacementYearEnd: string;

  sortBy: StructureInventorySort;

  /** The label of the maintainer picked. <b>Display state, not a criterion.</b> */
  maintainerLabel: string;
};

/** PDF to print, CSV to work with — a button each. Legacy offered PDF, Word and Excel. */
export const FORMATS: readonly ReportFormat[] = ['CSV', 'PDF'];

/** Legacy's defaults: nothing set, sorted by project, branch and user km. */
export const EMPTY_CRITERIA: StructureInventorySummaryCriteria = {
  clientNumber: '',
  clientLocationCode: '',
  siteStatusCode: '',
  downrated: false,
  siteTypeCode: '',
  structureTypeClassCode: '',
  kiloStart: '',
  kiloEnd: '',
  orgUnit: '',
  businessAreaOrgUnit: '',
  managementOrgUnit: '',
  forestFileId: '',
  roadSectionId: '',
  yearBuiltStart: '',
  yearBuiltEnd: '',
  specialAccessCode: '',
  capitalRoad: false,
  portableSuperstructureStatusCode: '',
  culvertTypeCode: '',
  superstructureTypeCode: '',
  culvertMaterialCode: '',
  deckTypeCode: '',
  openingWidthMin: '',
  openingWidthMax: '',
  loadRestrictionYearStart: '',
  loadRestrictionYearEnd: '',
  replacementYearStart: '',
  replacementYearEnd: '',
  sortBy: 'PROJECT_BRANCH_USER_KM',
  maintainerLabel: '',
};
