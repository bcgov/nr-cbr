import { checkFields, isYear, YEAR_MESSAGE } from '../validation';

import type { ReplacementStructuresSummaryCriteria } from './types';

export type CriteriaErrors = Partial<Record<keyof ReplacementStructuresSummaryCriteria, string>>;

/** The form's rules: each year four digits, legacy's — as on the Structure Inventory reports. */
export const criteriaErrors = (criteria: ReplacementStructuresSummaryCriteria): CriteriaErrors => {
  const errors: CriteriaErrors = {};
  checkFields(
    criteria,
    [
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
