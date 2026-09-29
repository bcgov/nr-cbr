import { keepPreviousData, useQuery } from '@tanstack/react-query';

import type { RoadSearchCriteria, RoadSearchResult } from '@/services/road.service';
import type { PagedResponse } from '@/types/api';
import type { UseQueryResult } from '@tanstack/react-query';

import API from '@/services/APIs';

export const ROAD_SEARCH_QUERY_KEY = 'road-search';

/**
 * Runs a road search, one page at a time, or nothing at all until one has been submitted.
 *
 * <p>The road lookup dialog's counterpart to `useSiteSearch`, and built the same way.
 * <b>`criteria` must be the submitted criteria, not the form's live state</b>: it is part of the
 * query key, so binding it to the form would fire a search on every keystroke — the behaviour the
 * dialog's Search button exists to avoid.
 *
 * @param criteria   the criteria as submitted, or `null` before the first search
 * @param pageNumber zero-based, as the backend expects
 */
export const useRoadSearch = (
  criteria: RoadSearchCriteria | null,
  pageNumber: number,
  pageSize: number,
): UseQueryResult<PagedResponse<RoadSearchResult>> =>
  useQuery({
    queryKey: [ROAD_SEARCH_QUERY_KEY, criteria, pageNumber, pageSize],
    queryFn: () => API.road.searchRoads(criteria as RoadSearchCriteria, pageNumber, pageSize),
    enabled: criteria !== null,
    // Keeps the current page on screen while the next one loads, rather than collapsing the table
    // to "No roads found." between pages — which reads as a search that found nothing.
    placeholderData: keepPreviousData,
  });
