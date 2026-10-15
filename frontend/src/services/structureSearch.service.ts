import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type {
  DocumentDetailsRequest,
  DocumentsResponse,
  DocumentUpdateRequest,
} from '@/pages/StructureDetail/documentsResponse';
import type {
  CloseProximityInspectionRequest,
  InspectionScheduleRequest,
  InspectionScheduleResponse,
  InspectionsResponse,
} from '@/pages/StructureDetail/inspectionsResponse';
import type {
  MonitorCreatedResponse,
  MonitorCreateRequest,
  MonitorUpdateRequest,
  MonitorView,
  StructureMonitor,
} from '@/pages/StructureDetail/monitorsResponse';
import type {
  RepairCreatedResponse,
  RepairCreateRequest,
  RepairTypeOption,
  RepairUpdateRequest,
  RepairView,
  StructureRepair,
} from '@/pages/StructureDetail/repairsResponse';
import type {
  PierRequest,
  SpanRequest,
  SpansAndPiersResponse,
} from '@/pages/StructureDetail/spansAndPiersResponse';
import type {
  CreatedResponse,
  CommentRequest,
  SitePreviewResponse,
  StructureDetailResponse,
  StructureNameSuggestion,
  StructureCreateRequest,
  ReplacementRequest,
  BridgeDetailsRequest,
  CulvertDetailsRequest,
  LoadRatingDetailsRequest,
  LoadRatingRequest,
  ReplacementDetailsRequest,
  StructureDetailsRequest,
  StructureInformationRequest,
} from '@/pages/StructureDetail/structureResponse';
import type { ItemListing } from '@/pages/StructureDetail/UserAudits';
import type {
  PagedResponse,
  StructureSearchCriteria,
  StructureSearchResult,
  StructureSort,
} from '@/pages/StructureSearch/types';

import { HttpClient, type APIConfig } from '@/config/api/types';
import { sortParams } from '@/utils/headerSort';

/** What `PUT /v1/structures/repair-responsibility` answers with. */
export type RepairResponsibilityResponse = { structureCount: number; siteCount: number };

/** What `PUT /v1/structures/archive` answers with. */
export type StructureArchiveResponse = { archivedCount: number };

/**
 * Structure Search, backed by `/api/v1/structures/search`.
 */
