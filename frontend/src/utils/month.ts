/**
 * A month as the inspection-date criteria take it — `yyyy/mm` — for Inspection Search and the
 * Inspections Conducted reports alike.
 *
 * <p>Legacy parses both bounds with a non-lenient `SimpleDateFormat("yyyy/MM")`, which is why this
 * is a month and not a day. A one-digit month is accepted because `SimpleDateFormat` accepts it;
 * `2026/1` and `2026/01` are the same month to legacy and are the same month here.
 */
export const MONTH_PATTERN = /^\d{4}\/(0?[1-9]|1[0-2])$/;

/** Unset, or a month in `yyyy/mm`. */
export const isMonth = (value: string): boolean => value === '' || MONTH_PATTERN.test(value);

/** `2026/1` and `2026/01` compare equal — pad before comparing, never compare the raw strings. */
export const monthKey = (value: string): string => {
  const [year, month] = value.split('/');
  return `${year}${month.padStart(2, '0')}`;
};

export const INVALID_MONTH = 'Enter a month as yyyy/mm, e.g. 2026/01';
export const REVERSED_MONTHS = 'The end month cannot be before the start month';

/**
 * The messages for a "from – to" pair of months: each box judged on its own, then — when both are
 * months — the end checked against the start. The reversed-range check is ours: legacy turns
 * `2026/06` to `2026/01` into a `BETWEEN` that cannot match, indistinguishable to the user from a
 * range with nothing in it. It goes on the "To" box, the one most likely mistyped.
 */
export const monthRangeErrors = (start: string, end: string): { start?: string; end?: string } => {
  const errors: { start?: string; end?: string } = {};
  if (!isMonth(start)) {
    errors.start = INVALID_MONTH;
  }
  if (!isMonth(end)) {
    errors.end = INVALID_MONTH;
  }
  if (
    start !== '' &&
    end !== '' &&
    !errors.start &&
    !errors.end &&
    monthKey(start) > monthKey(end)
  ) {
    errors.end = REVERSED_MONTHS;
  }
  return errors;
};

/**
 * The calendar's date format: a month, because the criterion is a month.
 *
 * <p>flatpickr formats `m` zero-padded, so anything the calendar writes comes back as `2026/01` —
 * already the canonical form the validation compares and the backend parses.
 */
export const MONTH_FORMAT = 'Y/m';

/**
 * `yyyy/mm` (or `yyyy/m`) to the first of that month, and anything else to `false`.
 *
 * <p>flatpickr's own parser fills the fields a format does not mention from *today*, so `Y/m`
 * reading "2026/02" on the 31st of a month lands on 3 March and the box redraws itself as
 * `2026/03`. Anchoring the day to the 1st is what makes a month-granular format safe.
 *
 * <p>`false` is flatpickr's signal for "not a date": it leaves the box empty rather than inventing
 * one — the behaviour wanted for a half-typed or misformatted month.
 */
export const parseMonth = (value: string): Date | false => {
  if (!MONTH_PATTERN.test(value)) {
    return false;
  }
  const [year, month] = value.split('/');
  return new Date(Number(year), Number(month) - 1, 1);
};

export const formatMonth = (date: Date): string =>
  `${date.getFullYear()}/${String(date.getMonth() + 1).padStart(2, '0')}`;
