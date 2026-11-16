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

/** One value as a multi-select's list: none when blank. */
const asList = (value: string): string[] => (value.trim() === '' ? [] : [value]);

/**
 * The criteria as Structure Search's: legacy's page ran the same structure search, so this one asks
 * the same endpoint, archived structures left out as there. Its district and area stay single
 * choices (they were not on the UAT pick list); Structure Search takes them as lists of one.
 */
export const toStructureSearch = (
  criteria: StructureDetailReportCriteria,
): StructureSearchCriteria => ({
  ...EMPTY_SEARCH,
  ...criteria,
  orgUnit: asList(criteria.orgUnit),
  managementOrgUnit: asList(criteria.managementOrgUnit),
});
