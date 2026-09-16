import { queryOptions, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

import type { CodeOption, OrgUnitOption } from '@/types/configuration';
import type { FetchQueryOptions, UseQueryResult } from '@tanstack/react-query';

import { THREE_HOURS } from '@/config/react-query/TimeUnits';
import API from '@/services/APIs';

/** Query key prefix for every `/api/v1/configuration/*` lookup. */
export const CONFIGURATION_QUERY_KEY = 'configuration';

/**
 * Code tables change a few times a decade, so these opt out of the app-wide defaults, which treat
 * everything as stale immediately and refetch on every mount and window focus. Three hours of
 * freshness means a user who moves between screens all day fetches each list a couple of times, not
 * once per visit; the backend caches them for the life of the pod anyway, so a refetch would mostly
 * be round trips to be told the same thing.
 */
const REFERENCE_DATA_STALE_TIME = THREE_HOURS;

/**
 * The lookups that take no parameter.
 *
 * <p>Declared as shared options rather than inline in each hook so that the prefetch below and the
 * reads cannot drift: a prefetch under a different key, or without the same `staleTime`, warms a
 * cache entry nothing reads and the screen fetches again anyway — which looks exactly like working
 * code.
 */
export const siteStatusCodesQuery = queryOptions({
  queryKey: [CONFIGURATION_QUERY_KEY, 'site-status-codes'],
  queryFn: () => API.configuration.getSiteStatusCodes(),
  staleTime: REFERENCE_DATA_STALE_TIME,
});

export const structureInspectionStatusCodesQuery = queryOptions({
  queryKey: [CONFIGURATION_QUERY_KEY, 'structure-inspection-status-codes'],
  queryFn: () => API.configuration.getStructureInspectionStatusCodes(),
  staleTime: REFERENCE_DATA_STALE_TIME,
});

export const specialAccessCodesQuery = queryOptions({
  queryKey: [CONFIGURATION_QUERY_KEY, 'special-access-codes'],
  queryFn: () => API.configuration.getSpecialAccessCodes(),
  staleTime: REFERENCE_DATA_STALE_TIME,
});

export const siteTypeCodesQuery = queryOptions({
  queryKey: [CONFIGURATION_QUERY_KEY, 'site-type-codes'],
  queryFn: () => API.configuration.getSiteTypeCodes(),
  staleTime: REFERENCE_DATA_STALE_TIME,
});

export const forestDistrictsQuery = queryOptions({
  queryKey: [CONFIGURATION_QUERY_KEY, 'forest-districts'],
  queryFn: () => API.configuration.getForestDistricts(),
  staleTime: REFERENCE_DATA_STALE_TIME,
});

/**
 * Every parameterless lookup — the set that is worth warming up front.
 *
 * <p>`as const` keeps this a tuple of five distinct option types rather than an array of their
 * union: four resolve to `CodeOption[]` and one to `OrgUnitOption[]`, and widening them to a union
 * would leave `useQueries` unable to type each result.
 */
const PREFETCHABLE = [
  siteStatusCodesQuery,
  structureInspectionStatusCodesQuery,
  specialAccessCodesQuery,
  siteTypeCodesQuery,
  forestDistrictsQuery,
] as const;

/**
 * Management areas for one forest district.
 *
 * <p>The district is part of the key, so each district's areas are cached separately — one shared
 * key would serve one district's areas to every other district.
 *
 * <p>Disabled until a district is chosen. That is the legacy behaviour and also the honest one: a
 * management area is a *former* district that rolls up into the selected one, so "all management
 * areas" is not a question the screen asks.
 */
export const managementAreasQuery = (forestDistrictOrgUnitNo: string) =>
  queryOptions({
    queryKey: [CONFIGURATION_QUERY_KEY, 'management-areas', forestDistrictOrgUnitNo],
    queryFn: () => API.configuration.getManagementAreas(forestDistrictOrgUnitNo),
    staleTime: REFERENCE_DATA_STALE_TIME,
    enabled: forestDistrictOrgUnitNo !== '',
  });

export const useSiteStatusCodes = (): UseQueryResult<CodeOption[]> =>
  useQuery(siteStatusCodesQuery);

export const useStructureInspectionStatusCodes = (): UseQueryResult<CodeOption[]> =>
  useQuery(structureInspectionStatusCodesQuery);

export const useSpecialAccessCodes = (): UseQueryResult<CodeOption[]> =>
  useQuery(specialAccessCodesQuery);

export const useSiteTypeCodes = (): UseQueryResult<CodeOption[]> => useQuery(siteTypeCodesQuery);

export const useForestDistricts = (): UseQueryResult<OrgUnitOption[]> =>
  useQuery(forestDistrictsQuery);

export const useManagementAreas = (
  forestDistrictOrgUnitNo: string,
): UseQueryResult<OrgUnitOption[]> => useQuery(managementAreasQuery(forestDistrictOrgUnitNo));

/** What a screen needs to know about the reference data as a whole. */
export type ReferenceDataState = {
  /** True while any parameterless lookup is still loading. */
  isLoading: boolean;
  /** True when at least one of them failed. */
  isError: boolean;
};

/**
 * The loading and error state of the parameterless lookups, as one answer.
 *
 * <p>`useQueries` rather than reading each hook's flags at the call site: the selects are all
 * disabled or all not, and a screen assembling that from five booleans gets it subtly wrong the
 * first time a sixth is added.
 */
export const useReferenceDataState = (): ReferenceDataState =>
  useQueries({
    queries: PREFETCHABLE,
    combine: (results) => ({
      isLoading: results.some((result) => result.isLoading),
      isError: results.some((result) => result.isError),
    }),
  });

/**
 * Warms the parameterless lookups once, in the background, as soon as there is a session.
 *
 * <p>Reference data is the same for everybody and barely changes, so fetching it while the user is
 * still reading the landing screen means the first screen that needs a dropdown renders with it
 * already filled instead of briefly disabled.
 *
 * <p>Management areas are deliberately <b>not</b> warmed: the lookup needs a district, there are
 * roughly forty of them, and the user will pick one. Warming every district up front would turn one
 * idle request into forty to save a round trip on one.
 *
 * <p><b>`enabled` must be "there is a session", not "the app has mounted".</b> These endpoints are
 * gated on the READ authority, and every request first runs `ensureSessionFresh`, which signs out
 * and hard-redirects to the app root when there is no token. Firing this on a public page would
 * therefore reload that page, which would mount this again and fire it again — a redirect loop on
 * the one screen a signed-out user is supposed to be able to sit on. Gating on the session is what
 * makes prefetching safe here, not an optimisation.
 *
 * <p>Failures are deliberately swallowed: `prefetchQuery` never rejects, and a warm-up that did not
 * work should cost the user nothing. The screen that actually needs the data reports its own
 * failure, with the context to explain what is missing.
 */
export const usePrefetchConfiguration = (enabled: boolean): void => {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) {
      return;
    }
    // Each no-ops when the cache already holds data inside its staleTime, so this costs one round
    // of requests per session rather than one per sign-in-shaped state change.
    PREFETCHABLE.forEach((options) => {
      // Cast because the five entries resolve to two different payload types and no single
      // prefetchQuery signature accepts both. Nothing here depends on the payload type: the call
      // runs the queryFn and puts the result in the cache under the key the options already carry.
      void queryClient.prefetchQuery(options as unknown as FetchQueryOptions);
    });
  }, [enabled, queryClient]);
};
