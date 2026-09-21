import { keepPreviousData, useQuery } from '@tanstack/react-query';

import type {
  InspectionSearchCriteria,
  InspectionSearchResult,
  PagedResponse,
} from '@/pages/InspectionSearch/types';
import type { UseQueryResult } from '@tanstack/react-query';

import API from '@/services/APIs';

export const INSPECTION_SEARCH_QUERY_KEY = 'inspection-search';

/**
 * Runs an inspection search, or nothing at all until one has been submitted.
 *
 * <p><b>`criteria` must be the submitted criteria, not the form's live state.</b> It is part of the
 * query key, so binding it to the form would fire a search — and a new cache entry — on every
 * keystroke. The screen keeps the two separate for that reason.
 *
 * <p>No `staleTime`: unlike the code tables, an inspection's status changes while people work — a
 * colleague can move one from Submitted to Reviewed mid-session — and a user who returns to a result
 * set expects it to be current. The app-wide default (stale immediately, refetch on mount and focus)
 * is the right behaviour here.
 *
 * @param criteria   the criteria as submitted, or `null` before the first search
 * @param pageNumber zero-based, as the backend expects
 */
export const useInspectionSearch = (
  criteria: InspectionSearchCriteria | null,
  pageNumber: number,
  pageSize: number,
): UseQueryResult<PagedResponse<InspectionSearchResult>> =>
  useQuery({
    queryKey: [INSPECTION_SEARCH_QUERY_KEY, criteria, pageNumber, pageSize],
    queryFn: () =>
      API.inspectionSearch.searchInspections(
        criteria as InspectionSearchCriteria,
        pageNumber,
        pageSize,
      ),
    enabled: criteria !== null,
    // Keeps the current page on screen while the next one loads, instead of collapsing the table to
    // empty and back. Without it, paging through results flashes the empty state between pages —
    // which reads as a search that found nothing rather than one still loading.
    placeholderData: keepPreviousData,
  });
