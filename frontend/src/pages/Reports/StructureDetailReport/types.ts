import type { MaintainerCriteria } from '../MaintainerFields';
import type { StructureSearchCriteria } from '@/pages/StructureSearch/types';

import { EMPTY_CRITERIA as EMPTY_SEARCH } from '@/pages/StructureSearch/types';

export type { ReportFormat } from '../types';

/**
 * The Structure Detail Report's search criteria (legacy CBR006).
 *
 * <p>Field-for-field from legacy's `structure_detail.jsp`, less the Area filter, as on the other
 * reports, and the Report Favourites. They find structures; the report itself is of one structure,
 * picked from what they find. Every criterion is optional.
 */
export type StructureDetailReportCriteria = MaintainerCriteria & {
  /** Structure #. A contains match, as on Structure Search. */
  structureName: string;
  siteTypeCode: string;
  /** Forest District. Changing it clears {@link managementOrgUnit}. */
  orgUnit: string;
  managementOrgUnit: string;
  forestFileId: string;
  /** "Br." — the road section, beside Project File ID#. */
  roadSectionId: string;
};

export const EMPTY_CRITERIA: StructureDetailReportCriteria = {
  structureName: '',
  siteTypeCode: '',
  clientNumber: '',
  clientLocationCode: '',
  maintainerLabel: '',
  orgUnit: '',
  managementOrgUnit: '',
  forestFileId: '',
  roadSectionId: '',
};

/**
 * The criteria as Structure Search's: legacy's page ran the same structure search, so this one asks
 * the same endpoint, archived structures left out as there.
 */
export const toStructureSearch = (
  criteria: StructureDetailReportCriteria,
): StructureSearchCriteria => ({ ...EMPTY_SEARCH, ...criteria });
