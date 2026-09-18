import type { StatusTagType } from '@/types/configuration';

/**
 * Carbon `Tag` colours for an inspection's report status, the counterpart to `siteStatus` for the
 * Inspection Search results.
 *
 * <p><b>Keyed on the code, never the description</b> — the description lives in a code table the
 * business can reword at any time, and the code is the stable half. Same rule as `siteStatus`, and
 * for the same reason.
 *
 * <p>The six codes are the ones the legacy source evidences
 * (`cbr-workflows.local.md` §"The statuses"): `OFL`, `PRO` and `SUB` are constants in
 * `Inspection.java:53-55`; `RVD`, `ACC` and `REJ` appear only as string literals in
 * `InspectionAction`. Anything else renders grey rather than guessed at.
 */
const INSPECTION_STATUS_TAG_TYPES: Record<string, StatusTagType> = {
  /**
   * Offline — checked out to a field device and not on the server's copy of the record. Purple
   * rather than a progress colour: the inspection is somewhere else, which is why the results table
   * neither links it nor lets anyone but a destructive role touch it.
   */
  OFL: 'purple',
  /** In progress — uploaded but incomplete, or set by hand. */
  PRO: 'blue',
  /** Submitted — complete and waiting on a reviewer. */
  SUB: 'teal',
  /** Reviewed — the terminal state, reachable only with `/approveInspection`. */
  RVD: 'green',
  /**
   * Accepted — the expired predecessor of `RVD`. Coloured the same because it means the same thing:
   * saving an `ACC` inspection silently rewrites it to `RVD`, and a downstream LRM view still reads
   * the two as one (`cbr-workflows.local.md` §"`ACC` is dead"). It survives only on rows nobody has
   * saved since, so it is read-only history rather than a status anything sets.
   */
  ACC: 'green',
  /** Rejected — referenced by the status rules but never set anywhere in the legacy Java. */
  REJ: 'red',
};

/** The colour for a status code; anything unrecognised is grey rather than guessed at. */
export const inspectionStatusTagType = (code: string | null | undefined): StatusTagType =>
  INSPECTION_STATUS_TAG_TYPES[(code ?? '').trim().toUpperCase()] ?? 'gray';

/**
 * What the pill reads: the decoded description, falling back to the raw code, so a status nothing
 * can decode shows as the code rather than as an empty pill.
 */
export const inspectionStatusLabel = (
  code: string | null | undefined,
  description: string | null | undefined,
): string => description?.trim() || code?.trim() || '';

/** The offline status, which the results table treats differently in two places. */
export const OFFLINE_STATUS_CODE = 'OFL';

/**
 * True when an inspection is checked out to a field device.
 *
 * <p>Legacy tests `inspection.inspectionReportStatusCode == 'OFL'` twice in the results table: an
 * offline row is not a link, because there is nothing on the server to show, and it is the only row
 * that offers delete.
 */
export const isOffline = (code: string | null | undefined): boolean =>
  (code ?? '').trim().toUpperCase() === OFFLINE_STATUS_CODE;
