import type { MaintainerCriteria } from '../MaintainerFields';
import type { SortOption } from '../ReportOutput';

export type { ReportFormat } from '../types';

/** The Monitors Summary's two orders — legacy's, with legacy's labels. */
export type MonitorsSummarySort = 'PROJECT_BRANCH_KM' | 'ROAD_NAME_STRUCTURE';

export const SORT_OPTIONS: readonly SortOption<MonitorsSummarySort>[] = [
  { value: 'PROJECT_BRANCH_KM', label: 'Project Number, Branch, KM (Asc)' },
  { value: 'ROAD_NAME_STRUCTURE', label: 'Road Name, Structure Id' },
];

/**
 * The Monitors Summary report's criteria (legacy CBR011).
 *
 * <p>Field-for-field from legacy's `monitor_summary.jsp`, less three: Area (the retired ministry
 * Areas, left out as on the other reports), Road Responsibility (commented out of the JSP) and
 * Save/Delete Report Favourite. Legacy has no Management Area here, and neither does this. Every
 * criterion is optional.
 */
export type MonitorsSummaryCriteria = MaintainerCriteria & {
  /** Structure #. A contains match. */
  structureName: string;
  siteStatusCode: string;
  siteTypeCode: string;
  monitoringStatusCode: string;
  /** Forest District. */
  orgUnit: string;
  businessAreaOrgUnit: string;
  /** The road's name. A contains match. */
  forestServiceRoad: string;
  forestFileId: string;
  /** "Br." — the road section, beside Project File ID#. */
  roadSectionId: string;
  /**
   * Most Recent Inspections Only? — the monitoring items of each structure's latest routine
   * inspection only. Legacy's checkbox printed on the criteria line but filtered nothing.
   */
  mostRecentInspections: boolean;
  sortBy: MonitorsSummarySort;
};

/** PDF to print, CSV to work with — a button each. Legacy offered PDF, Word and Excel. */
export const FORMATS = ['CSV', 'PDF'] as const;

/** Legacy's defaults: nothing set, sorted by project, branch and kilometre. */
export const EMPTY_CRITERIA: MonitorsSummaryCriteria = {
  structureName: '',
  siteStatusCode: '',
  siteTypeCode: '',
  clientNumber: '',
  clientLocationCode: '',
  maintainerLabel: '',
  monitoringStatusCode: '',
  orgUnit: '',
  businessAreaOrgUnit: '',
  forestServiceRoad: '',
  forestFileId: '',
  roadSectionId: '',
  mostRecentInspections: false,
  sortBy: 'PROJECT_BRANCH_KM',
};
