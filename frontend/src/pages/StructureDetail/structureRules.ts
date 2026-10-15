import { toYear, type Messages } from './formFields';

import type { StructureDetailsRequest } from './structureResponse';

/** Sources that need Year Added to Inventory — legacy's asterisk follows them. */
export const SOURCES_NEEDING_YEAR_ADDED = new Set(['IR', 'INSR', 'ITM', 'IFS', 'IIO']);
/** The costs are `NUMBER(6)`, whole dollars. */
const COST_MAX = 999_999;

/** The Structure card's values as typed: the numbers are text until saved. */
export type StructureValues = {
  builtByCode: string;
  yearFabricated: string;
  yearBuilt: string;
  inventoryAddedYear: string;
  sourceCode: string;
  endOfDesignLifeYear: string;
  asBuiltInfoOnFile: boolean;
  installationCost: string;
  materialCost: string;
};
type Field = keyof StructureValues;

/** A new structure's: nothing chosen, As Built Information on File No. */
export const EMPTY_STRUCTURE_VALUES: StructureValues = {
  builtByCode: '',
  yearFabricated: '',
  yearBuilt: '',
  inventoryAddedYear: '',
  sourceCode: '',
  endOfDesignLifeYear: '',
  asBuiltInfoOnFile: false,
  installationCost: '',
  materialCost: '',
};

/** A typed cost as a number, null when blank, or NaN when it is not whole dollars in range. */
const toCost = (typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  return /^\d+$/.test(trimmed) && Number(trimmed) <= COST_MAX ? Number(trimmed) : Number.NaN;
};

/** The section's blocking rules — the server's, checked before the round trip. */
export const structureErrorsOf = (
  values: StructureValues,
  sourceRequired: boolean,
): Messages<StructureValues> => {
  const errors: Messages<StructureValues> = {};
  const thisYear = new Date().getFullYear();
  const years: [Field, string][] = [
    ['yearFabricated', 'Year Superstructure Fabricated'],
    ['yearBuilt', 'Year Superstructure Installed'],
    ['inventoryAddedYear', 'Year Added to Inventory'],
    ['endOfDesignLifeYear', 'End of Design Life'],
  ];
  for (const [field, label] of years) {
    if (Number.isNaN(toYear(values[field] as string))) {
      errors[field] = `${label} must be a 4-digit year, e.g. 2004.`;
    }
  }
  const fabricated = toYear(values.yearFabricated);
  const built = toYear(values.yearBuilt);
  if (!errors.yearFabricated && fabricated !== null) {
    if (fabricated > thisYear) {
      errors.yearFabricated = `Year Superstructure Fabricated must not be after this year (${thisYear}).`;
    } else if (built !== null && !Number.isNaN(built) && fabricated > built) {
      errors.yearFabricated =
        'Year Superstructure Fabricated must not be after Year Superstructure Installed.';
    }
  }
  if (sourceRequired && !values.sourceCode) errors.sourceCode = 'Source is required.';
  if (
    SOURCES_NEEDING_YEAR_ADDED.has(values.sourceCode) &&
    !values.inventoryAddedYear.trim() &&
    !errors.inventoryAddedYear
  ) {
    errors.inventoryAddedYear = 'Year Added to Inventory is required for this Source.';
  }
  if (Number.isNaN(toCost(values.installationCost))) {
    errors.installationCost = 'Installation Cost must be whole dollars from $0 to $999,999.';
  }
  if (Number.isNaN(toCost(values.materialCost))) {
    errors.materialCost = 'Material Cost must be whole dollars from $0 to $999,999.';
  }
  return errors;
};

/**
 * Legacy's range warnings (`Structure.validate`), shown beside a field as it is typed. They never
 * stopped legacy's save and do not stop this one.
 */
export const structureWarningsOf = (values: StructureValues): Messages<StructureValues> => {
  const warnings: Messages<StructureValues> = {};
  const thisYear = new Date().getFullYear();
  const built = toYear(values.yearBuilt);
  const outside = (year: number | null, low: number, high: number, highText: string) => {
    if (year === null || Number.isNaN(year)) return undefined;
    if (year > high) return `Must not be greater than ${highText}.`;
    return year < low ? `Must be greater than or equal to ${low}.` : undefined;
  };
  warnings.yearBuilt = outside(built, 1900, thisYear, 'the current year');
  warnings.yearFabricated = outside(toYear(values.yearFabricated), 1900, thisYear + 1, 'next year');
  warnings.inventoryAddedYear = outside(
    toYear(values.inventoryAddedYear),
    1900,
    thisYear + 1,
    'next year',
  );
  // As legacy: only against a year installed.
  if (built !== null && !Number.isNaN(built)) {
    warnings.endOfDesignLifeYear = outside(
      toYear(values.endOfDesignLifeYear),
      1950,
      built + 50,
      `the year built + 50 years (${built + 50})`,
    );
  }
  const installation = toCost(values.installationCost);
  if (installation !== null && installation > 500_000) {
    warnings.installationCost = 'Installation Cost is not in the range $0 through $500,000.';
  }
  return warnings;
};

/** The values as the server takes them. */
export const toStructureRequest = (values: StructureValues): StructureDetailsRequest => ({
  builtByCode: values.builtByCode,
  yearFabricated: toYear(values.yearFabricated),
  yearBuilt: toYear(values.yearBuilt),
  inventoryAddedYear: toYear(values.inventoryAddedYear),
  sourceCode: values.sourceCode,
  endOfDesignLifeYear: toYear(values.endOfDesignLifeYear),
  asBuiltInfoOnFile: values.asBuiltInfoOnFile,
  installationCost: toCost(values.installationCost),
  materialCost: toCost(values.materialCost),
});
