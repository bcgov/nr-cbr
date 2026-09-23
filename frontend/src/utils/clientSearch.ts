import type { ClientSuggestion } from '@/types/client';

/**
 * Shorter than this matches too much to be a suggestion.
 *
 * <p>The backend applies the same floor — it is the authority, and this is not the only caller —
 * but applying it here as well means two characters never leave the browser at all.
 *
 * <p>Digits are exempt, both here and there: `5` is an exact key that asks for client `00000005`,
 * where two letters would match a large share of the table.
 */
export const MIN_CLIENT_TERM_LENGTH = 3;

const DIGITS = /^\d+$/;

/** True when a term is worth sending: an exact client number, or long enough to narrow. */
export const isSearchableTerm = (term: string): boolean => {
  const trimmed = term.trim();
  if (trimmed === '') return false;
  return DIGITS.test(trimmed) || trimmed.length >= MIN_CLIENT_TERM_LENGTH;
};

/**
 * The label for one suggestion: `CANFOR CORPORATION · Northern Division · Prince George · 00001012-01`.
 *
 * <p>Composed here rather than by the backend because it is a presentation decision, which is where
 * nr-frep's equivalent also lives. Absent parts are dropped rather than rendered empty — most
 * clients have no division name — so a lone office reads as
 * `WEST FRASER MILLS LTD · Quesnel · 00010120-00` instead of carrying a stranded separator.
 *
 * <p>The number-and-location-code pair is last and always present. It is the thing the search
 * actually filters on, and it is what makes two otherwise identical rows distinguishable when a
 * client has two locations in the same city.
 */
export const clientLabel = (client: ClientSuggestion): string => {
  // Guarded rather than interpolated straight in: the combo box renders its *selected* item through
  // this too, and that item is a stand-in carrying only the label — so an unguarded template would
  // append a literal "undefined-undefined" to every picked client.
  const number = client.clientNumber?.trim() ?? '';
  const locnCode = client.clientLocnCode?.trim() ?? '';
  const pair = number && locnCode ? `${number}-${locnCode}` : number;

  return [client.clientName?.trim(), client.clientLocnName?.trim(), client.city?.trim(), pair]
    .filter(Boolean)
    .join(' · ');
};

/**
 * One of a client's locations, as the Location filter lists it.
 *
 * <p>`01 · PRINCE GEORGE · PRINCE GEORGE` — the code first, because that is what the column stores
 * and what a user who knows it will look for. The name and city follow because a bare two-digit
 * code identifies nothing to anyone who does not already know it.
 *
 * <p>Falls back to the code alone when the location carries neither, which the client table
 * permits.
 */
export const locationLabel = (location: ClientSuggestion): string =>
  [location.clientLocnCode?.trim(), location.clientLocnName?.trim(), location.city?.trim()]
    .filter(Boolean)
    .join(' · ');
