import { keepPreviousData, useQuery } from '@tanstack/react-query';

import useDebounce from '@/hooks/useDebounce';

import type { ClientScope } from '@/services/client.service';
import type { ClientSuggestion } from '@/types/client';
import type { UseQueryResult } from '@tanstack/react-query';

import { ONE_MINUTE } from '@/config/react-query/TimeUnits';
import API from '@/services/APIs';
import { isSearchableTerm } from '@/utils/clientSearch';

export const CLIENT_SEARCH_QUERY_KEY = 'client-search';

/**
 * Long enough that a typist is not firing a request per character, short enough that the list is
 * there by the time they stop and look at it.
 */
export const CLIENT_SEARCH_DEBOUNCE_MS = 300;

/**
 * Suggestions for the Designated Maintainer combo box.
 *
 * <p>Debounced, then cached by term. The cache is what makes backspacing feel instant: deleting a
 * character returns to a term already fetched, so a user narrowing and widening a search pays for
 * each distinct term once rather than once per visit to it.
 *
 * <p><b>Unmatchable terms never leave the browser.</b> `isSearchableTerm` gates the query, so a
 * cleared field or a two-letter fragment makes no request at all. The backend applies the same rule
 * — it is the authority and this is not its only caller — but the round trip is pure waste.
 *
 * <p>A failed lookup is left to the caller to swallow. There is no toast here on purpose: this runs
 * on a keystroke, and one unreachable request would otherwise raise an error for a word the user is
 * still halfway through typing.
 *
 * @param term the raw text in the field, debounced here rather than by the caller
 */
export const useClientSearch = (
  term: string,
  scope: ClientScope = 'MAINTAINERS',
): UseQueryResult<ClientSuggestion[]> => {
  const debounced = useDebounce(term.trim(), CLIENT_SEARCH_DEBOUNCE_MS);

  return useQuery({
    // The scope is part of the key: the two sets answer different questions, and a term cached
    // under one must never be served for the other.
    queryKey: [CLIENT_SEARCH_QUERY_KEY, scope, debounced],
    queryFn: () => API.client.searchClients(debounced, scope),
    enabled: isSearchableTerm(debounced),
    // The set of in-use maintainers moves only when sites are edited, so a minute of freshness
    // costs nothing and spares the repeated fetches a type-ahead would otherwise make.
    staleTime: ONE_MINUTE,
    // Keeps the previous suggestions visible while the next term loads, instead of emptying the
    // list and refilling it — which reads as "no matches" for the moment it is blank.
    placeholderData: keepPreviousData,
  });
};

/**
 * The locations of one client, for the Location filter beside it on Site Search.
 *
 * <p>Asks nothing until a client is chosen, so it never runs while the user is still typing a name
 * — unlike the suggestion lookup above, this one is answered once per selection.
 *
 * <p>Cached for the same minute and for the same reason: which locations maintain a site moves only
 * when sites are edited.
 */
export const useClientLocations = (clientNumber: string): UseQueryResult<ClientSuggestion[]> =>
  useQuery({
    queryKey: [CLIENT_SEARCH_QUERY_KEY, 'locations', clientNumber.trim()],
    queryFn: () => API.client.clientLocations(clientNumber.trim()),
    enabled: clientNumber.trim() !== '',
    staleTime: ONE_MINUTE,
  });
