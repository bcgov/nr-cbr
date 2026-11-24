import { useQuery } from '@tanstack/react-query';

import useDebounce from '@/hooks/useDebounce';

import type { RoadSectionResponse } from '@/services/road.service';
import type { UseQueryResult } from '@tanstack/react-query';

import { THREE_HOURS } from '@/config/react-query/TimeUnits';
import API from '@/services/APIs';

export const ROAD_SECTION_QUERY_KEY = 'road-section';

/** Long enough that typing a ten-character file id is one request rather than ten. */
export const ROAD_SECTION_DEBOUNCE_MS = 300;

/**
 * The road section named by a Project File ID# and a Br., for the form's Forest Service Road field.
 *
 * <p>Replaces the {@code showSite.do?actionMapping=road} call `site.jsp` makes on every change of
 * either box — except that legacy re-submits the entire form to do it, through its `redisplay()`
 * handler, so the page reloads while the user is still filling it in.
 *
 * <p>Asks nothing until both halves are present: a road file alone names many sections, and they
 * are different roads.
 *
 * <p><b>A failed lookup is not an error.</b> A 404 is the ordinary answer to a pair half typed, so
 * the caller reads "no road" from `data` being absent and shows nothing. Retries are off for the
 * same reason: repeating a question already answered "no such road" wastes two round trips per
 * keystroke. A 503 means LRMOPS could not be asked — see `isRoadLookupUnavailable`.
 */
export const useRoadSection = (
  forestFileId: string,
  roadSectionId: string,
  /** False to ask nothing — a recreation site, whose Project File ID# is not a road. */
  active = true,
): UseQueryResult<RoadSectionResponse> => {
  const file = useDebounce(forestFileId.trim(), ROAD_SECTION_DEBOUNCE_MS);
  const section = useDebounce(roadSectionId.trim(), ROAD_SECTION_DEBOUNCE_MS);

  return useQuery({
    queryKey: [ROAD_SECTION_QUERY_KEY, file, section],
    queryFn: () => API.road.getRoadSection(file, section),
    enabled: active && file !== '' && section !== '',
    // A road section is renamed about as often as a code table changes, so a fresh read per
    // keystroke would be asking a question whose answer cannot have moved.
    staleTime: THREE_HOURS,
    retry: false,
  });
};
