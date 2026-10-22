import type { InspectionsConductedCriteria } from './types';

import { monthRangeErrors } from '@/utils/month';

export type CriteriaErrors = Partial<Record<keyof InspectionsConductedCriteria, string>>;

/**
 * The form's rules: each Inspection Date bound a month, `yyyy/mm`, and the end not before the
 * start — as Inspection Search's. Legacy raised one page-level message for the pair.
 */
export const criteriaErrors = (criteria: InspectionsConductedCriteria): CriteriaErrors => {
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
