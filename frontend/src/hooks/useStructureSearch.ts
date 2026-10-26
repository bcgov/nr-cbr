import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import useDebounce from '@/hooks/useDebounce';

import type {
  DocumentDetailsRequest,
  DocumentUpdateRequest,
} from '@/pages/StructureDetail/documentsResponse';
import type {
  CloseProximityInspectionRequest,
  InspectionScheduleRequest,
} from '@/pages/StructureDetail/inspectionsResponse';
import type {
  MonitorCreateRequest,
  MonitorUpdateRequest,
  MonitorView,
} from '@/pages/StructureDetail/monitorsResponse';
import type {
  RepairCreateRequest,
  RepairUpdateRequest,
  RepairView,
} from '@/pages/StructureDetail/repairsResponse';
import type { PierRequest, SpanRequest } from '@/pages/StructureDetail/spansAndPiersResponse';
import type {
  CommentRequest,
  BridgeDetailsRequest,
  CulvertDetailsRequest,
  LoadRatingDetailsRequest,
  LoadRatingRequest,
  ReplacementDetailsRequest,
  ReplacementRequest,
  StructureCreateRequest,
  StructureDetailsRequest,
  StructureInformationRequest,
} from '@/pages/StructureDetail/structureResponse';
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
import type { QueryClient, UseMutationResult, UseQueryResult } from '@tanstack/react-query';

import API from '@/services/APIs';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';

export const STRUCTURE_SEARCH_QUERY_KEY = 'structure-search';

/** Query key for one structure read by id — distinct from the search, which is keyed on criteria. */
export const STRUCTURE_QUERY_KEY = 'structure';

/**
 * Reads a structure's documents again after a save from an inspection's page, which may have
 * stored the inspection's audit record among them — every structure's when the save does not know
 * which.
 */
export const invalidateDocuments = (queryClient: QueryClient, structureId?: string) =>
  queryClient.invalidateQueries({
    predicate: ({ queryKey }) =>
      queryKey[0] === STRUCTURE_QUERY_KEY &&
      queryKey[2] === 'documents' &&
      (structureId === undefined || queryKey[1] === structureId),
  });

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
 * Records a completed close proximity inspection, then refreshes the Inspections tab's list. A
 * refused date comes back as `fieldErrors.completedDate`.
 */
export const useAddCloseProximityInspection = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: CloseProximityInspectionRequest) =>
      API.structureSearch.addCloseProximityInspection(structureId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: [STRUCTURE_QUERY_KEY, structureId, 'inspection-schedule'],
      }),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Adds a general comment — the Details tab's. What the server refused comes back as `fieldErrors`. */
export const useAddStructureComment = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: CommentRequest) =>
      API.structureSearch.addStructureComment(structureId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId], exact: true }),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * Adds a planned-inspection comment, then refreshes the Inspections tab's comments. The text the
 * server refused comes back as `fieldErrors.comment`.
 */
export const useAddPlannedInspectionComment = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: CommentRequest) =>
      API.structureSearch.addPlannedInspectionComment(structureId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: [STRUCTURE_QUERY_KEY, structureId, 'inspection-schedule'],
      }),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * Changes a comment's text, then refreshes both lists it may be in — the Inspections tab's planned
 * ones and the Details tab's general ones (the structure itself). The text the server refused comes
 * back as `fieldErrors.comment`.
 */
export const useUpdateStructureComment = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ commentId, request }: { commentId: string; request: CommentRequest }) =>
      API.structureSearch.updateStructureComment(structureId, commentId, request),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({
          queryKey: [STRUCTURE_QUERY_KEY, structureId, 'inspection-schedule'],
        }),
        queryClient.invalidateQueries({
          queryKey: [STRUCTURE_QUERY_KEY, structureId],
          exact: true,
        }),
      ]),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * Saves the structure's inspection schedule, then refreshes it. The fields the server refused come
 * back as `fieldErrors`, keyed as the request is.
 */
