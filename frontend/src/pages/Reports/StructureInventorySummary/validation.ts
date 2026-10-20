import { checkFields, isKilometre, isYear, KILOMETRES_MESSAGE, YEAR_MESSAGE } from '../validation';

import type { StructureInventorySummaryCriteria } from './types';

/** An opening width bound: unset, or whole millimetres — legacy's `######`. */
const isOpeningWidth = (value: string): boolean => value === '' || /^\d{1,6}$/.test(value);

export type CriteriaErrors = Partial<Record<keyof StructureInventorySummaryCriteria, string>>;

/**
 * Field-level messages for whatever is wrong — one per offending box, each judged on its own.
 * Legacy's rules: kilometres are a number, years are four digits, an opening width is whole
 * millimetres. The wording matches Structure Search's for the boxes the two share.
 */
export const criteriaErrors = (criteria: StructureInventorySummaryCriteria): CriteriaErrors => {
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
  checkFields(
    criteria,
    ['openingWidthMin', 'openingWidthMax'],
    isOpeningWidth,
    'Opening width must be whole millimetres, e.g. 1200',
    errors,
  );
  return errors;
};
