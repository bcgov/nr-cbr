import type { CancelablePromise } from '@/config/api/CancelablePromise';

import { HttpClient, type APIConfig } from '@/config/api/types';

/** One road section as `/api/v1/roads` returns it. */
export type RoadSectionResponse = {
  forestFileId: string;
  roadSectionId: string;
  /** The section's name — "Forest Service Road" on the site form. May be null. */
  forestServiceRoad: string | null;
  /**
   * The org unit the road implies, which the site form writes into Forest District.
   *
   * <p>It is the road's `FOREST_REGION` — legacy maps that column to `ORG_UNIT_NO` and assigns it
   * to the district. Carried as legacy carries it; see the backend record for the caveat.
   */
  orgUnitNo: number | null;
};

/** What the road lookup dialog asks on. Every criterion optional, every one a partial match. */
export type RoadSearchCriteria = {
  forestServiceRoad: string;
  forestFileId: string;
  roadSectionId: string;
  /** `FILE_TYPE_CODE` — B01 road permit, B40 forest service road, S01/S02 special use. */
  tenureType: string;
  clientName: string;
  clientNumber: string;
};

/** One row of the road search. The client is null for a file nobody holds. */
export type RoadSearchResult = {
  forestServiceRoad: string | null;
  forestFileId: string;
  roadSectionId: string;
  tenureType: string | null;
  clientName: string | null;
  clientNumber: string | null;
};

export const EMPTY_ROAD_CRITERIA: RoadSearchCriteria = {
  forestServiceRoad: '',
  forestFileId: '',
  roadSectionId: '',
  tenureType: '',
  clientName: '',
  clientNumber: '',
};

/**
 * The road-section lookup behind "Forest Service Road", backed by `/api/v1/roads`.
 */
export class RoadService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  /**
   * The road section a Project File ID# and a Br. name between them.
   *
   * <p>404 when there is none, which is the ordinary answer while either half is still being
   * typed. Callers treat that as "no road yet" and show nothing.
   */
  /**
   * Roads matching the dialog's criteria, at most two hundred — legacy's own cap.
   *
   * <p>Blank criteria are dropped rather than sent: the server treats a blank as unset either way,
   * and a URL carrying all six is unreadable in the place this gets debugged.
   */
  searchRoads(criteria: RoadSearchCriteria): CancelablePromise<RoadSearchResult[]> {
    return this.doRequest<RoadSearchResult[]>(this.config, {
      method: 'GET',
      url: '/v1/roads/search',
      query: populated(criteria),
    });
  }

  getRoadSection(
    forestFileId: string,
    roadSectionId: string,
  ): CancelablePromise<RoadSectionResponse> {
    return this.doRequest<RoadSectionResponse>(this.config, {
      method: 'GET',
      url: '/v1/roads',
      query: { forestFileId, roadSectionId },
    });
  }
}

/** Drops blank criteria, leaving only what the user actually asked for. */
const populated = (criteria: RoadSearchCriteria): Record<string, string> => {
  const query: Record<string, string> = {};
  for (const [field, value] of Object.entries(criteria)) {
    if (value.trim() !== '') {
      query[field] = value.trim();
    }
  }
  return query;
};
