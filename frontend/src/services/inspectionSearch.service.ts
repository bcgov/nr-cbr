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
