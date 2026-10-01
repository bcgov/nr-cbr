import { keepPreviousData, useQuery } from '@tanstack/react-query';

import type {
  PagedResponse,
  StructureSearchCriteria,
  StructureSearchResult,
  StructureSort,
} from '@/pages/StructureSearch/types';
import type { UseQueryResult } from '@tanstack/react-query';

import API from '@/services/APIs';

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
