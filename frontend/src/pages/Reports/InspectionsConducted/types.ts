import type { MaintainerCriteria } from '../MaintainerFields';
import type { SortOption } from '../ReportOutput';

export type { ReportFormat } from '../types';

/** Which of the two reports: the Summary (legacy CBR002) or the Detail (legacy CBR001). */
export type InspectionsConductedVariant = 'summary' | 'detail';

/** Where each page keeps its criteria for the tab. */
export const INSPECTIONS_CONDUCTED_STATE_KEYS: Record<InspectionsConductedVariant, string> = {
  summary: 'cbr.report.inspectionsConductedSummary',
  detail: 'cbr.report.inspectionsConductedDetail',
};

/**
 * The Summary's orders, legacy's labels as they were. Legacy sorts Project File ID# descending,
 * though its label does not say so; kept as legacy has it. Legacy's Forest Service Road is gone:
 * the road name is LRMOPS's, and CBR's database has none to sort on (2026-11-25).
 */
export type InspectionsConductedSummarySort = 'FOREST_FILE_ID' | 'USER_KM' | 'INSPECTION_DATE';

/** The Detail's orders, legacy's labels — less Road Name, User KM, for the same reason. */
export type InspectionsConductedDetailSort = 'PROJECT_BRANCH_USER_KM' | 'INSPECTION_DATE';

export type InspectionsConductedSort =
  InspectionsConductedSummarySort | InspectionsConductedDetailSort;

export const SUMMARY_SORT_OPTIONS: readonly SortOption<InspectionsConductedSummarySort>[] = [
  { value: 'FOREST_FILE_ID', label: 'Project File ID#' },
  { value: 'USER_KM', label: 'User Km (Asc)' },
  { value: 'INSPECTION_DATE', label: 'Inspection Date (Desc)' },
];

export const DETAIL_SORT_OPTIONS: readonly SortOption<InspectionsConductedDetailSort>[] = [
  { value: 'PROJECT_BRANCH_USER_KM', label: 'Project Number, Branch, User KM (Asc)' },
  { value: 'INSPECTION_DATE', label: 'Inspection Date (Desc)' },
];

/**
 * The Inspections Conducted reports' criteria — legacy's `inspections_conducted.jsp` (Summary) and
 * `inspection_detail.jsp` (Detail), which ask the same things but for the Detail's Inspection Type.
 *
 * <p>Less three of legacy's: Area (the retired ministry Areas, left out as on the other reports),
 * Road Responsibility (commented out of both JSPs) and Save/Delete Report Favourite. Every
 * criterion is optional.
 */
export type InspectionsConductedCriteria = MaintainerCriteria & {
  /** Structure #. A contains match. */
  structureName: string;
  siteTypeCode: string[];
  inspectionReportStatusCode: string[];
  /** The Detail's only: Routine by default, as legacy's. The Summary leaves it blank. */
  inspectionTypeCode: string;
  /** The first and last months of the inspection dates, `yyyy/mm`. */
  inspectionDateStart: string;
  inspectionDateEnd: string;
  /** Forest District. Changing it clears {@link managementOrgUnit}. */
  orgUnit: string[];
  managementOrgUnit: string[];
  businessAreaOrgUnit: string[];
  forestFileId: string;
  /** "Br." — the road section, beside Project File ID#. */
  roadSectionId: string;
  /** Inspector Name. A contains match. */
  inspectorName: string;
  closeProximity: boolean;
  mostRecentInspections: boolean;
  professionalInspection: boolean;
  sortBy: InspectionsConductedSort;
};

const EMPTY: Omit<InspectionsConductedCriteria, 'inspectionTypeCode' | 'sortBy'> = {
  structureName: '',
  siteTypeCode: [],
  inspectionReportStatusCode: [],
  inspectionDateStart: '',
  inspectionDateEnd: '',
  orgUnit: [],
  managementOrgUnit: [],
  businessAreaOrgUnit: [],
  forestFileId: '',
  roadSectionId: '',
  clientNumber: '',
  clientLocationCode: '',
  maintainerLabel: '',
  inspectorName: '',
  closeProximity: false,
  mostRecentInspections: false,
  professionalInspection: false,
};

/** Legacy's Summary defaults: nothing set, sorted by Forest Service Road. */
export const EMPTY_SUMMARY_CRITERIA: InspectionsConductedCriteria = {
  ...EMPTY,
  inspectionTypeCode: '',
  sortBy: 'FOREST_FILE_ID',
};

/** Legacy's Detail defaults: Routine inspections, sorted by project, branch and user km. */
export const EMPTY_DETAIL_CRITERIA: InspectionsConductedCriteria = {
  ...EMPTY,
  inspectionTypeCode: 'ROUT',
  sortBy: 'PROJECT_BRANCH_USER_KM',
};