export const useUpdateInspectionSchedule = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: InspectionScheduleRequest) =>
      API.structureSearch.updateInspectionSchedule(structureId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: [STRUCTURE_QUERY_KEY, structureId, 'inspection-schedule'],
      }),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

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
  /** On an inspection's page: the inspection, for the `CURRENT` view. */
  inspectionId?: string,
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
      inspectionId,
    ],
    queryFn: () =>
      inspectionId === undefined
        ? API.structureSearch.getStructureRepairs(
            structureId as string,
            view,
            pageNumber,
            pageSize,
            includeBeforeInstall,
          )
        : API.structureSearch.getStructureRepairs(
            structureId as string,
            view,
            pageNumber,
            pageSize,
            includeBeforeInstall,
            inspectionId,
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
  /** On an inspection's page: the inspection, for the `CURRENT` view. */
  inspectionId?: string,
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
      inspectionId,
    ],
    queryFn: () =>
      inspectionId === undefined
        ? API.structureSearch.getStructureMonitors(
            structureId as string,
            view,
            pageNumber,
            pageSize,
            includeBeforeInstall,
          )
        : API.structureSearch.getStructureMonitors(
            structureId as string,
            view,
            pageNumber,
            pageSize,
            includeBeforeInstall,
            inspectionId,
          ),
    enabled: enabled && Boolean(structureId),
    placeholderData: keepPreviousData,
  });

/**
 * Saves the Information tab's Edit, then refreshes the structure — its title and header. What the
 * server refused comes back as `fieldErrors`.
 */
export const useUpdateStructureInformation = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: StructureInformationRequest) =>
      API.structureSearch.updateStructureInformation(structureId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId], exact: true }),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * Saves the Details tab's Structure card, then refreshes everything the page shows of the
 * structure: Year Superstructure Installed decides what several tabs hide as from before it went
 * in. What the server refused comes back as `fieldErrors`.
 */
export const useUpdateStructureDetails = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: StructureDetailsRequest) =>
      API.structureSearch.updateStructureDetails(structureId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId] }),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * Saves the Details tab's Bridge card, then refreshes the structure: Type/Class decides which tabs
 * the page shows. What the server refused comes back as `fieldErrors`.
 */
export const useUpdateBridgeDetails = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: BridgeDetailsRequest) =>
      API.structureSearch.updateBridgeDetails(structureId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId] }),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** After a load rating change: the structure, whose Details tab holds them. */
const refreshStructure = (queryClient: QueryClient, structureId: string) =>
  queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId], exact: true });

/** Saves the fields of Load Rating Details. What the server refused comes back as `fieldErrors`. */
export const useUpdateLoadRatingDetails = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: LoadRatingDetailsRequest) =>
      API.structureSearch.updateLoadRatingDetails(structureId, request),
    onSuccess: () => refreshStructure(queryClient, structureId),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Adds a manual load rating. What the server refused comes back as `fieldErrors`. */
export const useCreateLoadRating = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: LoadRatingRequest) =>
      API.structureSearch.createLoadRating(structureId, request),
    onSuccess: () => refreshStructure(queryClient, structureId),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Changes a manual load rating. What the server refused comes back as `fieldErrors`. */
export const useUpdateLoadRating = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ loadRatingId, request }: { loadRatingId: string; request: LoadRatingRequest }) =>
      API.structureSearch.updateLoadRating(structureId, loadRatingId, request),
    onSuccess: () => refreshStructure(queryClient, structureId),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Deletes a manual load rating. */
export const useDeleteLoadRating = (
  structureId: string,
): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (loadRatingId: string) =>
      API.structureSearch.deleteLoadRating(structureId, loadRatingId),
    onSuccess: () => refreshStructure(queryClient, structureId),
  });
};

/** Records that this structure replaced another. What the server refused comes back as `fieldErrors`. */
export const useCreateReplacement = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: ReplacementRequest) =>
      API.structureSearch.createReplacement(structureId, request),
    onSuccess: () => refreshStructure(queryClient, structureId),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Deletes that this structure replaced another, given that one's id. */
export const useDeleteReplacement = (
  structureId: string,
): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (replacedId: string) =>
      API.structureSearch.deleteReplacement(structureId, replacedId),
    onSuccess: () => refreshStructure(queryClient, structureId),
  });
};

/** Saves Replacement Details. What the server refused comes back as `fieldErrors`. */
export const useUpdateReplacementDetails = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: ReplacementDetailsRequest) =>
      API.structureSearch.updateReplacementDetails(structureId, request),
    onSuccess: () => refreshStructure(queryClient, structureId),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Saves the Details tab's Culvert card. What the server refused comes back as `fieldErrors`. */
