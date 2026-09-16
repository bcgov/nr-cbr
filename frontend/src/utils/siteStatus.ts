import type { StatusTagType } from '@/types/configuration';

/**
 * Carbon `Tag` colours for a crossing site's status, matching how nr-frep colours a checklist
 * status.
 *
 * <p><b>Keyed on the code, never the description.</b> The description lives in
 * `CROSSING_SITE_STATUS_CODE` and the business can reword it there at any time — "Deactivated"
 * becoming "Closed" would silently grey out a colour that had been meaningful. The code is the
 * stable half.
 *
 * <p><b>The codes below are the ones the legacy source evidences, and there are eight rows in the
 * table.</b> Two are unaccounted for: nothing in the CBR source or the schema repo names them, and
 * the code table holds no data in either. They will render grey, which is the point of the default —
 * an unmapped status is still a readable pill, just an uncoloured one, rather than a crash or a
 * wrong colour. Confirm the full set against TEST and fill them in:
 *
 * ```sql
 * SELECT c.crossing_site_status_code, c.description, x.display_order
 *   FROM THE.CROSSING_SITE_STATUS_CODE c
 *   JOIN THE.CROSSING_SITE_STATUS_XREF x
 *     ON x.crossing_site_status_code = c.crossing_site_status_code
 *  ORDER BY x.display_order;
 * ```
 */
const SITE_STATUS_TAG_TYPES: Record<string, StatusTagType> = {
  /** Active — the crossing is in service. */
  ACT: 'green',
  /** Barricaded. Still a real crossing, but closed to traffic, so it earns the eye-catching colour. */
  BAR: 'red',
  /** Proposed. Legacy refuses this status once the site has structures, so it means "not yet built". */
  PP: 'blue',
  /** Deactivated (closed) — legacy's own wording in `errors.site.deactivated`. */
  DAC: 'cool-gray',
  /** Archived. */
  ARC: 'cool-gray',
  /** Handed to LRM. Grouped with ARC everywhere legacy tests it — out of CBR's hands either way. */
  LRM: 'cool-gray',
};

/** The colour for a status code; anything unrecognised is grey rather than guessed at. */
export const siteStatusTagType = (code: string | null | undefined): StatusTagType =>
  SITE_STATUS_TAG_TYPES[(code ?? '').trim().toUpperCase()] ?? 'gray';

/**
 * What the pill reads.
 *
 * <p>The decoded description, falling back to the raw code. A site whose status is not in the code
 * table — which the left join allows — shows the code rather than an empty pill, because a blank
 * cell reads as "no status" when the truth is "a status nothing can decode".
 */
export const siteStatusLabel = (
  code: string | null | undefined,
  description: string | null | undefined,
): string => description?.trim() || code?.trim() || '';
