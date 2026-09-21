/**
 * Inspection Search criteria and results.
 *
 * <p>Field-for-field from the legacy `inspection_search.jsp` and `InspectionSearchForm`. As on Site
 * Search, the names are the legacy form properties so the eventual request maps onto the search the
 * legacy `InspectionDAO` builds without a translation layer in between. (They differ in spelling
 * from Site Search's — `orgUnitNo` here against `orgUnit` there — because the two legacy forms
 * differ. Each screen is faithful to its own.)
 */

/**
 * The reference types the form's selects are built from. Declared in `@/types/configuration`
 * because every screen draws on the same 37 code tables, and re-exported here so this file stays
 * the one place the Inspection Search screen's shapes are named.
 */
export type { CodeOption, OrgUnitOption } from '@/types/configuration';

/** The paging envelope every search endpoint answers with. */
export type { PagedResponse } from '@/types/api';

/**
 * One reviewer, as the "Reviewed By" select needs them — `STRUCTURE_INSPECTION_REVIEWER` joined to
 * the user who may sign off an inspection.
 *
 * <p>Its own shape rather than a `CodeOption`, because the value is a numeric id rather than a code
 * anyone says out loud, so the select must not render it beside the name the way a code list does.
 *
 * <p>Whether this list survives at all is open: `cbr-auth-and-roles.local.md` §6 recommends moving
 * the reviewer permission into FAM and deleting the admin screen that maintains it, in which case
 * this re-sources to `UserLookupClient` (decision D4).
 */
export type InspectionReviewerOption = {
  inspectionReviewerId: string;
  displayName: string;
};

/**
 * The two orderings the legacy form offers, by their legacy `sortBy` values.
 *
 * <p>Legacy `reset()` selects {@link STRUCTURE_ID_DATE_SORT}, so that is the default on a fresh
 * form. `createSearch()` also carries a third, unreachable ordering for when `sortBy` is empty —
 * district code, road, branch, km ascending then date descending — which only happens on a
 * back-navigation into a restored search. It is not offered here because it is not offered there.
 */
export const STRUCTURE_ID_DATE_SORT = 'structureIdDateSort';
export const PROJECT_BRANCH_KM_DATE_SORT = 'projectBranchKmDateSort';

export type InspectionSortBy = typeof STRUCTURE_ID_DATE_SORT | typeof PROJECT_BRANCH_KM_DATE_SORT;

/**
 * The 19 criteria the legacy form offers, in its own order.
 *
 * <p>Every one is optional, including all of them at once. Legacy refuses an empty form with
 * `errors.search.select`, because its query was unpaginated and "no criteria" meant every
 * inspection in the province in one response; the search is paged server-side now, and Site Search
 * answers an empty form for the same reason.
 *
 * <p><b>Road Responsibility is deliberately absent.</b> `InspectionSearchForm` still carries
 * `roadResponsibilityCode` — it validates it, maps it into the search and reads it back out — but
 * the block that renders the dropdown is commented out in the JSP, so no user has been able to set
 * it for years. Porting the field would be porting a criterion the screen does not have.
 */
export type InspectionSearchCriteria = {
  siteId: string;
  /** "Structure #" on the legacy form, though the value is the structure's name. */
  structureName: string;
  structureTypeClassCode: string;
  closeProximity: boolean;
  inspectionTypeCode: string;
  mostRecentInspections: boolean;
  inspectionReportStatusCode: string;
  /** "Previously Reviewed Inspections Only?" — legacy `findChangedReviewed`. */
  findChangedReviewed: boolean;
  forestFileId: string;
  /** "Br." on the legacy form — the road section, shown beside Project File ID#. */
  roadSectionId: string;
  /**
   * "Include Inspections for Structures at Previous Sites?".
   *
   * <p>Not an independent filter: it changes what {@link siteId} means, from "inspections at this
   * site" to "inspections of structures that have ever been at this site". Legacy switches between
   * two different search criteria on it inside the `siteId` branch, so with no site number set it
   * does nothing at all — which the form does not say. The help text on the field here does.
   */
  findMovedStructures: boolean;
  forestServiceRoad: string;
  /** Inclusive month bound, `yyyy/MM`. See `validation.ts` for the format and the range rules. */
  inspectionDateStart: string;
  inspectionDateEnd: string;
  /** Forest District. Changing it re-filters {@link managementOrgUnitNo}. */
  orgUnitNo: string;
  managementOrgUnitNo: string;
  /** BCTS Business Area — a third, separate org-unit filter, not a child of the district. */
  businessAreaOrgUnitNo: string;
  inspectorName: string;
  inspectionReviewerId: string;
  sortBy: InspectionSortBy;
};

/**
 * A row of the results table — the ten columns the legacy table shows, plus the status code, which
 * legacy reads but does not print.
 *
 * <p>The code earns its place because two of the row's behaviours turn on it and neither is
 * derivable from the description: an `OFL` row is not a link, and it is the only row that offers
 * delete.
 *
 * <p><b>Every field but `id` is nullable, and that is the server's shape rather than a precaution.</b>
 * `INSPECTION_DATE` is a nullable column; `CROSSING_STRUCTURE_NAME`, the crossing name, the project
 * file and the kilometre are all nullable too; and the district, the road section and the decoded
 * status come through left joins that a real row can miss. `InspectionSearchService.toResult` maps
 * each of those with a null-tolerant read for exactly that reason. Declaring them `string` here said
 * otherwise, and the first live search found it — a null `inspectionDate` reached
 * `formatInspectionDate`, which called `.trim()` on it and took the whole page down.
 */
export type InspectionSearchResult = {
  /** `STRUCTURE_INSPECTION.INSPECTION_ID`. The only field the server can always fill. */
  id: string;
  /** ISO `yyyy-MM-dd`; the table prints it as `yyyy/MM/dd`, as legacy's `<fmt:formatDate>` does. */
  inspectionDate: string | null;
  /** The raw code — decides the colour of the status pill, the link and the delete control. */
  inspectionReportStatusCode: string | null;
  /** The decoded status — what the pill reads. */
  inspectionReportStatusDescription: string | null;
  /**
   * The site the structure sat at when the inspection happened, which is not necessarily where it
   * sits now — hence legacy's `siteAtTimeOfInspection` rather than a plain site id.
   */
  siteAtTimeOfInspection: string | null;
  structureName: string | null;
  /** Shown in the District Code column; `orgUnitName` is its tooltip in legacy, a suffix here. */
  orgUnitCode: string | null;
  orgUnitName: string | null;
  forestServiceRoad: string | null;
  /** The KM column — `POINT_OF_COMMENCEMENT_DISTANCE`. */
  pointOfCommencementDistance: string | null;
  crossingName: string | null;
  forestFileId: string | null;
  roadSectionId: string | null;
};

export const EMPTY_CRITERIA: InspectionSearchCriteria = {
  siteId: '',
  structureName: '',
  structureTypeClassCode: '',
  closeProximity: false,
  inspectionTypeCode: '',
  mostRecentInspections: false,
  inspectionReportStatusCode: '',
  findChangedReviewed: false,
  forestFileId: '',
  roadSectionId: '',
  findMovedStructures: false,
  forestServiceRoad: '',
  inspectionDateStart: '',
  inspectionDateEnd: '',
  orgUnitNo: '',
  managementOrgUnitNo: '',
  businessAreaOrgUnitNo: '',
  inspectorName: '',
  inspectionReviewerId: '',
  // Legacy `reset()` sets exactly this, so an untouched form sorts by structure then date.
  sortBy: STRUCTURE_ID_DATE_SORT,
};
