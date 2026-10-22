import type { MaintainerCriteria } from '../MaintainerFields';

export type { ReportFormat } from '../types';

/**
 * The Replacement Structures Summary report's criteria (legacy CBR009).
 *
 * <p>Field-for-field from legacy's `replacement_structure_summary.jsp`, less two: Area (the retired
 * ministry Areas, left out as on the other reports) and Save/Delete Report Favourite. Legacy offers
 * no Sort By — its stored procedure fixes the order — and neither does this. Every criterion is
 * optional.
 */
export type ReplacementStructuresSummaryCriteria = MaintainerCriteria & {
  /** Forest District. Changing it clears {@link managementOrgUnit}. */
  orgUnit: string;
  managementOrgUnit: string;
  siteStatusCode: string;
  siteTypeCode: string;
  businessAreaOrgUnit: string;
  /** Estimated Load Restriction Date, years `yyyy` — legacy's full log haul traffic date. */
  loadRestrictionYearStart: string;
  loadRestrictionYearEnd: string;
  /** Estimated Replacement Date, years `yyyy` — legacy's light vehicle traffic date. */
  replacementYearStart: string;
  replacementYearEnd: string;
  /** Capital Road: on for capital roads only; off for any road. */
  capitalRoad: boolean;
  /** The order is fixed; kept for the form, unshown. */
  sortBy: 'DEFAULT';
};

/** PDF to print, CSV to work with — a button each. Legacy offered PDF, Word and Excel. */
export const FORMATS = ['CSV', 'PDF'] as const;

/** Legacy's defaults: nothing set. */
export const EMPTY_CRITERIA: ReplacementStructuresSummaryCriteria = {
  orgUnit: '',
  managementOrgUnit: '',
  siteStatusCode: '',
  siteTypeCode: '',
  businessAreaOrgUnit: '',
  loadRestrictionYearStart: '',
  loadRestrictionYearEnd: '',
  replacementYearStart: '',
  replacementYearEnd: '',
  capitalRoad: false,
  clientNumber: '',
  clientLocationCode: '',
  maintainerLabel: '',
  sortBy: 'DEFAULT',
};