export const useUpdateCulvertDetails = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: CulvertDetailsRequest) =>
      API.structureSearch.updateCulvertDetails(structureId, request),
    onSuccess: () => refreshStructure(queryClient, structureId),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Long enough that typing a site number is one request rather than one a keystroke. */
const SITE_PREVIEW_DEBOUNCE_MS = 400;

/**
 * The site a structure would move to, as the Information tab's Edit shows it under Site # — legacy
 * refreshed its header the same way as the number was typed. Nothing is asked while the box is
 * empty or holds the structure's own site.
 */
export const useSitePreview = (siteId: string, currentSiteId: string | null) => {
  const debounced = useDebounce(siteId.trim(), SITE_PREVIEW_DEBOUNCE_MS);
  const asked = debounced !== '' && debounced !== (currentSiteId ?? '');
  const query = useQuery({
    queryKey: [STRUCTURE_QUERY_KEY, 'site-preview', debounced],
    queryFn: () => API.structureSearch.previewSite(debounced),
    enabled: asked,
    staleTime: 30_000,
    retry: false,
  });
  // Settled only once the typing has stopped on the number asked about.
  const settled = asked && debounced === siteId.trim();
  return { preview: settled ? query.data : undefined, loading: asked && query.isFetching };
};

/** Adds a structure. What the server refused comes back as `fieldErrors`, keyed by field. */
export const useCreateStructure = () => {
  const mutation = useMutation({
    mutationFn: (request: StructureCreateRequest) => API.structureSearch.createStructure(request),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** The fewest characters the Structure # type-ahead searches on — the backend's own minimum. */
export const STRUCTURE_NAME_MIN = 2;

/**
 * Structures whose Structure # starts with what is typed — Replacement Details' type-ahead.
 * Debounced, cached by term, and the last list kept while the next loads, as the client combo.
 */
export const useStructureNameSuggestions = (term: string) => {
  const debounced = useDebounce(term.trim(), 300);
  return useQuery({
    queryKey: [STRUCTURE_QUERY_KEY, 'name-suggestions', debounced],
    queryFn: () => API.structureSearch.suggestStructureNames(debounced),
    enabled: debounced.length >= STRUCTURE_NAME_MIN,
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
};

/**
 * After a span or pier change: the tab's lists, and the structure itself, whose Details tab counts
 * the spans. Only the structure's own query, not every tab's under it.
 */
const refreshSpansAndPiers = (queryClient: QueryClient, structureId: string) =>
  Promise.all([
    queryClient.invalidateQueries({
      queryKey: [STRUCTURE_QUERY_KEY, structureId, 'spans-and-piers'],
    }),
    queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId], exact: true }),
  ]);

/** Adds a span. What the server refused comes back as `fieldErrors`. */
export const useCreateSpan = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: SpanRequest) => API.structureSearch.createSpan(structureId, request),
    onSuccess: () => refreshSpansAndPiers(queryClient, structureId),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Changes a span. What the server refused comes back as `fieldErrors`. */
export const useUpdateSpan = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ spanId, request }: { spanId: string; request: SpanRequest }) =>
      API.structureSearch.updateSpan(structureId, spanId, request),
    onSuccess: () => refreshSpansAndPiers(queryClient, structureId),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Deletes a span. A refusal is a 409 whose detail says why. */
export const useDeleteSpan = (structureId: string): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (spanId: string) => API.structureSearch.deleteSpan(structureId, spanId),
    onSuccess: () => refreshSpansAndPiers(queryClient, structureId),
  });
};

/** Adds a pier. What the server refused comes back as `fieldErrors`. */
export const useCreatePier = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (request: PierRequest) => API.structureSearch.createPier(structureId, request),
    onSuccess: () => refreshSpansAndPiers(queryClient, structureId),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Changes a pier. What the server refused comes back as `fieldErrors`. */
export const useUpdatePier = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ pierId, request }: { pierId: string; request: PierRequest }) =>
      API.structureSearch.updatePier(structureId, pierId, request),
    onSuccess: () => refreshSpansAndPiers(queryClient, structureId),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Deletes a pier. A refusal is a 409 whose detail says why. */
