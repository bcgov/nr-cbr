import { dayRangeErrors } from '../validation';

import type { InspectionsDueCriteria } from './types';

export type CriteriaErrors = Partial<Record<keyof InspectionsDueCriteria, string>>;

/**
 * The form's rules: each Next Planned Inspection Date bound a day, `yyyy/mm/dd` as legacy's, and
 * the end not before the start. Legacy raised one page-level message for the pair. Dates set aside
 * by No Next Planned Inspection Date are cleared, so never judged.
 */
export const criteriaErrors = (criteria: InspectionsDueCriteria): CriteriaErrors => {
  const errors: CriteriaErrors = {};
  const days = dayRangeErrors(
    criteria.nextPlannedInspectionDateStart,
    criteria.nextPlannedInspectionDateEnd,
  );
  if (days.start) {
    errors.nextPlannedInspectionDateStart = days.start;
  }
  if (days.end) {
    errors.nextPlannedInspectionDateEnd = days.end;
  }
  return errors;
};
