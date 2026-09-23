/**
 * Site Search criteria and results.
 *
 * <p>Field-for-field from the legacy `site_search.jsp` — the names are the legacy form properties so
 * the eventual request maps onto `CBR.FIND_SITES_BY_CRITERIA` without a translation layer in
 * between. That procedure takes a caller-built `WHERE` clause plus a bind array rather than fixed
 * parameters, so whatever assembles it needs to know exactly which criterion each value came from.
 */

/**
 * The reference types the form's selects are built from. Declared in `@/types/configuration`
 * because every screen draws on the same 37 code tables, and re-exported here so this file stays
 * the one place the Site Search screen's shapes are named.
 */
export type { CodeOption, OrgUnitOption } from '@/types/configuration';

/**
 * The 17 criteria the legacy form offers. Every one is optional: the legacy search runs with none
 * set and returns everything, and there is no scoping to narrow it — any CBR user can search every
 * site in the province (cbr-auth-and-roles.local.md §3.3).
 */
export type SiteSearchCriteria = {
  siteId: string;
  siteStatusCode: string;
  forestFileId: string;
  /** "Br." on the legacy form — the road section, shown beside Project File ID#. */
  roadSectionId: string;
  structureInspectionStatusCode: string;
  forestServiceRoad: string;
  /** Designated Maintainer Client Number. Populated by the client-search lookup. */
  clientNumber: string;
  crossingName: string;
  clientLocationCode: string;
  /** Forest District. Changing it re-filters {@link managementOrgUnit} — see the note in index.tsx. */
  orgUnit: string;
  kiloStart: string;
  kiloEnd: string;
  managementOrgUnit: string;
  specialAccessCode: string;
  incomplete: boolean;
  siteTypeCode: string;
  capitalRoad: boolean;
  /** Designated Maintainer, by name rather than client number. */
  primaryUserName: string;
  /**
   * The label of the maintainer picked from the lookup, e.g.
   * `CANFOR CORPORATION · Vancouver · 00001012-00`.
   *
   * <p><b>Display state, not a criterion.</b> It is held here rather than inside the combo box so
   * that Reset clears it with everything else, and it is the one field `populated()` in
   * `siteSearch.service` refuses to send — the server filters on the number and the location code,
   * which are carried separately.
   */
  maintainerLabel: string;
};

/** A row of the results table. */
export type SiteSearchResult = {
  id: string;
  /** Shown in the District Code column; `orgUnitName` is its tooltip, as in the legacy table. */
  orgUnitCode: string;
  orgUnitName: string;
  forestServiceRoad: string;
  /** The KM column — `CROSSING_SITE.POINT_OF_COMMENCEMENT_DISTANCE`. */
  pointOfCommencementDistance: string;
  crossingName: string;
  forestFileId: string;
  roadSectionId: string;
  /** The raw code — decides the colour of the status pill. See `utils/siteStatus`. */
  crossingSiteStatusCode: string;
  /** The decoded status — what the pill reads. */
  crossingSiteStatusDescription: string;
};

export const EMPTY_CRITERIA: SiteSearchCriteria = {
  siteId: '',
  siteStatusCode: '',
  forestFileId: '',
  roadSectionId: '',
  structureInspectionStatusCode: '',
  forestServiceRoad: '',
  clientNumber: '',
  crossingName: '',
  clientLocationCode: '',
  orgUnit: '',
  kiloStart: '',
  kiloEnd: '',
  managementOrgUnit: '',
  specialAccessCode: '',
  incomplete: false,
  siteTypeCode: '',
  capitalRoad: false,
  primaryUserName: '',
  maintainerLabel: '',
};

/**
 * Re-exported so this file stays the one place the Site Search screen's shapes are named. The
 * definition lives in `@/types/api` because Inspection Search speaks the same envelope.
 */
export type { PagedResponse } from '@/types/api';
