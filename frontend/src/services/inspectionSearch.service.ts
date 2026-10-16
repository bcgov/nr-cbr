import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type {
  InspectionDetailResponse,
  InspectionHazardsRequest,
  InspectionUpdateRequest,
} from '@/pages/InspectionDetail/inspectionDetail';
import type {
  InspectionSearchCriteria,
  InspectionSearchResult,
  InspectionSort,
  PagedResponse,
} from '@/pages/InspectionSearch/types';
import type {
  InspectionCreateRequest,
  InspectionFormResponse,
} from '@/pages/NewInspection/inspectionForm';
import type { DocumentDetailsRequest } from '@/pages/StructureDetail/documentsResponse';
import type {
  MonitorCreateRequest,
  MonitorUpdateRequest,
} from '@/pages/StructureDetail/monitorsResponse';
import type {
  RepairCreateRequest,
  RepairUpdateRequest,
} from '@/pages/StructureDetail/repairsResponse';

import { HttpClient, type APIConfig } from '@/config/api/types';
import { sortParams } from '@/utils/headerSort';

/**
 * Inspection Search, backed by `/api/v1/inspections/search`.
 */
export class InspectionSearchService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  /**
   * One page of matching inspections.
   *
   * <p>`pageNumber` is zero-based, matching the backend. Blank criteria are dropped rather than sent
   * as empty parameters: the server treats a blank as unset either way, but a URL carrying all
   * nineteen is unreadable in a log or a network tab, which is where this gets debugged.
   *
   * <p>The four toggles are sent only when true. Three of them are one-way filters — "Close
   * Proximity Inspection Required?" on means those inspections only, off means no filter rather than
   * the others — so `false` and absent mean the same thing, and sending `false` would suggest
   * otherwise. The fourth, "Include Inspections for Structures at Previous Sites?", is not a filter
   * at all: it changes which column Site # is matched against, and off is again the absence of that.
   *
   * <p><b>A request with no criteria comes back empty rather than with everything.</b> That is the
   * server's refusal, matching legacy's `errors.search.select`; the form refuses first, so this only
   * matters to a caller that is not the form.
   */
  searchInspections(
    criteria: InspectionSearchCriteria,
    pageNumber: number,
    pageSize: number,
    sort: InspectionSort | null = null,
  ): CancelablePromise<PagedResponse<InspectionSearchResult>> {
    return this.doRequest<PagedResponse<InspectionSearchResult>>(this.config, {
      method: 'GET',
      url: '/v1/inspections/search',
      // The sort only when a header was clicked; absent, the server answers in legacy's default
      // order — structure name, then the newest inspection first.
      query: { ...populated(criteria), pageNumber, pageSize, ...sortParams(sort) },
    });
  }

  /** One inspection, for its page. 404 when there is no such inspection. */
  getInspection(inspectionId: string): CancelablePromise<InspectionDetailResponse> {
    return this.doRequest<InspectionDetailResponse>(this.config, {
      method: 'GET',
      url: '/v1/inspections/{inspectionId}',
      path: { inspectionId },
    });
  }

  /** The questions an inspection of the structure asks, and its spans and piers. */
  getInspectionForm(structureId: string): CancelablePromise<InspectionFormResponse> {
    return this.doRequest<InspectionFormResponse>(this.config, {
      method: 'GET',
      url: '/v1/inspections/form',
      query: { structureId },
    });
  }

  /** Adds an inspection, In Progress or Submitted. 400 with `fieldErrors` keyed by field. */
  createInspection(request: InspectionCreateRequest): CancelablePromise<{ id: string }> {
    return this.doRequest<{ id: string }>(this.config, {
      method: 'POST',
      url: '/v1/inspections',
      body: request,
    });
  }

  /**
   * Deletes an offline inspection.
   *
   * <p>204 on success. 404 if it is already gone, 409 if it exists but is no longer offline — the
   * body carries a sentence naming the status it holds instead, which the caller should show rather
   * than replace with wording of its own.
   *
   * <p><b>Irreversible, and it takes more with it than the row.</b> The backend removes the
   * inspection's attachments, repairs, monitor items, filled-in form, load rating and its entire
   * status history, then re-derives the structure's load rating from whatever is left. Nothing
   * records that the inspection existed.
   *
   * <p>Only an offline inspection can be deleted. The results table offers the control on an `OFL`
   * row only, and the server refuses anything else — legacy enforces that rule in its JSP alone, so
   * a hand-typed URL could delete a reviewed inspection there.
   */
  deleteInspection(inspectionId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/inspections/{inspectionId}',
      path: { inspectionId },
    });
  }

  /** Adds a repair from an inspection's page, raised by it. 409 once it is reviewed. */
  addInspectionRepair(
    inspectionId: string,
    request: RepairCreateRequest,
  ): CancelablePromise<{ id: string; number: number }> {
    return this.doRequest<{ id: string; number: number }>(this.config, {
      method: 'POST',
      url: '/v1/inspections/{inspectionId}/repairs',
      path: { inspectionId },
      body: request,
    });
  }

  /** Saves an edit to one of the structure's repairs, from an inspection's page. */
  updateInspectionRepair(
    inspectionId: string,
    repairId: string,
    request: RepairUpdateRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/inspections/{inspectionId}/repairs/{repairId}',
      path: { inspectionId, repairId },
      body: request,
    });
  }

  /** Deletes one of the structure's repairs, from an inspection's page. */
  deleteInspectionRepair(inspectionId: string, repairId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/inspections/{inspectionId}/repairs/{repairId}',
      path: { inspectionId, repairId },
    });
  }

  /** Adds a monitoring item from an inspection's page, raised by it. 409 once it is reviewed. */
  addInspectionMonitor(
    inspectionId: string,
    request: MonitorCreateRequest,
  ): CancelablePromise<{ id: string; number: number }> {
    return this.doRequest<{ id: string; number: number }>(this.config, {
      method: 'POST',
      url: '/v1/inspections/{inspectionId}/monitors',
      path: { inspectionId },
      body: request,
    });
  }

  /** Saves an edit to one of the structure's monitoring items, from an inspection's page. */
  updateInspectionMonitor(
    inspectionId: string,
    monitorId: string,
    request: MonitorUpdateRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/inspections/{inspectionId}/monitors/{monitorId}',
      path: { inspectionId, monitorId },
      body: request,
    });
  }

  /** Deletes one of the structure's monitoring items, from an inspection's page. */
  deleteInspectionMonitor(inspectionId: string, monitorId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/inspections/{inspectionId}/monitors/{monitorId}',
      path: { inspectionId, monitorId },
    });
  }

  /**
   * Adds a document or photo from an inspection's page, the inspection's. Multipart, as the
   * structure page's. 409 once it is Reviewed or Accepted.
   */
  addInspectionDocument(
    inspectionId: string,
    file: File,
    details: DocumentDetailsRequest,
  ): CancelablePromise<{ id: string }> {
    return this.doRequest<{ id: string }>(this.config, {
      method: 'POST',
      url: '/v1/inspections/{inspectionId}/documents',
      path: { inspectionId },
      formData: {
        file,
        // A Blob, so the part carries its JSON type and the server reads it as the object it is.
        details: new Blob([JSON.stringify(details)], { type: 'application/json' }),
      },
    });
  }

  /** Deletes one of the structure's documents, from an inspection's page. */
  deleteInspectionDocument(inspectionId: string, fileId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/inspections/{inspectionId}/documents/{fileId}',
      path: { inspectionId, fileId },
    });
  }

  /** Saves an inspection's Hydraulic Hazards. 400 with `fieldErrors`; 409 once it is reviewed. */
  updateInspectionHazards(
    inspectionId: string,
    request: InspectionHazardsRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/inspections/{inspectionId}/hazards',
      path: { inspectionId },
      body: request,
    });
  }

  /** Saves an inspection's Inspection card. 400 with `fieldErrors`; 409 once it is reviewed. */
  updateInspection(
    inspectionId: string,
    request: InspectionUpdateRequest,
  ): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/inspections/{inspectionId}',
      path: { inspectionId },
      body: request,
    });
  }

  /** Saves an inspection's comments. 400 with `fieldErrors`; 409 once it is reviewed. */
  updateInspectionComment(inspectionId: string, comment: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'PUT',
      url: '/v1/inspections/{inspectionId}/comment',
      path: { inspectionId },
      body: { comment },
    });
  }
}

/** Drops blank strings and false booleans, leaving only the criteria the user actually set. */
const populated = (criteria: InspectionSearchCriteria): Record<string, string | boolean> => {
  const query: Record<string, string | boolean> = {};
  for (const [field, value] of Object.entries(criteria)) {
    if (typeof value === 'string' && value.trim() !== '') {
      query[field] = value.trim();
    } else if (value === true) {
      query[field] = true;
    }
  }
  return query;
};
