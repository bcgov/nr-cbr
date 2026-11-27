import type { StructureInventoryCommonCriteria } from './types';

import { isKilometre } from '@/pages/SiteSearch/validation';
import { toIso } from '@/pages/StructureDetail/dateBox';
import { isYear } from '@/pages/StructureSearch/validation';

/** The messages the reports' boxes share with Structure Search. */
export const KILOMETRES_MESSAGE = 'Kilometres must be a number, e.g. 12.5';
export const YEAR_MESSAGE = 'Enter a year as yyyy, e.g. 2030';
export const DAY_MESSAGE = 'Enter a date as yyyy/mm/dd, e.g. 2026/01/31';
export const REVERSED_DAYS = 'The end date cannot be before the start date';

/**
 * The most Forest Districts one run of Inspections Conducted – Detail or Inspections – Blank Report
 * may ask for. A province's run of either is more than the server can produce before the browser
 * gives up, so the business made the district required, up to eight, then let an Area do instead
 * (2026-11-27). The server holds the same rule.
 */
export const MAX_DISTRICTS = 8;
export const AREA_OR_DISTRICT_REQUIRED = 'Select an Area or at least one Forest District.';
export const TOO_MANY_DISTRICTS = `Select no more than ${MAX_DISTRICTS} Forest Districts.`;

/**
 * The Area and Forest District rule of those two reports: an Area or a district — both is fine —
 * and never more than {@link MAX_DISTRICTS} districts, Area or not. The message goes under Forest
 * District.
 */
export const districtError = (area: string, orgUnit: readonly string[]): string | undefined => {
  if (orgUnit.length === 0 && area.trim() === '') {
    return AREA_OR_DISTRICT_REQUIRED;
  }
  return orgUnit.length > MAX_DISTRICTS ? TOO_MANY_DISTRICTS : undefined;
};

/**
 * The messages for a "from – to" pair of days, `yyyy/mm/dd` as legacy's: each box judged on its
 * own, then — when both are days — the end checked against the start, on the "To" box, as the
 * month ranges do.
 */
export const dayRangeErrors = (start: string, end: string): { start?: string; end?: string } => {
  const errors: { start?: string; end?: string } = {};
  const from = start === '' ? '' : toIso(start);
  const to = end === '' ? '' : toIso(end);
  if (from === null) {
    errors.start = DAY_MESSAGE;
  }
  if (to === null) {
    errors.end = DAY_MESSAGE;
  }
  if (from && to && from > to) {
    errors.end = REVERSED_DAYS;
  }
  return errors;
};

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
