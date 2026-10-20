/** What a report is produced as — a button each. Not every report offers every format. */
export type ReportFormat = 'PDF' | 'CSV' | 'KMZ';

/** The Structure Inventory reports' four orders, all ascending — legacy's. */
export type StructureInventorySort =
  'PROJECT_BRANCH_USER_KM' | 'ROAD_NAME_USER_KM' | 'SITE_ID' | 'STRUCTURE_ID';

export const SORT_OPTIONS: { value: StructureInventorySort; label: string }[] = [
  { value: 'PROJECT_BRANCH_USER_KM', label: 'Project Number, Branch, User KM' },
  { value: 'ROAD_NAME_USER_KM', label: 'Road Name, User KM' },
  { value: 'SITE_ID', label: 'Site Id' },
  { value: 'STRUCTURE_ID', label: 'Structure Id' },
];

/**
 * The criteria the Structure Inventory reports share — the Summary's and the Detailed's forms
 * alike — so their shared boxes are built once ({@link ./criteriaGroups}).
 */
export type StructureInventoryCommonCriteria = {
  clientNumber: string;
  clientLocationCode: string;
  /** The label of the maintainer picked. <b>Display state, not a criterion.</b> */
  maintainerLabel: string;
  siteStatusCode: string;
  downrated: boolean;
  capitalRoad: boolean;
  siteTypeCode: string;
  /** Forest District. Changing it clears {@link managementOrgUnit}. */
  orgUnit: string;
  businessAreaOrgUnit: string;
  managementOrgUnit: string;
  forestFileId: string;
  /** "Br." — the road section, beside Project File ID#. */
  roadSectionId: string;
  kiloStart: string;
  kiloEnd: string;
  yearBuiltStart: string;
  yearBuiltEnd: string;
  loadRestrictionYearStart: string;
  loadRestrictionYearEnd: string;
  replacementYearStart: string;
  replacementYearEnd: string;
  specialAccessCode: string;
  portableSuperstructureStatusCode: string;
  sortBy: StructureInventorySort;
};

/** Legacy's defaults for the shared criteria: nothing set, sorted by project, branch and user km. */
export const EMPTY_COMMON_CRITERIA: StructureInventoryCommonCriteria = {
  clientNumber: '',
  clientLocationCode: '',
  maintainerLabel: '',
  siteStatusCode: '',
  downrated: false,
  capitalRoad: false,
  siteTypeCode: '',
  orgUnit: '',
  businessAreaOrgUnit: '',
  managementOrgUnit: '',
  forestFileId: '',
  roadSectionId: '',
  kiloStart: '',
  kiloEnd: '',
  yearBuiltStart: '',
  yearBuiltEnd: '',
  loadRestrictionYearStart: '',
  loadRestrictionYearEnd: '',
  replacementYearStart: '',
  replacementYearEnd: '',
  specialAccessCode: '',
  portableSuperstructureStatusCode: '',
  sortBy: 'PROJECT_BRANCH_USER_KM',
};