export const useDeletePier = (structureId: string): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (pierId: string) => API.structureSearch.deletePier(structureId, pierId),
    onSuccess: () => refreshSpansAndPiers(queryClient, structureId),
  });
};

/**
 * Adds a monitoring item, then refreshes the structure's monitoring table. The fields the server
 * refused come back as `fieldErrors`, keyed as the request is.
 */
export const useCreateStructureMonitor = (structureId: string, inspectionId?: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    // From an inspection's page, raised by the inspection — its own endpoint.
    mutationFn: (request: MonitorCreateRequest) =>
      inspectionId === undefined
        ? API.structureSearch.createStructureMonitor(structureId, request)
        : API.inspectionSearch.addInspectionMonitor(inspectionId, request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: [STRUCTURE_QUERY_KEY, structureId, 'monitors'],
      });
      // From an inspection's page, a save of the inspection: it may have made an audit record.
      if (inspectionId !== undefined) await invalidateDocuments(queryClient, structureId);
    },
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * Saves an edit to one monitoring item, then refreshes the structure's monitoring table. The
 * fields the server refused come back as `fieldErrors`, keyed as the request is.
 */
export const useUpdateStructureMonitor = (structureId: string, inspectionId?: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ monitorId, request }: { monitorId: string; request: MonitorUpdateRequest }) =>
      inspectionId === undefined
        ? API.structureSearch.updateStructureMonitor(structureId, monitorId, request)
        : API.inspectionSearch.updateInspectionMonitor(inspectionId, monitorId, request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: [STRUCTURE_QUERY_KEY, structureId, 'monitors'],
      });
      // From an inspection's page, a save of the inspection: it may have made an audit record.
      if (inspectionId !== undefined) await invalidateDocuments(queryClient, structureId);
    },
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * The repair types that apply to a structure, each with its group — the Repair Item dialog's type
 * list. Fetched once per structure while the dialog is open; the types change with the structure's
 * type, which the dialog does not edit.
 */
export const useStructureRepairTypes = (structureId: string) =>
  useQuery({
    queryKey: [STRUCTURE_QUERY_KEY, structureId, 'repair-types'],
    queryFn: () => API.structureSearch.getStructureRepairTypes(structureId),
  });

/**
 * Adds a repair, then refreshes the structure's repairs table. The fields the server refused come
 * back as `fieldErrors`, keyed as the request is.
 */
export const useCreateStructureRepair = (structureId: string, inspectionId?: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    // From an inspection's page, raised by the inspection — its own endpoint.
    mutationFn: (request: RepairCreateRequest) =>
      inspectionId === undefined
        ? API.structureSearch.createStructureRepair(structureId, request)
        : API.inspectionSearch.addInspectionRepair(inspectionId, request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: [STRUCTURE_QUERY_KEY, structureId, 'repairs'],
      });
      // From an inspection's page, a save of the inspection: it may have made an audit record.
      if (inspectionId !== undefined) await invalidateDocuments(queryClient, structureId);
    },
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * Saves an edit to one repair, then refreshes the structure's repairs table. The fields the server
 * refused come back as `fieldErrors`, keyed as the request is.
 */
export const useUpdateStructureRepair = (structureId: string, inspectionId?: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ repairId, request }: { repairId: string; request: RepairUpdateRequest }) =>
      inspectionId === undefined
        ? API.structureSearch.updateStructureRepair(structureId, repairId, request)
        : API.inspectionSearch.updateInspectionRepair(inspectionId, repairId, request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: [STRUCTURE_QUERY_KEY, structureId, 'repairs'],
      });
      // From an inspection's page, a save of the inspection: it may have made an audit record.
      if (inspectionId !== undefined) await invalidateDocuments(queryClient, structureId);
    },
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/**
 * Deletes one repair, then refreshes the structure's repairs table — every page and view of it,
 * as the repair may be on any of them.
 */
export const useDeleteStructureRepair = (
  structureId: string,
  inspectionId?: string,
): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (repairId: string) =>
      inspectionId === undefined
        ? API.structureSearch.deleteStructureRepair(structureId, repairId)
        : API.inspectionSearch.deleteInspectionRepair(inspectionId, repairId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: [STRUCTURE_QUERY_KEY, structureId, 'repairs'],
      });
      // From an inspection's page, a save of the inspection: it may have made an audit record.
      if (inspectionId !== undefined) await invalidateDocuments(queryClient, structureId);
    },
  });
};

