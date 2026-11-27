import { dayRangeErrors, districtError } from '../validation';

import type { InspectionsDueCriteria } from './types';

export type CriteriaErrors = Partial<Record<keyof InspectionsDueCriteria, string>>;

/**
 * The form's rules: each Next Planned Inspection Date bound a day, `yyyy/mm/dd` as legacy's, and
 * the end not before the start. Legacy raised one page-level message for the pair. Dates set aside
 * by No Next Planned Inspection Date are cleared, so never judged. With `districtRequired` — the
 * Blank — an Area or one to eight Forest Districts, too
 * ({@link districtError}).
 */
export const criteriaErrors = (
  criteria: InspectionsDueCriteria,
  districtRequired = false,
): CriteriaErrors => {
  const errors: CriteriaErrors = {};
  const district = districtRequired
    ? districtError(criteria.areaOrgUnit, criteria.orgUnit)
    : undefined;
  if (district) {
    errors.orgUnit = district;
  }
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
