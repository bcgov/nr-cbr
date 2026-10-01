import { useMutation, useQueryClient } from '@tanstack/react-query';

import type { SiteUpdateRequest } from '@/components/SiteForm/request';
import type { SiteErrors } from '@/components/SiteForm/validation';
import type { UseMutationResult } from '@tanstack/react-query';

import { fieldErrorsFrom } from '@/hooks/useCreateSite';
import { SITE_QUERY_KEY, SITE_SEARCH_QUERY_KEY } from '@/hooks/useSiteSearch';
import API from '@/services/APIs';

export type UpdateSiteResult = UseMutationResult<void, unknown, SiteUpdateRequest> & {
  /** What the server refused, ready to merge into the form's own error map. */
  fieldErrors: SiteErrors;
};

/**
 * Saves an edit to one site.
 *
 * <p>On success the site is read again, and every search result with it: the road's name, the
 * maintainer and the search rows all come from the server, and an echo of what was sent would miss
 * whatever the server kept or derived — a Level 1 save stores Site Details alone, and the road
 * segment is worked out afresh.
 *
 * <p>Refusals come back as field messages, as they do for a new site; see `useCreateSite`.
 */
export const useUpdateSite = (siteId: string | undefined): UpdateSiteResult => {
  const queryClient = useQueryClient();

  const mutation = useMutation<void, unknown, SiteUpdateRequest>({
    mutationFn: (site) => API.siteSearch.updateSite(siteId as string, site),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: [SITE_QUERY_KEY, siteId] }),
        queryClient.invalidateQueries({ queryKey: [SITE_SEARCH_QUERY_KEY] }),
      ]),
  });

  return { ...mutation, fieldErrors: fieldErrorsFrom(mutation.error) };
};
