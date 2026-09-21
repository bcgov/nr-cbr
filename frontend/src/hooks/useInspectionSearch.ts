import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type {
  InspectionSearchCriteria,
  InspectionSearchResult,
  PagedResponse,
} from '@/pages/InspectionSearch/types';
import type { UseMutationResult, UseQueryResult } from '@tanstack/react-query';

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

/**
 * Deletes an offline inspection, then makes every search result reflect it.
 *
 * <p>Invalidating the whole `inspection-search` key rather than removing the row locally: the
 * deleted inspection changes the total and therefore the paging, and a page that drops a row
 * without re-counting shows "20 matches" over nineteen rows. Refetching is one request and cannot
 * disagree with the server.
 *
 * <p>The error is deliberately not translated here. A 409 arrives with a sentence naming the status
 * the inspection actually holds — it stopped being offline while the results were on screen — and
 * the user cannot see that from a table they may not have refreshed, so replacing it with "could
 * not delete" would leave them with no next step.
 */
export const useDeleteInspection = (): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (inspectionId: string) => API.inspectionSearch.deleteInspection(inspectionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [INSPECTION_SEARCH_QUERY_KEY] }),
  });
};
