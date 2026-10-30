import type { MaintainerCriteria } from '../MaintainerFields';
import type { SortOption } from '../ReportOutput';

export type { ReportFormat } from '../types';

/**
 * Which of the three reports: Inspections Due – Summary (legacy CBR004), – Detailed (legacy
 * CBR003), or Inspections – Blank Report (legacy CBR010).
 */
export type InspectionsDueVariant = 'summary' | 'detailed' | 'blank';

/** Where each page keeps its criteria for the tab. */
export const INSPECTIONS_DUE_STATE_KEYS: Record<InspectionsDueVariant, string> = {
  summary: 'cbr.report.inspectionsDueSummary',
  detailed: 'cbr.report.inspectionsDueDetailed',
  blank: 'cbr.report.inspectionsBlank',
};

/**
 * The Inspections Due reports' two orders, legacy's labels — each within the forest district, then
 * by branch and kilometre, as legacy sorts them.
 */
export type InspectionsDueSort = 'FOREST_SERVICE_ROAD' | 'FOREST_FILE_ID';

export const SORT_OPTIONS: readonly SortOption<InspectionsDueSort>[] = [
  { value: 'FOREST_SERVICE_ROAD', label: 'Forest Service Road' },
  { value: 'FOREST_FILE_ID', label: 'Project File ID#' },
];

/** Legacy's Close Proximity select: either, Yes or No. */
export const CLOSE_PROXIMITY_CHOICES = [
  { value: 'Y', text: 'Yes' },
  { value: 'N', text: 'No' },
] as const;

/** Special Equipment Requirements' extra choice: structures that need none. Legacy's "None". */
export const NO_SPECIAL_EQUIPMENT = 'NONE';

/**
 * The criteria of the three reports — legacy's `inspections_due_summary.jsp`,
 * `inspections_due_detailed.jsp` (the same form) and `inspections_blank.jsp`, which asks the same
 * but for Structure # in place of No Next Planned Inspection Date and Sort By.
 *
 * <p>Less two of legacy's: Area (the retired ministry Areas, left out as on the other reports) and
 * Save/Delete Report Favourite. Every criterion is optional.
 */
export type InspectionsDueCriteria = MaintainerCriteria & {
  /** Structure #, the Blank's only. A contains match. */
  structureName: string;
  /** The first and last days of the next planned inspection, `yyyy/mm/dd`, both included. */
  nextPlannedInspectionDateStart: string;
  nextPlannedInspectionDateEnd: string;
  /** The Due reports' only: structures with no next planned inspection — the dates set aside. */
  noNextPlannedInspectionDate: boolean;
  siteTypeCode: string;
  /** Type/Class. */
  structureTypeClassCode: string;
  /** Close Proximity Inspection Required?: blank for either, `Y` or `N`. */
  closeProximity: string;
  /** Inspection Status — the structure's. */
  structureInspectionStatusCode: string;
  siteStatusCode: string;
  /** Forest District. Changing it clears {@link managementOrgUnit}. */
  orgUnit: string;
  managementOrgUnit: string;
  businessAreaOrgUnit: string;
  forestFileId: string;
  /** "Br." — the road section, beside Project File ID#. */
  roadSectionId: string;
  specialAccessCode: string;
  /** A code, {@link NO_SPECIAL_EQUIPMENT} for none, or blank for any. */
  specialEquipmentCode: string;
  /** The Blank's order is fixed; it keeps the default, unshown. */
  sortBy: InspectionsDueSort;
};

/**
 * PDF to print, CSV to work with, KMZ to see on a map — a button each. Legacy offered PDF, Word
 * and Excel; the KMZ is the Structure Inventory – Detailed one, for the structures listed.
 */
export const FORMATS = ['CSV', 'KMZ', 'PDF'] as const;

/** The Blank's: its forms are for printing, not for a map. */
export const BLANK_FORMATS = ['CSV', 'PDF'] as const;

/** Legacy's defaults, for all three: nothing set, sorted by Forest Service Road. */
export const EMPTY_CRITERIA: InspectionsDueCriteria = {
  structureName: '',
  nextPlannedInspectionDateStart: '',
  nextPlannedInspectionDateEnd: '',
  noNextPlannedInspectionDate: false,
  siteTypeCode: '',
  structureTypeClassCode: '',
  closeProximity: '',
  structureInspectionStatusCode: '',
  siteStatusCode: '',
  orgUnit: '',
  managementOrgUnit: '',
  businessAreaOrgUnit: '',
  forestFileId: '',
  roadSectionId: '',
  clientNumber: '',
  clientLocationCode: '',
  maintainerLabel: '',
  specialAccessCode: '',
  specialEquipmentCode: '',
  sortBy: 'FOREST_SERVICE_ROAD',
};
