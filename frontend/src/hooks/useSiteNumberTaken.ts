import { useQuery } from '@tanstack/react-query';

import useDebounce from '@/hooks/useDebounce';

import API from '@/services/APIs';

/** Long enough that typing a fourteen-character site number is one request rather than fourteen. */
export const SITE_NUMBER_DEBOUNCE_MS = 400;

export const SITE_NUMBER_TAKEN_QUERY_KEY = 'site-number-taken';

/**
 * Whether a site number is already in use — the check legacy makes before it will create a site.
 *
 * <p>`SiteForm.validate` looks the number up and refuses the save with `errors.site.exists` when it
 * finds something (`SiteForm.java:275-279`), and the live validate does the same to paint the box
 * as the user types. <b>`CROSSING_SITE_ID` is a natural key the user types</b>, so nothing else
 * stands between two districts choosing the same name for a crossing; without this the second one
 * would find out from a constraint violation after filling in thirty fields.
 *
 * <p>Answered by the same `GET /api/v1/sites/{siteId}` the detail page uses: a 200 means taken, a
 * 404 means free. A dedicated endpoint would be a second way to ask one question, and this one is
 * already built and already gated on the same read permission.
 *
 * <p><b>A failed request reads as "not taken".</b> Refusing to save because a check could not be
 * made would block a legitimate site on an unrelated outage — and the real protection is the
 * primary key, which cannot be talked out of it.
 */
export const useSiteNumberTaken = (siteId: string, enabled = true): boolean => {
  const debounced = useDebounce(siteId.trim(), SITE_NUMBER_DEBOUNCE_MS);

  const { data } = useQuery({
    queryKey: [SITE_NUMBER_TAKEN_QUERY_KEY, debounced],
    queryFn: () =>
      API.siteSearch
        .getSite(debounced)
        .then(() => true)
        .catch(() => false),
    enabled: enabled && debounced !== '',
    // The answer cannot change while this form is open except by someone else creating the same
    // number in the same minute, which the primary key catches anyway.
    staleTime: 60_000,
    retry: false,
  });

  return data ?? false;
};
