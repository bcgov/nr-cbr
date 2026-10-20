/**
 * The Reports menu — legacy's eleven reports, in legacy's order and under legacy's names
 * (cbr-navigation.local.md §1, §5 "Naming"): the names appear in printed output and user
 * documentation. `legacy` is the report's number in cbr-reporting-and-integrations.local.md §1.
 */
export const REPORTS = [
  {
    path: 'structure-inventory-summary',
    name: 'Structure Inventory – Summary',
    legacy: 'CBR008',
  },
  {
    path: 'structure-inventory-detailed',
    name: 'Structure Inventory – Detailed',
    legacy: 'CBR007',
  },
  { path: 'structure-detail', name: 'Structure Detail Report', legacy: 'CBR006' },
  { path: 'repairs-summary', name: 'Repairs Summary', legacy: 'CBR005' },
  { path: 'monitors-summary', name: 'Monitors Summary', legacy: 'CBR011' },
  {
    path: 'inspections-conducted-summary',
    name: 'Inspections Conducted – Summary',
    legacy: 'CBR002',
  },
  {
    path: 'inspections-conducted-detail',
    name: 'Inspections Conducted – Detail',
    legacy: 'CBR001',
  },
  { path: 'inspections-due-summary', name: 'Inspections Due – Summary', legacy: 'CBR004' },
  { path: 'inspections-due-detailed', name: 'Inspections Due – Detailed', legacy: 'CBR003' },
  { path: 'inspections-blank', name: 'Inspections – Blank Report', legacy: 'CBR010' },
  {
    path: 'replacement-structures-summary',
    name: 'Replacement Structures Summary',
    legacy: 'CBR009',
  },
] as const;
