import { checkFields, isKilometre, isYear, KILOMETRES_MESSAGE, YEAR_MESSAGE } from '../validation';

import type { StructureInventoryDetailedCriteria } from './types';

export type CriteriaErrors = Partial<Record<keyof StructureInventoryDetailedCriteria, string>>;

/**
 * Field-level messages for whatever is wrong — one per offending box, each judged on its own.
 * Legacy's rules: kilometres are a number, years are four digits.
 */
export const criteriaErrors = (criteria: StructureInventoryDetailedCriteria): CriteriaErrors => {
  const errors: CriteriaErrors = {};
  checkFields(criteria, ['kiloStart', 'kiloEnd'], isKilometre, KILOMETRES_MESSAGE, errors);
  checkFields(
    criteria,
    [
      'yearBuiltStart',
      'yearBuiltEnd',
      'loadRestrictionYearStart',
      'loadRestrictionYearEnd',
      'replacementYearStart',
      'replacementYearEnd',
    ],
    isYear,
    YEAR_MESSAGE,
    errors,
  );
  return errors;
};
