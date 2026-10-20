import type { StructureInventorySummaryCriteria } from './types';

import { isKilometre } from '@/pages/SiteSearch/validation';
import { isYear } from '@/pages/StructureSearch/validation';

/** An opening width bound: unset, or whole millimetres — legacy's `######`. */
const isOpeningWidth = (value: string): boolean => value === '' || /^\d{1,6}$/.test(value);

const KILOMETRE_FIELDS = ['kiloStart', 'kiloEnd'] as const;

const YEAR_FIELDS = [
  'yearBuiltStart',
  'yearBuiltEnd',
  'loadRestrictionYearStart',
  'loadRestrictionYearEnd',
  'replacementYearStart',
  'replacementYearEnd',
] as const;

const OPENING_WIDTH_FIELDS = ['openingWidthMin', 'openingWidthMax'] as const;

export type CriteriaErrors = Partial<Record<keyof StructureInventorySummaryCriteria, string>>;

/**
 * Field-level messages for whatever is wrong — one per offending box, each judged on its own.
 * Legacy's rules: kilometres are a number, years are four digits, an opening width is whole
 * millimetres. The wording matches Structure Search's for the boxes the two share.
 */
export const criteriaErrors = (criteria: StructureInventorySummaryCriteria): CriteriaErrors => {
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
  for (const field of OPENING_WIDTH_FIELDS) {
    if (!isOpeningWidth(criteria[field])) {
      errors[field] = 'Opening width must be whole millimetres, e.g. 1200';
    }
  }
  return errors;
};
