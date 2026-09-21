import type { InspectionSearchCriteria } from './types';

/**
 * An inspection-date bound: unset, or a month in `yyyy/MM`.
 *
 * <p>Legacy parses both bounds with a non-lenient `SimpleDateFormat("yyyy/MM")`, which is why this
 * is a month and not a day — the boxes are eight characters wide and the label says `(yyyy/mm)`.
 * A one-digit month is accepted because `SimpleDateFormat` accepts it; `2026/1` and `2026/01` are
 * the same month to legacy and are the same month here.
 */
export const MONTH_PATTERN = /^\d{4}\/(0?[1-9]|1[0-2])$/;

export const isMonth = (value: string): boolean => value === '' || MONTH_PATTERN.test(value);

/** `2026/1` and `2026/01` compare equal — pad before comparing, never compare the raw strings. */
const monthKey = (value: string): string => {
  const [year, month] = value.split('/');
  return `${year}${month.padStart(2, '0')}`;
};

export type CriteriaErrors = Partial<Record<keyof InspectionSearchCriteria, string>>;

/**
 * Field-level messages for whatever is currently wrong.
 *
 * <p>One entry per offending box. Legacy raises a single page-level "Inspection Date must be in
 * format yyyy/MM" for the pair, which leaves the user to work out which of the two boxes it means.
 *
 * <p>The reversed-range check is ours. Legacy accepts `2026/06` to `2026/01` and turns it into a
 * `BETWEEN` that cannot match, so the screen reports no inspections rather than an impossible range
 * — indistinguishable, to the user, from a month with no inspections in it.
 */
export const criteriaErrors = (criteria: InspectionSearchCriteria): CriteriaErrors => {
  const errors: CriteriaErrors = {};

  if (!isMonth(criteria.inspectionDateStart)) {
    errors.inspectionDateStart = 'Enter a month as yyyy/mm, e.g. 2026/01';
  }
  if (!isMonth(criteria.inspectionDateEnd)) {
    errors.inspectionDateEnd = 'Enter a month as yyyy/mm, e.g. 2026/01';
  }

  const bothValid =
    criteria.inspectionDateStart !== '' &&
    criteria.inspectionDateEnd !== '' &&
    !errors.inspectionDateStart &&
    !errors.inspectionDateEnd;

  if (bothValid && monthKey(criteria.inspectionDateStart) > monthKey(criteria.inspectionDateEnd)) {
    // On the "To" box, because that is the one the user most likely mistyped — the range reads left
    // to right and the first box is usually the deliberate one.
    errors.inspectionDateEnd = 'The end month cannot be before the start month';
  }

  return errors;
};