/**
 * Deletes one monitoring item, then refreshes the structure's monitoring table — every page and
 * view of it, as the item may be on any of them.
 */
export const useDeleteStructureMonitor = (
  structureId: string,
  inspectionId?: string,
): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (monitorId: string) =>
      inspectionId === undefined
        ? API.structureSearch.deleteStructureMonitor(structureId, monitorId)
        : API.inspectionSearch.deleteInspectionMonitor(inspectionId, monitorId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: [STRUCTURE_QUERY_KEY, structureId, 'monitors'],
      });
      // From an inspection's page, a save of the inspection: it may have made an audit record.
      if (inspectionId !== undefined) await invalidateDocuments(queryClient, structureId);
    },
  });
};

/** A structure's documents and photos, fetched only once their tab is opened (`enabled`). */
export const useStructureDocuments = (structureId: string | undefined, enabled: boolean) =>
  useQuery({
    queryKey: [STRUCTURE_QUERY_KEY, structureId, 'documents'],
    queryFn: () => API.structureSearch.getDocuments(structureId as string),
    enabled: enabled && Boolean(structureId),
  });

/** One file to add, under the caller's key for it. */
export type DocumentUpload = { key: number; file: File; details: DocumentDetailsRequest };

/** What became of each file: the keys stored, and why each of the rest was not. */
export type DocumentUploadOutcome = {
  added: number[];
  failed: { key: number; error: unknown }[];
};

/**
 * Adds files to a structure, one request a file and one request at a time, then refreshes its
 * documents. One file a request keeps each under the frontend WAF's body limit
 * (`frontend/coraza.conf`); one at a time keeps the WAF and the backend holding a single upload.
 *
 * <p>Never rejects: a file the server refuses is reported in the outcome — its `fieldErrors` keyed
 * `file`, `attachmentTypeCode`, `createdDate` or `description` — and the rest are still sent.
 * `onProgress` is told how many have been sent before each request.
 */
export const useAddDocuments = (structureId: string, inspectionId?: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      uploads,
      onProgress,
    }: {
      uploads: DocumentUpload[];
      onProgress?: (sent: number) => void;
    }): Promise<DocumentUploadOutcome> => {
      const outcome: DocumentUploadOutcome = { added: [], failed: [] };
      // From an inspection's page, the inspection's — its own endpoint.
      const send = (upload: DocumentUpload) =>
        inspectionId === undefined
          ? API.structureSearch.addDocument(structureId, upload.file, upload.details)
          : API.inspectionSearch.addInspectionDocument(inspectionId, upload.file, upload.details);
      // One at a time, each after the last: every request stays under the WAF's body limit, the
      // progress counts file by file, and a refused file does not stop the rest.
      await uploads.reduce(async (previous, upload, sent) => {
        await previous;
        onProgress?.(sent);
        try {
          await send(upload);
          outcome.added.push(upload.key);
        } catch (error) {
          outcome.failed.push({ key: upload.key, error });
        }
      }, Promise.resolve());
      return outcome;
    },
    onSuccess: ({ added }) =>
      added.length > 0
        ? queryClient.invalidateQueries({
            queryKey: [STRUCTURE_QUERY_KEY, structureId, 'documents'],
          })
        : undefined,
  });
};

/** Changes a document's type and description, then refreshes the structure's documents. */
export const useUpdateDocument = (structureId: string) => {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: ({ fileId, request }: { fileId: string; request: DocumentUpdateRequest }) =>
      API.structureSearch.updateDocument(structureId, fileId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId, 'documents'] }),
  });
  return { ...mutation, fieldErrors: apiFieldErrors(mutation.error) };
};

/** Deletes a document and its file, then refreshes the structure's documents. */
export const useDeleteDocument = (
  structureId: string,
  inspectionId?: string,
): UseMutationResult<void, Error, string> => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (fileId: string) =>
      inspectionId === undefined
        ? API.structureSearch.deleteDocument(structureId, fileId)
        : API.inspectionSearch.deleteInspectionDocument(inspectionId, fileId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [STRUCTURE_QUERY_KEY, structureId, 'documents'] }),
  });
};

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
