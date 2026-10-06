import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type { StructureDetailResponse } from '@/pages/StructureDetail/structureResponse';
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
