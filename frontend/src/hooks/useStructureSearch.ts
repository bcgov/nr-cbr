import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type {
  MonitorCreateRequest,
  MonitorUpdateRequest,
  MonitorView,
} from '@/pages/StructureDetail/monitorsResponse';
import type { RepairView } from '@/pages/StructureDetail/repairsResponse';
import type { DeleteTarget } from '@/pages/StructureSearch/selection';
import type {
  PagedResponse,
  StructureSearchCriteria,
  StructureSearchResult,
  StructureSort,
} from '@/pages/StructureSearch/types';
import type {
  RepairResponsibilityResponse,
  StructureArchiveResponse,
} from '@/services/structureSearch.service';
import type { UseMutationResult, UseQueryResult } from '@tanstack/react-query';

import API from '@/services/APIs';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

export const STRUCTURE_SEARCH_QUERY_KEY = 'structure-search';

/** Query key for one structure read by id — distinct from the search, which is keyed on criteria. */
export const STRUCTURE_QUERY_KEY = 'structure';

/**
 * One structure, for its page.
 *
 * <p>No `staleTime`, as for a site: a structure's data changes while people work, and someone
 * returning to it expects to see it as it is now.
 */
export const useStructure = (structureId: string | undefined) =>
  useQuery({
    queryKey: [STRUCTURE_QUERY_KEY, structureId],
    queryFn: () => API.structureSearch.getStructure(structureId as string),
    enabled: Boolean(structureId),
  });

/**
 * A bridge's spans and piers, fetched only once its tab is opened (`enabled`) — legacy loads its
 * later tabs after the page, and most visits never open this one. Under the structure's key, so
 * invalidating a structure refreshes its spans and piers too.
 */
export const useStructureSpansAndPiers = (structureId: string | undefined, enabled: boolean) =>
  useQuery({
    queryKey: [STRUCTURE_QUERY_KEY, structureId, 'spans-and-piers'],
    queryFn: () => API.structureSearch.getSpansAndPiers(structureId as string),
    enabled: enabled && Boolean(structureId),
  });

/** The top of a structure's Inspections tab, fetched only once the tab is opened (`enabled`). */
export const useStructureInspectionSchedule = (structureId: string | undefined, enabled: boolean) =>
  useQuery({
    queryKey: [STRUCTURE_QUERY_KEY, structureId, 'inspection-schedule'],
    queryFn: () => API.structureSearch.getInspectionSchedule(structureId as string),
    enabled: enabled && Boolean(structureId),
  });

/**
 * A page of a structure's inspections, fetched only once the tab is opened. The previous page stays
 * on screen while the next loads, as on Structure Search.
 *
 * @param pageNumber zero-based
 */
export const useStructureInspections = (
  structureId: string | undefined,
  enabled: boolean,
  pageNumber: number,
  pageSize: number,
  includeBeforeInstall: boolean,
) =>
  useQuery({
    queryKey: [
      STRUCTURE_QUERY_KEY,
      structureId,
      'inspections',
      pageNumber,
      pageSize,
      includeBeforeInstall,
    ],
    queryFn: () =>
      API.structureSearch.getStructureInspections(
        structureId as string,
        pageNumber,
        pageSize,
        includeBeforeInstall,
      ),
    enabled: enabled && Boolean(structureId),
    placeholderData: keepPreviousData,
  });

/**
 * A page of a structure's repairs, fetched only once the tab is opened. The previous page stays on
 * screen while the next loads.
 *
 * @param pageNumber zero-based
 */
export const useStructureRepairs = (
  structureId: string | undefined,
  enabled: boolean,
  view: RepairView,
  pageNumber: number,
  pageSize: number,
  includeBeforeInstall: boolean,
) =>
  useQuery({
    queryKey: [
      STRUCTURE_QUERY_KEY,
      structureId,
      'repairs',
      view,
      pageNumber,
      pageSize,
      includeBeforeInstall,
    ],
    queryFn: () =>
      API.structureSearch.getStructureRepairs(
        structureId as string,
        view,
        pageNumber,
        pageSize,
        includeBeforeInstall,
      ),
    enabled: enabled && Boolean(structureId),
    placeholderData: keepPreviousData,
  });

/**
 * A page of a structure's monitoring items, fetched only once the tab is opened. The previous page
 * stays on screen while the next loads.
 *
 * @param pageNumber zero-based
 */
export const useStructureMonitors = (
  structureId: string | undefined,
  enabled: boolean,
  view: MonitorView,
  pageNumber: number,
  pageSize: number,
  includeBeforeInstall: boolean,
) =>
  useQuery({
    queryKey: [
      STRUCTURE_QUERY_KEY,
      structureId,
      'monitors',
      view,
      pageNumber,
      pageSize,
      includeBeforeInstall,
    ],
    queryFn: () =>
      API.structureSearch.getStructureMonitors(
        structureId as string,
        view,
        pageNumber,
        pageSize,
        includeBeforeInstall,
      ),
    enabled: enabled && Boolean(structureId),
    placeholderData: keepPreviousData,
  });

/**
 * Adds a monitoring item, then refreshes the structure's monitoring table. The fields the server
 * refused come back as `fieldErrors`, keyed as the request is.
 */
export const useCreateStructureMonitor = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: MonitorCreateRequest) =>
      API.structureSearch.createStructureMonitor(structureId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId, 'monitors'] }),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * Saves an edit to one monitoring item, then refreshes the structure's monitoring table. The
 * fields the server refused come back as `fieldErrors`, keyed as the request is.
 */
