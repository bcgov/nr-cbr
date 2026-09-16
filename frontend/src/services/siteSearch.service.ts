import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type { PagedResponse, SiteSearchCriteria, SiteSearchResult } from '@/pages/SiteSearch/types';

import { HttpClient, type APIConfig } from '@/config/api/types';

/**
 * Site Search, backed by `/api/v1/sites/search`.
 */
export class SiteSearchService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  /**
   * One page of matching sites.
   *
   * <p>`pageNumber` is zero-based, matching the backend. Blank criteria are dropped rather than sent
   * as empty parameters: the server treats a blank as unset either way, but a URL carrying all
   * seventeen is unreadable in a log or a network tab, which is where this gets debugged.
   *
   * <p>The two booleans are sent only when true. They are one-way filters — "Capital Road" on means
   * capital roads only, off means no filter rather than non-capital roads only — so `false` and
   * absent mean the same thing, and sending `false` would suggest otherwise.
   */
  searchSites(
    criteria: SiteSearchCriteria,
    pageNumber: number,
    pageSize: number,
  ): CancelablePromise<PagedResponse<SiteSearchResult>> {
    return this.doRequest<PagedResponse<SiteSearchResult>>(this.config, {
      method: 'GET',
      url: '/v1/sites/search',
      query: { ...populated(criteria), pageNumber, pageSize },
    });
  }
}

/** Drops blank strings and false booleans, leaving only the criteria the user actually set. */
const populated = (criteria: SiteSearchCriteria): Record<string, string | boolean> => {
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
