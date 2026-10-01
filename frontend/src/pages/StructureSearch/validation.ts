import type { StructureSearchCriteria } from './types';

import { isKilometre } from '@/pages/SiteSearch/validation';

/**
 * A year bound: unset, or four digits. Paired with `StructureSearchCriteria.YEAR` in the backend,
 * which enforces it; this copy tells the user before a 400 does.
 */
export const YEAR_PATTERN = /^\d{4}$/;

export const isYear = (value: string): boolean => value === '' || YEAR_PATTERN.test(value);

const KILOMETRE_FIELDS = ['kiloStart', 'kiloEnd'] as const;

const YEAR_FIELDS = [
  'loadRestrictionYearStart',
  'loadRestrictionYearEnd',
  'replacementYearStart',
  'replacementYearEnd',
  'closureYearStart',
  'closureYearEnd',
  'yearBuiltStart',
  'yearBuiltEnd',
] as const;

export type CriteriaErrors = Partial<Record<keyof StructureSearchCriteria, string>>;

/**
 * Field-level messages for whatever is currently wrong — one per offending box, each judged on its
 * own, as on Site Search. The wording is the backend's, so a message reads the same whichever side
 * caught it.
 */
export const criteriaErrors = (criteria: StructureSearchCriteria): CriteriaErrors => {
  const errors: CriteriaErrors = {};
  for (const field of KILOMETRE_FIELDS) {
    if (!isKilometre(criteria[field])) {
      errors[field] = 'Kilometres must be a number, e.g. 12.5';
    }
  }
  for (const field of YEAR_FIELDS) {
    if (!isYear(criteria[field])) {
      errors[field] = 'Enter a year as yyyy, e.g. 2030';
    }
  }
  return errors;
};