export const useUpdateStructureMonitor = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ monitorId, request }: { monitorId: string; request: MonitorUpdateRequest }) =>
      API.structureSearch.updateStructureMonitor(structureId, monitorId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId, 'monitors'] }),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * Deletes one repair, then refreshes the structure's repairs table — every page and view of it,
 * as the repair may be on any of them.
 */
export const useDeleteStructureRepair = (
  structureId: string,
): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (repairId: string) =>
      API.structureSearch.deleteStructureRepair(structureId, repairId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId, 'repairs'] }),
  });
};

/**
 * Deletes one monitoring item, then refreshes the structure's monitoring table — every page and
 * view of it, as the item may be on any of them.
 */
export const useDeleteStructureMonitor = (
  structureId: string,
): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (monitorId: string) =>
      API.structureSearch.deleteStructureMonitor(structureId, monitorId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId, 'monitors'] }),
  });
};

/** A structure's documents and photos, fetched only once their tab is opened (`enabled`). */
export const useStructureDocuments = (structureId: string | undefined, enabled: boolean) =>
  useQuery({
    queryKey: [STRUCTURE_QUERY_KEY, structureId, 'documents'],
    queryFn: () => API.structureSearch.getDocuments(structureId as string),
    enabled: enabled && Boolean(structureId),
  });

/**
 * Runs a structure search, or nothing until one has been submitted.
 *
 * <p>`criteria` must be the <b>submitted</b> criteria, not the form's live state — it is part of the
 * key, so binding it to the form would search on every keystroke. See `useSiteSearch`, which this
 * mirrors.
 *
 * @param pageNumber zero-based, as the backend expects
 * @param sort       the header the user sorted by, or `null` for legacy's order
 */
export const useStructureSearch = (
  criteria: StructureSearchCriteria | null,
  pageNumber: number,
  pageSize: number,
  sort: StructureSort | null = null,
): UseQueryResult<PagedResponse<StructureSearchResult>> =>
  useQuery({
    queryKey: [STRUCTURE_SEARCH_QUERY_KEY, criteria, pageNumber, pageSize, sort],
    queryFn: () =>
      API.structureSearch.searchStructures(
        criteria as StructureSearchCriteria,
        pageNumber,
        pageSize,
        sort,
      ),
    enabled: criteria !== null,
    // The current page stays on screen while the next loads, rather than flashing "No structures
    // found." between pages.
    placeholderData: keepPreviousData,
  });

/**
 * Archives structures, then re-runs every structure search.
 *
 * <p>Refetched rather than edited in place: an archived structure leaves the results unless
 * archived ones were asked for, which changes the total and the paging.
 */
export const useArchiveStructures = (): UseMutationResult<
  StructureArchiveResponse,
  Error,
  string[]
> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (structureIds: string[]) => API.structureSearch.archiveStructures(structureIds),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [STRUCTURE_SEARCH_QUERY_KEY] }),
  });
};

/** What a run of deletes did: which went, and which the server refused and why. */
export type DeleteOutcome = {
  deleted: DeleteTarget[];
  failed: (DeleteTarget & { reason: string })[];
};

/**
 * Deletes one structure and records what happened in {@link outcome}. Never rejects: a refusal is
 * an outcome like any other, so the deletes after it still run.
 */
const deleteOne = (target: DeleteTarget, outcome: DeleteOutcome): Promise<void> =>
  API.structureSearch.deleteStructure(target.id).then(
    () => {
      outcome.deleted.push(target);
    },
    (error: unknown) => {
      outcome.failed.push({
        ...target,
        reason: apiErrorMessage(error, `${target.name} could not be deleted.`),
      });
    },
  );

/**
 * Deletes structures one request at a time, then re-runs every structure search.
 *
 * <p>One request per structure, each its own transaction on the server, as legacy deletes them: a
 * refusal or a failure on one does not stop the rest, and is reported with the server's own
 * sentence — "Structure B100 has inspections and cannot be deleted." The mutation itself never
 * fails; what happened to each structure is in the outcome.
 *
 * <p>In sequence rather than all at once, so a large selection does not land on the server as a
 * burst.
 */
export const useDeleteStructures = (): UseMutationResult<DeleteOutcome, Error, DeleteTarget[]> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (targets: DeleteTarget[]) => {
      const outcome: DeleteOutcome = { deleted: [], failed: [] };
      // Each delete chained onto the one before, so the next starts only when the last has
      // settled — one at a time, without an `await` in a loop.
      return targets
        .reduce<Promise<void>>(
          (previous, target) => previous.then(() => deleteOne(target, outcome)),
          Promise.resolve(),
        )
        .then(() => outcome);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: [STRUCTURE_SEARCH_QUERY_KEY] }),
  });
};

/** What Update Repair Responsibility sends. */
export type RepairResponsibilityChange = {
  structureIds: string[];
  clientNumber: string;
  clientLocationCode: string;
};

/**
 * Sets the designated maintainer of the ticked structures' sites, then re-runs every structure
 * search — the Maintainer column has changed.
 */
export const useUpdateRepairResponsibility = (): UseMutationResult<
  RepairResponsibilityResponse,
  Error,
  RepairResponsibilityChange
> => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ structureIds, clientNumber, clientLocationCode }: RepairResponsibilityChange) =>
      API.structureSearch.updateRepairResponsibility(
        structureIds,
        clientNumber,
        clientLocationCode,
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [STRUCTURE_SEARCH_QUERY_KEY] }),
  });
};