export class StructureSearchService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  /**
   * One page of matching structures. `pageNumber` is zero-based, matching the backend.
   *
   * <p>Blank criteria and false toggles are dropped, as Site Search does: every toggle here is a
   * one-way filter, so `false` and absent mean the same thing.
   */
  searchStructures(
    criteria: StructureSearchCriteria,
    pageNumber: number,
    pageSize: number,
    sort: StructureSort | null = null,
  ): CancelablePromise<PagedResponse<StructureSearchResult>> {
    return this.doRequest<PagedResponse<StructureSearchResult>>(this.config, {
      method: 'GET',
      url: '/v1/structures/search',
      query: { ...populated(criteria), pageNumber, pageSize, ...sortParams(sort) },
    });
  }

  /**
   * Archives structures — Structure Search's Archive button.
   *
   * <p>One request for the whole selection, in one transaction on the server. Answers with how many
   * were archived: an id with no structure is skipped, and an already-archived one is archived
   * again, as legacy does. Level 2 and above; anyone else gets a 403.
   */
  archiveStructures(structureIds: string[]): CancelablePromise<StructureArchiveResponse> {
    return this.doRequest<StructureArchiveResponse>(this.config, {
      method: 'PUT',
      url: '/v1/structures/archive',
      // Strings on screen, because every id the table holds is one; numbers to the server, whose
      // CROSSING_STRUCTURE_ID is a NUMBER(10).
      body: { structureIds: structureIds.map(Number) },
    });
  }

  /**
   * Deletes one structure — called once per ticked structure by Structure Search's Delete.
   *
   * <p>204 on success. 404 when it is already gone; 409 when inspections, documents or photos,
   * repairs, monitors, a close-proximity inspection or a replacement link still belong to it, with a
   * sentence naming which, which the caller should show as it stands. Irreversible.
   */
  deleteStructure(structureId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/structures/{structureId}',
      path: { structureId },
    });
  }

  /**
   * Sets the designated maintainer of the ticked structures' sites — Structure Search's Update
   * Repair Responsibility.
   *
   * <p>It is the site that changes, so every structure on it shares the new maintainer. One
   * request, one transaction. Answers with how many structures and sites were updated; 400 when
   * the maintainer does not exist. Level 1 and above.
   */
  updateRepairResponsibility(
    structureIds: string[],
    clientNumber: string,
    clientLocationCode: string,
  ): CancelablePromise<RepairResponsibilityResponse> {
    return this.doRequest<RepairResponsibilityResponse>(this.config, {
      method: 'PUT',
      url: '/v1/structures/repair-responsibility',
      body: { structureIds: structureIds.map(Number), clientNumber, clientLocationCode },
    });
  }

  /**
   * One structure, for its page — the header and the Details tab.
   *
   * <p>404 when there is no such structure, which is ordinary: the link here comes from a results
   * page that may have been on screen for some time.
   */
  getStructure(structureId: string): CancelablePromise<StructureDetailResponse> {
    return this.doRequest<StructureDetailResponse>(this.config, {
      method: 'GET',
      url: '/v1/structures/{structureId}',
      path: { structureId },
    });
  }

  /** A bridge's spans and piers — the structure page's Spans & Piers tab. */
  getSpansAndPiers(structureId: string): CancelablePromise<SpansAndPiersResponse> {
    return this.doRequest<SpansAndPiersResponse>(this.config, {
      method: 'GET',
      url: '/v1/structures/{structureId}/spans-and-piers',
      path: { structureId },
    });
  }

  /** A structure's documents and photos — the structure page's Documents & Photos tab. */
  getDocuments(structureId: string): CancelablePromise<DocumentsResponse> {
    return this.doRequest<DocumentsResponse>(this.config, {
      method: 'GET',
      url: '/v1/structures/{structureId}/documents',
      path: { structureId },
    });
  }

  /**
   * One document's file, as a Blob typed by the server: an image or a PDF under its own type,
   * anything else as `application/octet-stream`. Fetched with the user's token, which a plain link
   * could not send.
   */
  getDocumentFile(structureId: string, fileId: string): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/structures/{structureId}/documents/{fileId}/file',
      path: { structureId, fileId },
      responseType: 'blob',
    });
  }

  /** Saves the Details tab's Structure card. 400 with `fieldErrors` keyed by the request's fields. */
  updateStructureDetails(
    structureId: string,
    request: StructureDetailsRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/structure-details',
      path: { structureId },
      body: request,
    });
  }

  /** Saves the Details tab's Bridge card. 400 with `fieldErrors` keyed by the request's fields. */
  updateBridgeDetails(structureId: string, request: BridgeDetailsRequest): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/bridge-details',
      path: { structureId },
      body: request,
    });
  }

  /** Saves the fields of Load Rating Details. 400 with `fieldErrors` keyed by the request's fields. */
  updateLoadRatingDetails(
    structureId: string,
    request: LoadRatingDetailsRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/load-rating-details',
      path: { structureId },
      body: request,
    });
  }

  /** Adds a manual load rating. 400 with `fieldErrors` keyed `rating`, `reasonCode`, `reasonComment`. */
  createLoadRating(
    structureId: string,
    request: LoadRatingRequest,
  ): CancelablePromise<CreatedResponse> {
    return this.doRequest<CreatedResponse>(this.config, {
      method: 'POST',
      url: '/v1/structures/{structureId}/load-ratings',
      path: { structureId },
      body: request,
    });
  }

  /** Changes a manual load rating. 400 with `fieldErrors` when a field is refused. */
  updateLoadRating(
    structureId: string,
    loadRatingId: string,
    request: LoadRatingRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/load-ratings/{loadRatingId}',
      path: { structureId, loadRatingId },
      body: request,
    });
  }

  /** Deletes a manual load rating. */
  deleteLoadRating(structureId: string, loadRatingId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/structures/{structureId}/load-ratings/{loadRatingId}',
      path: { structureId, loadRatingId },
    });
  }

  /** Records that this structure replaced another. 400 with `fieldErrors` keyed `replacedName`. */
  createReplacement(structureId: string, request: ReplacementRequest): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'POST',
      url: '/v1/structures/{structureId}/replacements',
      path: { structureId },
      body: request,
    });
  }

  /** Deletes that this structure replaced another, named by its id. */
  deleteReplacement(structureId: string, replacedId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/structures/{structureId}/replacements/{replacedId}',
      path: { structureId, replacedId },
    });
  }

  /** Saves Replacement Details. 400 with `fieldErrors` keyed by the request's fields. */
  updateReplacementDetails(
    structureId: string,
    request: ReplacementDetailsRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/replacement-details',
      path: { structureId },
      body: request,
    });
  }

  /** Saves the Details tab's Culvert card. 400 with `fieldErrors` keyed by the request's fields. */
  updateCulvertDetails(
    structureId: string,
    request: CulvertDetailsRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/culvert-details',
      path: { structureId },
      body: request,
    });
  }

  /** Adds a structure to a site. 400 with `fieldErrors` keyed by field (`bridge.deckWidth`, …). */
  createStructure(request: StructureCreateRequest): CancelablePromise<CreatedResponse> {
    return this.doRequest<CreatedResponse>(this.config, {
      method: 'POST',
      url: '/v1/structures',
      body: request,
    });
  }

  /** Structures whose Structure # starts with the term — at most 20, archived ones marked. */
  suggestStructureNames(term: string): CancelablePromise<StructureNameSuggestion[]> {
    return this.doRequest<StructureNameSuggestion[]>(this.config, {
      method: 'GET',
      url: '/v1/structures/name-suggestions',
      query: { term },
    });
  }

  /** A site a structure might move to, with why it cannot, if it cannot. */
  previewSite(siteId: string): CancelablePromise<SitePreviewResponse> {
    return this.doRequest<SitePreviewResponse>(this.config, {
      method: 'GET',
      url: '/v1/structures/site-preview',
      query: { siteId },
    });
  }

  /**
   * Saves the Information tab's Edit — the structure's name and site. 400 with `fieldErrors` keyed
   * `structureName` or `siteId`.
   */
  updateStructureInformation(
    structureId: string,
    request: StructureInformationRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/information',
      path: { structureId },
      body: request,
    });
  }

  /**
   * Adds a span to a structure's bridge. 400 with `fieldErrors` keyed `number` or `lengthMetres`;
   * 409 when the structure has no bridge record.
   */
  createSpan(structureId: string, request: SpanRequest): CancelablePromise<CreatedResponse> {
    return this.doRequest<CreatedResponse>(this.config, {
      method: 'POST',
      url: '/v1/structures/{structureId}/spans',
      path: { structureId },
      body: request,
    });
  }

  /** Changes a span's number and length. 400 with `fieldErrors` when a field is refused. */
  updateSpan(structureId: string, spanId: string, request: SpanRequest): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/spans/{spanId}',
      path: { structureId, spanId },
      body: request,
    });
  }

  /** Deletes a span. 409, with the reason as its detail, when the bridge will not allow it. */
  deleteSpan(structureId: string, spanId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/structures/{structureId}/spans/{spanId}',
      path: { structureId, spanId },
    });
  }

  /**
   * Adds a pier to a structure's bridge. 400 with `fieldErrors` keyed `number` or `typeCode`, or
   * `pier` when the bridge has too few spans for another.
   */
  createPier(structureId: string, request: PierRequest): CancelablePromise<CreatedResponse> {
    return this.doRequest<CreatedResponse>(this.config, {
      method: 'POST',
      url: '/v1/structures/{structureId}/piers',
      path: { structureId },
      body: request,
    });
  }

  /** Changes a pier's number and type. 400 with `fieldErrors` when a field is refused. */
  updatePier(structureId: string, pierId: string, request: PierRequest): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/piers/{pierId}',
      path: { structureId, pierId },
      body: request,
    });
  }

  /** Deletes a pier. 409, with the reason as its detail, when it has been inspected. */
  deletePier(structureId: string, pierId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/structures/{structureId}/piers/{pierId}',
      path: { structureId, pierId },
    });
  }

  /**
   * Adds one file to a structure, with its details. Sent as multipart: the file, and the details as
   * a JSON part. 400 with `fieldErrors` keyed `file`, `attachmentTypeCode`, `createdDate` or
   * `description` when it is refused — then nothing is stored.
   */
  addDocument(
    structureId: string,
    file: File,
    details: DocumentDetailsRequest,
  ): CancelablePromise<CreatedResponse> {
    return this.doRequest<CreatedResponse>(this.config, {
      method: 'POST',
      url: '/v1/structures/{structureId}/documents',
      path: { structureId },
      formData: {
        file,
        // A Blob, so the part carries its JSON type and the server reads it as the object it is.
        details: new Blob([JSON.stringify(details)], { type: 'application/json' }),
      },
    });
  }

  /** Changes a document's type and description. 400 with `fieldErrors` when a field is refused. */
  updateDocument(
    structureId: string,
    fileId: string,
    request: DocumentUpdateRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/documents/{fileId}',
      path: { structureId, fileId },
      body: request,
    });
  }

  /** Deletes a document and its file. 404 when the structure has no such document. */
  deleteDocument(structureId: string, fileId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/structures/{structureId}/documents/{fileId}',
      path: { structureId, fileId },
    });
  }

  /** The top of a structure's Inspections tab: its comments, schedule and close proximity record. */
  getInspectionSchedule(structureId: string): CancelablePromise<InspectionScheduleResponse> {
    return this.doRequest<InspectionScheduleResponse>(this.config, {
      method: 'GET',
      url: '/v1/structures/{structureId}/inspection-schedule',
      path: { structureId },
    });
  }

  /**
   * Records a completed close proximity inspection — P.Eng only. 400 with `fieldErrors` when the
   * date is refused; 409 when the structure stands on no site.
   */
  addCloseProximityInspection(
    structureId: string,
    request: CloseProximityInspectionRequest,
  ): CancelablePromise<CreatedResponse> {
    return this.doRequest<CreatedResponse>(this.config, {
      method: 'POST',
      url: '/v1/structures/{structureId}/close-proximity-inspections',
      path: { structureId },
      body: request,
    });
  }

  /** Adds a general comment — the Details tab's. 400 with `fieldErrors` keyed `comment`. */
  addStructureComment(
    structureId: string,
    request: CommentRequest,
  ): CancelablePromise<CreatedResponse> {
    return this.doRequest<CreatedResponse>(this.config, {
      method: 'POST',
      url: '/v1/structures/{structureId}/comments',
      path: { structureId },
      body: request,
    });
  }

  /**
   * Adds a planned-inspection comment to a structure. 400 with `fieldErrors` when the text is
   * refused; 404 when there is no such structure.
   */
  addPlannedInspectionComment(
    structureId: string,
    request: CommentRequest,
  ): CancelablePromise<CreatedResponse> {
    return this.doRequest<CreatedResponse>(this.config, {
      method: 'POST',
      url: '/v1/structures/{structureId}/planned-inspection-comments',
      path: { structureId },
      body: request,
    });
  }

  /**
   * Changes a comment's text, either kind. 400 with `fieldErrors` when the text is refused; 404
   * when the structure has no such comment.
   */
  updateStructureComment(
    structureId: string,
    commentId: string,
    request: CommentRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/comments/{commentId}',
      path: { structureId, commentId },
      body: request,
    });
  }

  /**
   * Saves the structure's inspection schedule. 400 with `fieldErrors` when a field is refused; 404
   * when there is no such structure.
   */
  updateInspectionSchedule(
    structureId: string,
    request: InspectionScheduleRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/inspection-schedule',
      path: { structureId },
      body: request,
    });
  }

  /**
   * A page of a structure's inspections, newest first.
   *
   * @param pageNumber zero-based, as the backend expects
   * @param includeBeforeInstall also those from before the superstructure went in
   */
  getStructureInspections(
    structureId: string,
    pageNumber: number,
    pageSize: number,
    includeBeforeInstall: boolean,
  ): CancelablePromise<InspectionsResponse> {
    return this.doRequest<InspectionsResponse>(this.config, {
      method: 'GET',
      url: '/v1/structures/{structureId}/inspections',
      path: { structureId },
      query: { pageNumber, pageSize, includeBeforeInstall },
    });
  }

  /**
   * A page of a structure's repairs, in legacy's order.
   *
   * @param view outstanding only, or all
   * @param pageNumber zero-based, as the backend expects
   * @param includeBeforeInstall also those raised before the superstructure went in
   */
  getStructureRepairs(
    structureId: string,
    view: RepairView,
    pageNumber: number,
    pageSize: number,
    includeBeforeInstall: boolean,
  ): CancelablePromise<ItemListing<StructureRepair>> {
    return this.doRequest<ItemListing<StructureRepair>>(this.config, {
      method: 'GET',
      url: '/v1/structures/{structureId}/repairs',
      path: { structureId },
      query: { view, pageNumber, pageSize, includeBeforeInstall },
    });
  }

  /**
   * A page of a structure's monitoring items, in legacy's order.
   *
   * @param view outstanding only, or all
   * @param pageNumber zero-based, as the backend expects
   * @param includeBeforeInstall also those raised before the superstructure went in
   */
  getStructureMonitors(
    structureId: string,
    view: MonitorView,
    pageNumber: number,
    pageSize: number,
    includeBeforeInstall: boolean,
  ): CancelablePromise<ItemListing<StructureMonitor>> {
    return this.doRequest<ItemListing<StructureMonitor>>(this.config, {
      method: 'GET',
      url: '/v1/structures/{structureId}/monitors',
      path: { structureId },
      query: { view, pageNumber, pageSize, includeBeforeInstall },
    });
  }

  /**
   * Adds a monitoring item to a structure, Suggested and numbered next. 400 with `fieldErrors` when
   * a field is refused.
   */
  createStructureMonitor(
    structureId: string,
    request: MonitorCreateRequest,
  ): CancelablePromise<MonitorCreatedResponse> {
    return this.doRequest<MonitorCreatedResponse>(this.config, {
      method: 'POST',
      url: '/v1/structures/{structureId}/monitors',
      path: { structureId },
      body: request,
    });
  }

  /**
   * Saves an edit to one monitoring item. 400 with `fieldErrors` when a field is refused; 404 when
   * the structure has no such item.
   */
  updateStructureMonitor(
    structureId: string,
    monitorId: string,
    request: MonitorUpdateRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/monitors/{monitorId}',
      path: { structureId, monitorId },
      body: request,
    });
  }

  /**
   * The repair types that apply to a structure, one entry per group each falls in, in legacy's
   * order: by group, then the type's order.
   */
  getStructureRepairTypes(structureId: string): CancelablePromise<RepairTypeOption[]> {
    return this.doRequest<RepairTypeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/structures/{structureId}/repair-types',
      path: { structureId },
    });
  }

  /**
   * Adds a repair to a structure, Suggested and numbered next. 400 with `fieldErrors` when a field
   * is refused.
   */
  createStructureRepair(
    structureId: string,
    request: RepairCreateRequest,
  ): CancelablePromise<RepairCreatedResponse> {
    return this.doRequest<RepairCreatedResponse>(this.config, {
      method: 'POST',
      url: '/v1/structures/{structureId}/repairs',
      path: { structureId },
      body: request,
    });
  }

  /**
   * Saves an edit to one repair. 400 with `fieldErrors` when a field is refused; 404 when the
   * structure has no such repair.
   */
  updateStructureRepair(
    structureId: string,
    repairId: string,
    request: RepairUpdateRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/structures/{structureId}/repairs/{repairId}',
      path: { structureId, repairId },
      body: request,
    });
  }

  /** Deletes one repair of a structure. 404 when the structure has no such repair. */
  deleteStructureRepair(structureId: string, repairId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/structures/{structureId}/repairs/{repairId}',
      path: { structureId, repairId },
    });
  }

  /** Deletes one monitoring item of a structure. 404 when the structure has no such item. */
  deleteStructureMonitor(structureId: string, monitorId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/structures/{structureId}/monitors/{monitorId}',
      path: { structureId, monitorId },
    });
  }
}

/** `maintainerLabel` is what the maintainer lookup displays; the server filters on the key. */
const DISPLAY_ONLY: ReadonlySet<string> = new Set(['maintainerLabel']);

const populated = (criteria: StructureSearchCriteria): Record<string, string | boolean> => {
  const query: Record<string, string | boolean> = {};
  for (const [field, value] of Object.entries(criteria)) {
    if (DISPLAY_ONLY.has(field)) {
      continue;
    }
    if (typeof value === 'string' && value.trim() !== '') {
      query[field] = value.trim();
    } else if (value === true) {
      query[field] = true;
    }
  }
  return query;
};
