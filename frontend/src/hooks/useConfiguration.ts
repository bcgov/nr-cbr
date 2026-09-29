import { queryOptions, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

import useDebounce from '@/hooks/useDebounce';

import type { RecreationProjectResponse } from '@/services/configuration.service';
import type { CodeOption, OrgUnitOption } from '@/types/configuration';
import type { UseQueryResult } from '@tanstack/react-query';

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

/** The same wait the road lookup uses — see `ROAD_SECTION_DEBOUNCE_MS`, and for the same reason. */
const RECREATION_PROJECT_DEBOUNCE_MS = 300;

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

export const structureTypeClassCodesQuery = queryOptions({
  queryKey: [CONFIGURATION_QUERY_KEY, 'structure-type-class-codes'],
  queryFn: () => API.configuration.getStructureTypeClassCodes(),
  staleTime: REFERENCE_DATA_STALE_TIME,
});

export const inspectionTypeCodesQuery = queryOptions({
  queryKey: [CONFIGURATION_QUERY_KEY, 'inspection-type-codes'],
  queryFn: () => API.configuration.getInspectionTypeCodes(),
  staleTime: REFERENCE_DATA_STALE_TIME,
});

export const inspectionReportStatusCodesQuery = queryOptions({
  queryKey: [CONFIGURATION_QUERY_KEY, 'inspection-report-status-codes'],
  queryFn: () => API.configuration.getInspectionReportStatusCodes(),
  staleTime: REFERENCE_DATA_STALE_TIME,
});

export const businessAreasQuery = queryOptions({
  queryKey: [CONFIGURATION_QUERY_KEY, 'business-areas'],
  queryFn: () => API.configuration.getBusinessAreas(),
  staleTime: REFERENCE_DATA_STALE_TIME,
});

export const forestDistrictsQuery = queryOptions({
  queryKey: [CONFIGURATION_QUERY_KEY, 'forest-districts'],
  queryFn: () => API.configuration.getForestDistricts(),
  staleTime: REFERENCE_DATA_STALE_TIME,
});

/**
 * The parameterless lookups each search screen draws on.
 *
 * <p>Grouped per screen rather than kept as one list of everything, because a screen's selects are
 * disabled while *its* reference data loads: one shared group would have Site Search waiting on the
 * four inspection lists it never reads, and Inspection Search waiting on site statuses. Forest
 * districts are in both because both filter by district.
 *
 * <p>`as const` keeps each a tuple of distinct option types rather than an array of their union —
 * some resolve to `CodeOption[]` and some to `OrgUnitOption[]`, and widening them would leave
 * `useQueries` unable to type each result.
 *
 * <p><b>Adding a lookup here means adding it to {@link usePrefetchConfiguration} too.</b> That
 * duplication is deliberate — see the note there.
 */
const SITE_SEARCH_LOOKUPS = [
  siteStatusCodesQuery,
  structureInspectionStatusCodesQuery,
  specialAccessCodesQuery,
  siteTypeCodesQuery,
  forestDistrictsQuery,
] as const;

const INSPECTION_SEARCH_LOOKUPS = [
  structureTypeClassCodesQuery,
  inspectionTypeCodesQuery,
  inspectionReportStatusCodesQuery,
  forestDistrictsQuery,
  businessAreasQuery,
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

export const useStructureTypeClassCodes = (): UseQueryResult<CodeOption[]> =>
  useQuery(structureTypeClassCodesQuery);

export const useInspectionTypeCodes = (): UseQueryResult<CodeOption[]> =>
  useQuery(inspectionTypeCodesQuery);

export const useInspectionReportStatusCodes = (): UseQueryResult<CodeOption[]> =>
  useQuery(inspectionReportStatusCodesQuery);

export const useBusinessAreas = (): UseQueryResult<OrgUnitOption[]> => useQuery(businessAreasQuery);

export const useForestDistricts = (): UseQueryResult<OrgUnitOption[]> =>
  useQuery(forestDistrictsQuery);

/**
 * The recreation districts for a project file — the Recreation District list on a recreation site.
 *
 * <p>Keyed on the file rather than on another org unit, and not warmed with the rest: the list
 * differs per file, so there is nothing to prefetch before one is chosen.
 */
export const useRecreationDistricts = (forestFileId: string): UseQueryResult<OrgUnitOption[]> =>
  useQuery({
    queryKey: [CONFIGURATION_QUERY_KEY, 'recreation-districts', forestFileId],
    queryFn: () => API.configuration.getRecreationDistricts(forestFileId),
    enabled: forestFileId.trim() !== '',
    staleTime: REFERENCE_DATA_STALE_TIME,
  });

/**
 * The project name for a recreation file — "Project Name" on the site form.
 *
 * <p>What a recreation site shows where a crossing shows its Forest Service Road, so the two are
 * never fetched together: this asks nothing unless the site type is `REC`.
 *
 * <p>Debounced, because the file id is typed a character at a time and each one would otherwise be
 * a request. The road lookup beside it is debounced for the same reason and by the same amount.
 */
export const useRecreationProjectName = (
  forestFileId: string,
  enabled: boolean,
): UseQueryResult<RecreationProjectResponse> => {
  const file = useDebounce(forestFileId.trim(), RECREATION_PROJECT_DEBOUNCE_MS);

  return useQuery({
    queryKey: [CONFIGURATION_QUERY_KEY, 'recreation-project-name', file],
    queryFn: () => API.configuration.getRecreationProjectName(file),
    enabled: enabled && file !== '',
    staleTime: REFERENCE_DATA_STALE_TIME,
  });
};

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

const combineReferenceData = (
  results: readonly { isLoading: boolean; isError: boolean }[],
): ReferenceDataState => ({
  isLoading: results.some((result) => result.isLoading),
  isError: results.some((result) => result.isError),
});

/**
 * The loading and error state of the lookups Site Search draws on, as one answer.
 *
 * <p>`useQueries` rather than reading each hook's flags at the call site: the selects are all
 * disabled or all not, and a screen assembling that from five booleans gets it subtly wrong the
 * first time a sixth is added.
 */
export const useSiteReferenceDataState = (): ReferenceDataState =>
  useQueries({ queries: SITE_SEARCH_LOOKUPS, combine: combineReferenceData });

/** The same, for the lookups Inspection Search draws on. */
export const useInspectionReferenceDataState = (): ReferenceDataState =>
  useQueries({ queries: INSPECTION_SEARCH_LOOKUPS, combine: combineReferenceData });

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
 * <p>Failures are deliberately swallowed. A warm-up that did not work should cost the user nothing:
 * the screen that actually needs the data reports its own failure, with the context to explain what
 * is missing. `query` rejects on error — unlike the `prefetchQuery` it replaces — so the `catch` is
 * what keeps a failed lookup from surfacing as an unhandled rejection.
 *
 * <p>The calls are written out rather than looped over the two tuples above. `query` infers its
 * generics from the options it is given, and iterating a tuple hands it the union of two payload
 * types, which no single call can satisfy; an earlier version got around that with a cast to a
 * now-deprecated type. Typed calls are worth more than the loop, at the cost of naming each lookup
 * twice.
 */
const ignoreWarmUpFailure = () => undefined;

export const usePrefetchConfiguration = (enabled: boolean): void => {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) {
      return;
    }
    // Each no-ops when the cache already holds data inside its staleTime, so this costs one round
    // of requests per session rather than one per sign-in-shaped state change.
    void queryClient.query(siteStatusCodesQuery).catch(ignoreWarmUpFailure);
    void queryClient.query(structureInspectionStatusCodesQuery).catch(ignoreWarmUpFailure);
    void queryClient.query(specialAccessCodesQuery).catch(ignoreWarmUpFailure);
    void queryClient.query(siteTypeCodesQuery).catch(ignoreWarmUpFailure);
    void queryClient.query(forestDistrictsQuery).catch(ignoreWarmUpFailure);
    void queryClient.query(structureTypeClassCodesQuery).catch(ignoreWarmUpFailure);
    void queryClient.query(inspectionTypeCodesQuery).catch(ignoreWarmUpFailure);
    void queryClient.query(inspectionReportStatusCodesQuery).catch(ignoreWarmUpFailure);
    void queryClient.query(businessAreasQuery).catch(ignoreWarmUpFailure);
  }, [enabled, queryClient]);
};
