import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type {
  PagedResponse,
  StructureSearchCriteria,
  StructureSearchResult,
  StructureSort,
} from '@/pages/StructureSearch/types';

import { HttpClient, type APIConfig } from '@/config/api/types';
import { sortParams } from '@/utils/headerSort';

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
