import type { InspectionSearchCriteria } from './types';

import { monthRangeErrors } from '@/utils/month';

export { MONTH_PATTERN, isMonth } from '@/utils/month';

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

  const months = monthRangeErrors(criteria.inspectionDateStart, criteria.inspectionDateEnd);
  if (months.start) {
    errors.inspectionDateStart = months.start;
  }
  if (months.end) {
    errors.inspectionDateEnd = months.end;
  }

  return errors;
};
