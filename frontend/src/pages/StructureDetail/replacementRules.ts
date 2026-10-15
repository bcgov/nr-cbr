import { overLimitError } from '@/utils/textLimits';

import { toYear, type Messages as FormMessages } from './formFields';

import type { ReplacementDetailsRequest } from './structureResponse';

/** `ESTIMATED_REPLACEMENT_COST NUMBER(8)`, whole dollars. */
const COST_MAX = 99_999_999;
/** `ESTIMATED_REPLACEMENT_COST_CMT VARCHAR2(2000 BYTE)`. */
export const TEXT_MAX = 2000;

export type ReplacementValues = {
  closureYear: string;
  replacementYear: string;
  loadRestrictionYear: string;
  cost: string;
  costComment: string;
};
type Field = keyof ReplacementValues;
type Messages = FormMessages<ReplacementValues>;

/** A typed cost as a number, null when blank, or NaN when it is not whole dollars in range. */
const toCost = (typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  return /^\d+$/.test(trimmed) && Number(trimmed) <= COST_MAX ? Number(trimmed) : Number.NaN;
};

const usable = (year: number | null): year is number => year !== null && !Number.isNaN(year);

/** Estimated Load Restriction not after Estimated Replacement, nor after Estimated Closure. */
const restrictionOrderError = (
  restriction: number | null,
  replacement: number | null,
  closure: number | null,
): string | undefined => {
  if (!usable(restriction)) return undefined;
  if (usable(replacement) && restriction > replacement) {
    return 'Estimated Load Restriction must not be after Estimated Replacement.';
  }
  return usable(closure) && restriction > closure
    ? 'Estimated Load Restriction must not be after Estimated Closure.'
    : undefined;
};

/** The form's blocking rules — legacy's `StructureForm.validate`, checked as the values change. */
export const replacementErrorsOf = (
  values: ReplacementValues,
  restrictionRequired: boolean,
): Messages => {
  const errors: Messages = {};
  const years: [Field, string][] = [
    ['closureYear', 'Estimated Closure'],
    ['replacementYear', 'Estimated Replacement'],
    ['loadRestrictionYear', 'Estimated Load Restriction'],
  ];
  for (const [field, label] of years) {
    if (Number.isNaN(toYear(values[field]))) {
      errors[field] = `${label} must be a 4-digit year, e.g. 2004.`;
    }
  }
  const closure = toYear(values.closureYear);
  const replacement = toYear(values.replacementYear);
  const restriction = toYear(values.loadRestrictionYear);
  if (restriction === null && restrictionRequired) {
    errors.loadRestrictionYear = 'Estimated Load Restriction is required.';
  }
  if (usable(replacement) && usable(closure) && replacement > closure) {
    errors.replacementYear = 'Estimated Replacement must not be after Estimated Closure.';
  }
  errors.loadRestrictionYear ??= restrictionOrderError(restriction, replacement, closure);
  if (Number.isNaN(toCost(values.cost))) {
    errors.cost = 'Estimated Replacement Cost must be whole dollars from $0 to $99,999,999.';
  }
  if (overLimitError(values.costComment, TEXT_MAX)) {
    errors.costComment = overLimitError(values.costComment, TEXT_MAX);
  }
  return errors;
};

/**
 * Legacy's warnings (`Structure.validate`, `StructureAction.validate`), shown as typed; they never
 * stopped its save. The year limits count from Year Superstructure Installed, when there is one.
 */
export const replacementWarningsOf = (
  values: ReplacementValues,
  yearBuilt: number | null,
): Messages => {
  const warnings: Messages = {};
  const outside = (year: number | null, offset: number) => {
    if (!usable(year)) return undefined;
    if (yearBuilt !== null && year > yearBuilt + offset) {
      return `Must not be greater than the year built + ${offset} years (${yearBuilt + offset}).`;
    }
    return year < 1950 ? 'Must be greater than or equal to 1950.' : undefined;
  };
  warnings.closureYear = outside(toYear(values.closureYear), 50);
  warnings.replacementYear = outside(toYear(values.replacementYear), 60);
  warnings.loadRestrictionYear = outside(toYear(values.loadRestrictionYear), 50);
  const cost = toCost(values.cost);
  if (cost !== null && !Number.isNaN(cost) && cost > 2_000_000) {
    warnings.cost = 'Estimated Replacement Cost is not in the range $0 through $2,000,000.';
  }
  return warnings;
};

/** A new structure's: nothing entered. */
export const EMPTY_REPLACEMENT_VALUES: ReplacementValues = {
  closureYear: '',
  replacementYear: '',
  loadRestrictionYear: '',
  cost: '',
  costComment: '',
};

/** The values as the server takes them. */
export const toReplacementRequest = (values: ReplacementValues): ReplacementDetailsRequest => ({
  closureYear: toYear(values.closureYear),
  replacementYear: toYear(values.replacementYear),
  loadRestrictionYear: toYear(values.loadRestrictionYear),
  cost: toCost(values.cost),
  costComment: values.costComment,
});
