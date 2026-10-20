import type { MaintainerCriteria } from '../MaintainerFields';
import type { ReportFormat, StructureInventorySort } from '../types';

export type { ReportFormat, StructureInventorySort } from '../types';

/**
 * The Structure Inventory – Detailed report's criteria (legacy CBR007).
 *
 * <p>Field-for-field from legacy's `structure_inventory_detailed.jsp` — the Summary's criteria less
 * its six construction filters (type/class, superstructure, deck, culvert type and material,
 * opening width) — and, as on the Summary, less the Area filter and the Report Favourites. Every
 * criterion is optional: legacy runs the report with none set.
 */
export type StructureInventoryDetailedCriteria = MaintainerCriteria & {
  siteStatusCode: string;
  /** Downrated Structure? */
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
  /** "Year Built", yyyy — the same column the Summary calls Year Superstructure Installed. */
  yearBuiltStart: string;
  yearBuiltEnd: string;
  /** Estimated Load Restriction, yyyy. */
  loadRestrictionYearStart: string;
  loadRestrictionYearEnd: string;
  /** Estimated Replacement, yyyy. */
  replacementYearStart: string;
  replacementYearEnd: string;
  specialAccessCode: string;
  /** From the bridge record. */
  portableSuperstructureStatusCode: string;

  sortBy: StructureInventorySort;
};

/** As legacy: PDF to print, CSV to work with, KMZ to see on a map. */
export const FORMATS: readonly ReportFormat[] = ['CSV', 'KMZ', 'PDF'];

/** Legacy's defaults: nothing set, sorted by project, branch and user km. */
export const EMPTY_CRITERIA: StructureInventoryDetailedCriteria = {
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
