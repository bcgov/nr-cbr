import type { StructureInventoryCommonCriteria } from './types';

import { isKilometre } from '@/pages/SiteSearch/validation';
import { isYear } from '@/pages/StructureSearch/validation';

/** The messages the reports' boxes share with Structure Search. */
export const KILOMETRES_MESSAGE = 'Kilometres must be a number, e.g. 12.5';
export const YEAR_MESSAGE = 'Enter a year as yyyy, e.g. 2030';

/**
 * Adds a message for each of these boxes whose value fails the test — each judged on its own, as
 * on the search pages.
 */
export const checkFields = <C extends object>(
  criteria: C,
  fields: readonly (keyof C)[],
  isValid: (value: string) => boolean,
  message: string,
  errors: Partial<Record<keyof C, string>>,
): void => {
  for (const field of fields) {
    if (!isValid(String(criteria[field]))) {
      errors[field] = message;
    }
  }
};

export { isKilometre, isYear };

/**
 * The rules on the boxes the Structure Inventory reports share: kilometres are a number, years
 * are four digits — legacy's.
 */
export const commonCriteriaErrors = <C extends StructureInventoryCommonCriteria>(
  criteria: C,
): Partial<Record<keyof C, string>> => {
  const errors: Partial<Record<keyof C, string>> = {};
  checkFields<C>(criteria, ['kiloStart', 'kiloEnd'], isKilometre, KILOMETRES_MESSAGE, errors);
  checkFields<C>(
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
