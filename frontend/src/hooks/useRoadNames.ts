import { keepPreviousData, useQuery } from '@tanstack/react-query';

import useDebounce from '@/hooks/useDebounce';

import type { UseQueryResult } from '@tanstack/react-query';

import { ONE_MINUTE } from '@/config/react-query/TimeUnits';
import API from '@/services/APIs';

/** Fewer characters match too many names to be a useful list. The backend applies the same floor. */
export const MIN_ROAD_NAME_TERM_LENGTH = 3;

/** As the maintainer lookup: long enough not to ask per keystroke, short enough to keep up. */
export const ROAD_NAME_DEBOUNCE_MS = 300;

/**
 * Suggestions for the Forest Service Road pick list on the search screens, from LRMOPS.
 *
 * <p>Debounced, then cached by term, so backspacing to a term already asked costs nothing. A term
 * under {@link MIN_ROAD_NAME_TERM_LENGTH} characters never leaves the browser.
 *
 * @param term the raw text in the field, debounced here
 */
export const useRoadNames = (term: string): UseQueryResult<string[]> => {
  const debounced = useDebounce(term.trim(), ROAD_NAME_DEBOUNCE_MS);

  return useQuery({
    queryKey: ['road-names', debounced.toUpperCase()],
    queryFn: () => API.road.getRoadNames(debounced),
    enabled: debounced.length >= MIN_ROAD_NAME_TERM_LENGTH,
    // Road names change a few times a day across the province.
    staleTime: ONE_MINUTE,
    placeholderData: keepPreviousData,
  });
};
