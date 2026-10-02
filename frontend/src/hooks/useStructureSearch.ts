import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

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
import { apiErrorMessage } from '@/utils/apiError';

export const STRUCTURE_SEARCH_QUERY_KEY = 'structure-search';

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
    mutationFn: async (targets: DeleteTarget[]) => {
      const outcome: DeleteOutcome = { deleted: [], failed: [] };
      for (const target of targets) {
        try {
          await API.structureSearch.deleteStructure(target.id);
          outcome.deleted.push(target);
        } catch (error) {
          outcome.failed.push({
            ...target,
            reason: apiErrorMessage(error, `${target.name} could not be deleted.`),
          });
        }
      }
      return outcome;
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
