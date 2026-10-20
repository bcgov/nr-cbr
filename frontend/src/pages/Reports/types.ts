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
