import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type {
  InspectionSearchCriteria,
  InspectionSearchResult,
  PagedResponse,
} from '@/pages/InspectionSearch/types';

import { HttpClient, type APIConfig } from '@/config/api/types';

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
  ): CancelablePromise<PagedResponse<InspectionSearchResult>> {
    return this.doRequest<PagedResponse<InspectionSearchResult>>(this.config, {
      method: 'GET',
      url: '/v1/inspections/search',
      query: { ...populated(criteria), pageNumber, pageSize },
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
}

/**
 * Drops blank strings and false booleans, leaving only the criteria the user actually set.
 *
 * <p>`sortBy` always has a value and is always sent — it is not a criterion, it is what the server
 * orders by, and omitting it would silently fall through to the legacy default ordering.
 */
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
