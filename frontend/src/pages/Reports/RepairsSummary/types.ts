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
  siteStatusCode: string;
  siteTypeCode: string;
  /** A repair status, or one of legacy's groups of them — {@link REPAIR_STATUS_GROUPS}. */
  repairStatusCode: string;
  /** A repair priority, or one of legacy's groups of them — {@link REPAIR_PRIORITY_GROUPS}. */
  repairPriorityCode: string;
  /** "Structural Safety": a repair class, or any two of them — {@link repairClassOptions}. */
  repairClassCode: string;
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

/** Legacy's own groups of repair priorities, listed after the priorities themselves. */
export const REPAIR_PRIORITY_GROUPS: readonly CodeOption[] = [
  { code: 'U&H&M', description: 'Urgent, High and Medium' },
  { code: 'H&M', description: 'High and Medium' },
  { code: 'M&L', description: 'Medium and Low' },
];

/**
 * Structural Safety's options: each repair class, then every pair of them — "S&N", "Structural
 * and Non-structural" — as legacy builds them from the code table.
 */
export const repairClassOptions = (classes: readonly CodeOption[]): CodeOption[] => [
  ...classes,
  ...classes.flatMap((first, index) =>
    classes.slice(index + 1).map((second) => ({
      code: `${first.code}&${second.code}`,
      description: `${first.description} and ${second.description}`,
    })),
  ),
];

/** Legacy's defaults: Outstanding repairs, sorted by project and branch. */
export const EMPTY_CRITERIA: RepairsSummaryCriteria = {
  structureName: '',
  siteId: '',
  siteStatusCode: '',
  siteTypeCode: '',
  clientNumber: '',
  clientLocationCode: '',
  maintainerLabel: '',
  repairStatusCode: 'OUT',
  repairPriorityCode: '',
  repairClassCode: '',
  orgUnit: '',
  businessAreaOrgUnit: '',
  managementOrgUnit: '',
  forestServiceRoad: '',
  forestFileId: '',
  roadSectionId: '',
  downrated: false,
  mostRecentInspections: false,
  sortBy: 'PROJECT_BRANCH_KM',
};
