import type { OwnerCriteria } from '../criteriaGroups';
import type { SortOption } from '../ReportOutput';
import type { CodeOption } from '@/types/configuration';

export type { ReportFormat } from '../types';

/** The Repairs Summary's three orders — legacy's, with legacy's labels. */
export type RepairsSummarySort = 'PROJECT_BRANCH_KM' | 'ROAD_NAME_USER_KM' | 'SITE_ID';

export const SORT_OPTIONS: readonly SortOption<RepairsSummarySort>[] = [
  { value: 'PROJECT_BRANCH_KM', label: 'Project Number, Branch' },
  { value: 'ROAD_NAME_USER_KM', label: 'Road Name, KM User' },
  { value: 'SITE_ID', label: 'Site Id' },
];

/**
 * The Repairs Summary report's criteria (legacy CBR005).
 *
 * <p>Field-for-field from legacy's `repair_summary.jsp`, less three: Admin Area (the retired
 * ministry Areas, left out as on the other reports), Road Responsibility (commented out of the JSP,
 * so no user has set it in years) and Save/Delete Report Favourite. Every criterion is optional.
 */
export type RepairsSummaryCriteria = OwnerCriteria & {
  /** Structure #. A contains match. */
  structureName: string;
  /** Site #. A contains match. */
  siteId: string;
  siteStatusCode: string[];
  siteTypeCode: string;
  /** A repair status, or one of legacy's groups of them — {@link REPAIR_STATUS_GROUPS}. */
  repairStatusCode: string;
  /**
   * Repair Priority — a multi-select. It replaces legacy's ready-made groups ("Urgent, High and
   * Medium" and the rest), which the UAT pick list asked to change: any combination can be ticked.
   */
  repairPriorityCode: string[];
  /**
   * "Structural Safety" — a multi-select of the repair classes, replacing legacy's list of every
   * pair of them ("Structural and Non-structural").
   */
  repairClassCode: string[];
  /** The road's name. A contains match. */
  forestServiceRoad: string;
  forestFileId: string;
  /** "Br." — the road section, beside Project File ID#. */
  roadSectionId: string;
  /** Downrated Structure? — a one-way filter. */
  downrated: boolean;
  /** Most Recent Inspections Only? — the repairs of each structure's latest inspection only. */
  mostRecentInspections: boolean;
  sortBy: RepairsSummarySort;
};

/** PDF to print, CSV to work with — a button each. Legacy offered PDF, Word and Excel. */
export const FORMATS = ['CSV', 'PDF'] as const;

/** Legacy's own groups of repair statuses, listed after the statuses themselves. */
export const REPAIR_STATUS_GROUPS: readonly CodeOption[] = [
  { code: 'SOR', description: 'Suggested or Required' },
  { code: 'OUT', description: 'Outstanding' },
];

/** Legacy's defaults: Outstanding repairs, sorted by project and branch. */
export const EMPTY_CRITERIA: RepairsSummaryCriteria = {
  structureName: '',
  siteId: '',
  siteStatusCode: [],
  siteTypeCode: '',
  clientNumber: '',
  clientLocationCode: '',
  maintainerLabel: '',
  repairStatusCode: 'OUT',
  repairPriorityCode: [],
  repairClassCode: [],
  orgUnit: [],
  businessAreaOrgUnit: [],
  managementOrgUnit: [],
  forestServiceRoad: '',
  forestFileId: '',
  roadSectionId: '',
  downrated: false,
  mostRecentInspections: false,
  sortBy: 'PROJECT_BRANCH_KM',
};
