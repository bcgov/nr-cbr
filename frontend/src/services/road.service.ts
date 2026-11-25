import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type { PagedResponse } from '@/types/api';
import type { CodeOption } from '@/types/configuration';
import type { SortOrder } from '@/utils/headerSort';

import { HttpClient, type APIConfig } from '@/config/api/types';
import { sortParams } from '@/utils/headerSort';

/** A Forest District a road section lies in, from LRMOPS. */
export type RoadDistrict = {
  /** The district's org unit number, as the site's Forest District stores it. */
  orgUnitNo: number | null;
  /** The district's code, e.g. `DPG`. */
  code: string | null;
  /** How much of the section lies in this district, in metres; null when LRMOPS's answer omits it. */
  lengthMetres?: number | null;
};

/** One road section as `/api/v1/roads` returns it, from LRMOPS. */
export type RoadSectionResponse = {
  forestFileId: string;
  roadSectionId: string;
  /** The section's name — "Forest Service Road" on the site form. May be null. */
  forestServiceRoad: string | null;
  /** LRMOPS's tenure type as stored — `RP-RETIRE` for a retired road permit. May be null. */
  tenureType?: string | null;
  /** The tenure holder's name. May be null. */
  clientName?: string | null;
  /**
   * The Forest Districts the section lies in. One: the site form fills it in. Two or more (the
   * section crosses a district boundary): the user picks one of them. May be empty.
   */
  districts: RoadDistrict[];
};

/** What the road lookup dialog asks on. Every criterion optional, every one a partial match. */
export type RoadSearchCriteria = {
  forestServiceRoad: string;
  forestFileId: string;
  roadSectionId: string;
  /** LRMOPS's road tenure type, matched exactly — `FSR`, `FSR-RETIRE`, `RP`, … (see `getTenureTypes`). */
  tenureType: string;
  /** The tenure holder's name, a partial match. LRMOPS records no client number. */
  clientName: string;
};

/** One row of the road search, from LRMOPS. The client is null when LRMOPS records none. */
export type RoadSearchResult = {
  forestServiceRoad: string | null;
  forestFileId: string;
  roadSectionId: string;
  tenureType: string | null;
  clientName: string | null;
};

/**
 * The dialog's sortable columns — the backend's `RoadSortColumn`, one per header. New in CBR:
 * legacy's dialog had one fixed order (road name, file, branch), still what a search answers with
 * until a header is clicked.
 */
export type RoadSortColumn =
  'FOREST_SERVICE_ROAD' | 'PROJECT_FILE' | 'BRANCH' | 'TENURE_TYPE' | 'CLIENT_NAME';

/** The columns the user sorted by, the first deciding. Empty is legacy's order. */
export type RoadSort = SortOrder<RoadSortColumn>;

export const EMPTY_ROAD_CRITERIA: RoadSearchCriteria = {
  forestServiceRoad: '',
  forestFileId: '',
  roadSectionId: '',
  tenureType: '',
  clientName: '',
};

/** The message for a road lookup the LRM Ops API could not answer (a 503 from CBR's backend). */
export const ROAD_LOOKUP_UNAVAILABLE = 'Road lookup is unavailable right now.';

/** Whether a failed road call means the LRM Ops API could not answer, rather than "no road". */
export const isRoadLookupUnavailable = (error: unknown): boolean =>
  (error as { status?: unknown } | null)?.status === 503;

/**
 * The road-section lookup behind "Forest Service Road", backed by `/api/v1/roads` — which reads
 * LRMOPS through the LRM Ops API, and answers 503 when that cannot answer.
 */
export class RoadService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  /**
   * One page of the roads matching the dialog's criteria, with the true total.
   *
   * <p>`pageNumber` is zero-based, matching the backend. Blank criteria are dropped rather than
   * sent: the server treats a blank as unset either way, and a URL carrying all six is unreadable
   * in the place this gets debugged.
   *
   * @param sort the header sorted by; legacy's order when absent
   */
  searchRoads(
    criteria: RoadSearchCriteria,
    pageNumber: number,
    pageSize: number,
    sort: RoadSort = [],
  ): CancelablePromise<PagedResponse<RoadSearchResult>> {
    return this.doRequest<PagedResponse<RoadSearchResult>>(this.config, {
      method: 'GET',
      url: '/v1/roads/search',
      query: { ...populated(criteria), pageNumber, pageSize, ...sortParams(sort) },
    });
  }

  /**
   * The road section a Project File ID# and a Br. name between them.
   *
   * <p>404 when there is none, which is the ordinary answer while either half is still being
   * typed. Callers treat that as "no road yet" and show nothing. 503 when LRMOPS can't be asked.
   */
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

  /**
   * LRMOPS's road tenure types with their descriptions, retired variants included, for the dialog's
   * Tenure Type dropdown — already in the order to show them (FSR, FSR-RETIRE, RP, …).
   */
  getTenureTypes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/roads/tenure-types',
    });
  }

  /**
   * Road names containing `term`, those starting with it first, at most twenty: the Forest
   * Service Road pick list on the search screens, which filter on the name picked, exactly.
   * Empty for a term under three characters. 503 when LRMOPS can't be asked.
   */
  getRoadNames(term: string): CancelablePromise<string[]> {
    return this.doRequest<string[]>(this.config, {
      method: 'GET',
      url: '/v1/roads/names',
      query: { search: term },
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
