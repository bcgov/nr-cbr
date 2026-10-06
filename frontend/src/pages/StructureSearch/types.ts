import type { HeaderSort } from '@/utils/headerSort';

/**
 * Structure Search criteria and results.
 *
 * <p>Field-for-field from the legacy `structure_search.jsp`, under the names the backend's
 * `StructureSearchCriteria` binds — every one is a query parameter of `/api/v1/structures/search`.
 */

export type { CodeOption, OrgUnitOption } from '@/types/configuration';

/**
 * The criteria the form offers. Every one is optional: like Site Search, the legacy screen runs with
 * none set and returns every active structure in the province.
 *
 * <p>User Kilometres is not here, though the backend still accepts it. Site Search dropped that
 * range, and this form follows it so the two screens filter a site the same way.
 */
export type StructureSearchCriteria = {
  /** Structure #. A contains match, like every free-text criterion. */
  structureName: string;
  /** Downrated Structure? — design load rating above the current one, or no current rating. */
  downrated: boolean;
  structureTypeClassCode: string;
  portableStructure: boolean;
  /** From the bridge record, so it only ever matches a bridge. */
  superstructureTypeCode: string;
  /** From the bridge record, so it only ever matches a bridge. */
  structureCurbTypeCode: string;
  /** From the culvert record, so it only ever matches a culvert. */
  culvertTypeCode: string;
  incomplete: boolean;

  siteId: string;
  siteStatusCode: string;
  forestFileId: string;
  /** "Br." on the legacy form — the road section, shown beside Project File ID#. */
  roadSectionId: string;
  siteTypeCode: string;
  forestServiceRoad: string;
  kiloStart: string;
  kiloEnd: string;
  crossingName: string;

  /** Forest District. Changing it clears {@link managementOrgUnit} — see the page. */
  orgUnit: string;
  /** Off, the default, leaves archived structures out — legacy's rule. */
  includeArchived: boolean;
  managementOrgUnit: string;
  clientNumber: string;
  clientLocationCode: string;
  /** Designated Maintainer by name, for a term that is not a pick from the lookup. */
  primaryUserName: string;

  /** Estimated Load Restriction, yyyy — `FULL_LOG_HAUL_TRFFC_RPLCMNT_DT`. */
  loadRestrictionYearStart: string;
  loadRestrictionYearEnd: string;
  /** Estimated Replacement, yyyy — the light-vehicle replacement year. */
  replacementYearStart: string;
  replacementYearEnd: string;
  /** Estimated Closure, yyyy. */
  closureYearStart: string;
  closureYearEnd: string;
  /** Year Superstructure Installed, yyyy. */
  yearBuiltStart: string;
  yearBuiltEnd: string;
  specialAccessCode: string;
  specialEquipmentCode: string;

  /**
   * The label of the maintainer picked from the lookup. <b>Display state, not a criterion</b> —
   * the same arrangement as Site Search, and dropped by `populated()` before the request is sent.
   */
  maintainerLabel: string;
};

/** A row of the results table. */
export type StructureSearchResult = {
  id: string;
  structureName: string;
  siteId: string;
  orgUnitCode: string;
  orgUnitName: string;
  forestServiceRoad: string;
  /** The site's KM — `CROSSING_SITE.POINT_OF_COMMENCEMENT_DISTANCE`. */
  kilometres: string;
  crossingName: string;
  structureTypeClass: string;
  forestFileId: string;
  roadSectionId: string;
  clientNumber: string;
  clientLocationCode: string;
  clientName: string;
  /**
   * What stops this structure being deleted — "inspections", "repairs" and so on; empty when
   * nothing does. Null when the caller cannot delete, for whom the server does not work it out.
   */
  deleteBlockers?: string[] | null;
};

/**
 * A ticked structure, as remembered across pages: what the screen needs to act on it when its row
 * is no longer in view.
 */
export type SelectedStructure = {
  /** Its Structure #, or the id when it has none. */
  name: string;
  /** The site it stands on — whose maintainer Update Repair Responsibility sets. Null for none. */
  siteId: string | null;
  /** As on {@link StructureSearchResult} when it was ticked. */
  deleteBlockers: string[] | null;
};

/**
 * The results columns a user can sort by, one per header, by the server's names for them — see
 * `StructureSortColumn`. New in CBR, as on Site Search.
 */
export type StructureSortColumn =
  | 'STRUCTURE_NAME'
  | 'SITE_ID'
  | 'DISTRICT'
  | 'FOREST_SERVICE_ROAD'
  | 'KILOMETRES'
  | 'CROSSING_NAME'
  | 'TYPE_CLASS'
  | 'PROJECT_FILE'
  | 'MAINTAINER';

/** A header the user sorted by, and which way. `null` where used means legacy's order. */
export type StructureSort = HeaderSort<StructureSortColumn>;

export const EMPTY_CRITERIA: StructureSearchCriteria = {
  structureName: '',
  downrated: false,
  structureTypeClassCode: '',
  portableStructure: false,
  superstructureTypeCode: '',
  structureCurbTypeCode: '',
  culvertTypeCode: '',
  incomplete: false,
  siteId: '',
  siteStatusCode: '',
  forestFileId: '',
  roadSectionId: '',
  siteTypeCode: '',
  forestServiceRoad: '',
  kiloStart: '',
  kiloEnd: '',
  crossingName: '',
  orgUnit: '',
  includeArchived: false,
  managementOrgUnit: '',
  clientNumber: '',
  clientLocationCode: '',
  primaryUserName: '',
  loadRestrictionYearStart: '',
  loadRestrictionYearEnd: '',
  replacementYearStart: '',
  replacementYearEnd: '',
  closureYearStart: '',
  closureYearEnd: '',
  yearBuiltStart: '',
  yearBuiltEnd: '',
  specialAccessCode: '',
  specialEquipmentCode: '',
  maintainerLabel: '',
};

export type { PagedResponse } from '@/types/api';
