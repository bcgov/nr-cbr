import { useMutation, useQueryClient } from '@tanstack/react-query';

import type { SiteCreateRequest } from '@/components/SiteForm/request';
import type { SiteErrors } from '@/components/SiteForm/validation';
import type { SiteDetailResponse } from '@/pages/SiteDetail/siteResponse';
import type { UseMutationResult } from '@tanstack/react-query';

import { toFieldErrors } from '@/components/SiteForm/request';
import { SITE_SEARCH_QUERY_KEY } from '@/hooks/useSiteSearch';
import API from '@/services/APIs';

/**
 * The `fieldErrors` a refused save carries, if it carried any.
 *
 * <p>The backend puts them on the problem detail alongside `detail`, keyed by field. Anything else
 * — a 500, a network failure, a 403 — has none, and the caller falls back to the sentence.
 */
const fieldErrorsFrom = (error: unknown): SiteErrors => {
  const body = (error as { body?: unknown })?.body;
  if (body === null || typeof body !== 'object') return {};

  const raw = (body as Record<string, unknown>).fieldErrors;
  if (raw === null || typeof raw !== 'object') return {};

  const messages: Record<string, string> = {};
  for (const [field, message] of Object.entries(raw as Record<string, unknown>)) {
    if (typeof message === 'string') {
      messages[field] = message;
    }
  }
  return toFieldErrors(messages);
};

export type CreateSiteResult = UseMutationResult<SiteDetailResponse, unknown, SiteCreateRequest> & {
  /** What the server refused, ready to merge into the form's own error map. */
  fieldErrors: SiteErrors;
};

/**
 * Creates a site.
 *
 * <p><b>The server's refusals come back as field messages</b>, not as a banner. It applies the same
 * rules the form does — deliberately, because the form is a convenience and anything may reach the
 * endpoint without it — so a 400 here means the two disagreed, which is worth showing in the same
 * place the form shows its own.
 *
 * <p>Invalidates the search on success: the new site belongs in results the user may already have
 * on screen, and a stale list that does not contain a site they just created reads as a failed save.
 */
export const useCreateSite = (): CreateSiteResult => {
  const queryClient = useQueryClient();

  const mutation = useMutation<SiteDetailResponse, unknown, SiteCreateRequest>({
    mutationFn: (site) => API.siteSearch.createSite(site),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SITE_SEARCH_QUERY_KEY] });
    },
  });

  return { ...mutation, fieldErrors: fieldErrorsFrom(mutation.error) };
};
