import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { PagedResponse, SiteSearchCriteria, SiteSearchResult } from '@/pages/SiteSearch/types';
import type { UseMutationResult, UseQueryResult } from '@tanstack/react-query';

import API from '@/services/APIs';

export const SITE_SEARCH_QUERY_KEY = 'site-search';

/**
 * Runs a site search, or nothing at all until one has been submitted.
 *
 * <p><b>`criteria` must be the submitted criteria, not the form's live state.</b> It is part of the
 * query key, so binding it to the form would fire a search — and a new cache entry — on every
 * keystroke. The screen keeps the two separate for that reason.
 *
 * <p>No `staleTime`: unlike the code tables, a site's data changes while people work, and a user who
 * returns to a result set expects it to be current. The app-wide default (stale immediately,
 * refetch on mount and focus) is the right behaviour here.
 *
 * @param criteria   the criteria as submitted, or `null` before the first search
 * @param pageNumber zero-based, as the backend expects
 */
export const useSiteSearch = (
  criteria: SiteSearchCriteria | null,
  pageNumber: number,
  pageSize: number,
): UseQueryResult<PagedResponse<SiteSearchResult>> =>
  useQuery({
    queryKey: [SITE_SEARCH_QUERY_KEY, criteria, pageNumber, pageSize],
    queryFn: () => API.siteSearch.searchSites(criteria as SiteSearchCriteria, pageNumber, pageSize),
    enabled: criteria !== null,
    // Keeps the current page on screen while the next one loads, instead of collapsing the table to
    // empty and back. Without it, paging through results flashes "No sites found." between pages —
    // which reads as a search that found nothing rather than one still loading.
    placeholderData: keepPreviousData,
  });

/**
 * Deletes a site, then makes every search result reflect it.
 *
 * <p>Invalidating the whole `site-search` key rather than removing the row locally: the deleted site
 * changes the total and therefore the paging, and a page that drops a row without re-counting shows
 * "20 matches" over nineteen rows. Refetching is one request and cannot disagree with the server.
 *
 * <p>The error is deliberately not translated here. A 409 arrives with a sentence naming what still
 * references the site — an archived structure, a close-proximity inspection — and the user cannot
 * see either of those from the results table, so replacing it with "could not delete" would leave
 * them with no next step.
 */
export const useDeleteSite = (): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (siteId: string) => API.siteSearch.deleteSite(siteId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [SITE_SEARCH_QUERY_KEY] }),
  });
};
