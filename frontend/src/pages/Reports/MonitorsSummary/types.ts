import type { MaintainerCriteria } from '../MaintainerFields';
import type { SortOption } from '../ReportOutput';

export type { ReportFormat } from '../types';

/**
 * The Monitors Summary's order — legacy's first, with its label. Legacy's other, Road Name,
 * Structure Id, is gone: the road name is LRMOPS's, and CBR's database has none to sort on (2026-11-25). One order, so the form offers no Sort By.
 */
export type MonitorsSummarySort = 'PROJECT_BRANCH_KM';

export const SORT_OPTIONS: readonly SortOption<MonitorsSummarySort>[] = [
  { value: 'PROJECT_BRANCH_KM', label: 'Project Number, Branch, KM (Asc)' },
];

/**
 * The Monitors Summary report's criteria (legacy CBR011).
 *
 * <p>Field-for-field from legacy's `monitor_summary.jsp`, less two: Road Responsibility (commented
 * out of the JSP) and Save/Delete Report Favourite. Legacy has no Management Area here, and neither does this. Every
 * criterion is optional.
 */
export type MonitorsSummaryCriteria = MaintainerCriteria & {
  /** Structure #. A contains match. */
  structureName: string;
  siteStatusCode: string[];
  siteTypeCode: string;
  monitoringStatusCode: string[];
  /** Area: one of the three, by number — the sites whose district rolls up into it. One pick. */
  areaOrgUnit: string;
  /** Forest District. */
  orgUnit: string[];
  businessAreaOrgUnit: string[];
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
  siteStatusCode: [],
  siteTypeCode: '',
  clientNumber: '',
  clientLocationCode: '',
  maintainerLabel: '',
  monitoringStatusCode: [],
  areaOrgUnit: '',
  orgUnit: [],
  businessAreaOrgUnit: [],
  forestServiceRoad: '',
  forestFileId: '',
  roadSectionId: '',
  mostRecentInspections: false,
  sortBy: 'PROJECT_BRANCH_KM',
};
