import { DateTime } from 'luxon';

/**
 * Formats an ISO `yyyy-MM-dd` date as `MMM d, yyyy` (e.g. `2002-12-01` → `Dec 1, 2002`). Uses Luxon
 * with a strict format so it only touches full ISO dates; blank input or anything that isn't a
 * `yyyy-MM-dd` date (e.g. a bare year) is returned unchanged. Parsing a date-only value keeps it in
 * the local zone, so the displayed day never shifts.
 */
export function formatShortDate(value: string | null | undefined): string {
  if (!value) return '';
  const date = DateTime.fromFormat(value.trim(), 'yyyy-MM-dd');
  return date.isValid ? date.toFormat('MMM d, yyyy') : value;
}

/**
 * Formats an ISO `yyyy-MM-ddTHH:mm:ss` timestamp as `MMM d, yyyy h:mm a` (e.g. `Dec 1, 2002 3:07 PM`).
 * A bare `yyyy-MM-dd` date is formatted as {@link formatShortDate} does; blank input is returned
 * empty and anything else unchanged. Read in the local zone, as the audit columns are written.
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '';
  const trimmed = value.trim();
  const timestamp = DateTime.fromISO(trimmed);
  if (!timestamp.isValid) return value;
  return trimmed.includes('T')
    ? timestamp.toFormat('MMM d, yyyy h:mm a')
    : timestamp.toFormat('MMM d, yyyy');
}
