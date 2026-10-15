import { overLimitError } from '@/utils/textLimits';

import type { Messages as FormMessages } from './formFields';
import type { CulvertDetailsRequest } from './structureResponse';

export const OPEN_BOTTOM = 'OPN';
export const OTHER = 'OTH';
/** `CULVERT_MATERIAL_COMMENT VARCHAR2(2000)`. */
export const TEXT_MAX = 2000;

/** The form's values as typed: the numbers are text until saved. */
export type CulvertValues = {
  portable: boolean;
  culvertNumber: string;
  lengthMetres: string;
  gradient: string;
  culvertTypeCode: string;
  materialCode: string;
  materialComment: string;
  inletCoverDepthMm: string;
  outletCoverDepthMm: string;
  openingHeightMm: string;
  openingWidthMm: string;
  headwallLocationCode: string;
  openBottomCode: string;
};
type Field = keyof CulvertValues;
type Messages = FormMessages<CulvertValues>;
export type NumberField =
  | 'culvertNumber'
  | 'lengthMetres'
  | 'gradient'
  | 'inletCoverDepthMm'
  | 'outletCoverDepthMm'
  | 'openingHeightMm'
  | 'openingWidthMm';

/** How each number is typed and what its column holds. */
const NUMBERS: Record<
  NumberField,
  { label: string; pattern: RegExp; max: number; format: string }
> = {
  culvertNumber: {
    label: 'Culvert Number',
    pattern: /^\d+$/,
    max: 99_999,
    format: 'a whole number',
  },
  lengthMetres: {
    label: 'Culvert Length',
    pattern: /^\d+(\.\d?)?$/,
    max: 99_999.9,
    format: 'metres, with up to 1 decimal place',
  },
  gradient: {
    label: 'Gradient',
    pattern: /^-?\d+(\.\d?)?$/,
    max: 999.9,
    format: 'a number with up to 1 decimal place',
  },
  inletCoverDepthMm: {
    label: 'Inlet Cover Depth',
    pattern: /^\d+$/,
    max: 9_999_999_999,
    format: 'whole millimetres',
  },
  outletCoverDepthMm: {
    label: 'Outlet Cover Depth',
    pattern: /^\d+$/,
    max: 9_999_999_999,
    format: 'whole millimetres',
  },
  openingHeightMm: {
    label: 'Opening Height',
    pattern: /^\d+$/,
    max: 9_999_999_999,
    format: 'whole millimetres',
  },
  openingWidthMm: {
    label: 'Opening Width',
    pattern: /^\d+$/,
    max: 9_999_999_999,
    format: 'whole millimetres',
  },
};

/** A typed number, null when blank, or NaN when it is not as the field takes it. */
export const toNumber = (field: NumberField, typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  const { pattern, max } = NUMBERS[field];
  return pattern.test(trimmed) && Math.abs(Number(trimmed)) <= max ? Number(trimmed) : Number.NaN;
};

/** What was stored, so a field that had a value is required (legacy's tier rule). */
export type CulvertStored = Partial<Record<Field, boolean>>;

/** The numbers: required once stored, and as their columns hold them. */
const numberErrors = (values: CulvertValues, stored: CulvertStored): Messages => {
  const errors: Messages = {};
  for (const field of Object.keys(NUMBERS) as NumberField[]) {
    const value = toNumber(field, values[field]);
    const { label, format } = NUMBERS[field];
    if (value === null && stored[field]) errors[field] = `${label} is required.`;
    else if (Number.isNaN(value))
      errors[field] = `${label} must be ${format}, as the column holds.`;
  }
  return errors;
};

/** Culvert Type, once stored, and Open Bottom Substructure for an open-bottom culvert. */
const typeErrors = (values: CulvertValues, stored: CulvertStored): Messages => {
  const errors: Messages = {};
  if (!values.culvertTypeCode && stored.culvertTypeCode) {
    errors.culvertTypeCode = 'Culvert Type is required.';
  }
  if (values.culvertTypeCode === OPEN_BOTTOM && !values.openBottomCode) {
    errors.openBottomCode = 'Open Bottom Substructure is required for an open-bottom culvert.';
  }
  return errors;
};

/** Culvert Material, once stored, and an Other's details. */
const materialErrors = (values: CulvertValues, stored: CulvertStored): Messages => {
  const errors: Messages = {};
  if (!values.materialCode && stored.materialCode) {
    errors.materialCode = 'Culvert Material is required.';
  }
  if (values.materialCode === OTHER) {
    errors.materialComment = values.materialComment.trim()
      ? overLimitError(values.materialComment, TEXT_MAX) || undefined
      : 'Culvert Details is required.';
  }
  return errors;
};

/** The form's blocking rules — the server's, checked as the values change. */
export const culvertErrorsOf = (
  values: CulvertValues,
  stored: CulvertStored,
  woodLog: boolean,
): Messages => ({
  ...numberErrors(values, stored),
  // A Wood Log Culvert has no Culvert Type or Open Bottom Substructure.
  ...(woodLog ? {} : typeErrors(values, stored)),
  ...materialErrors(values, stored),
});

/** Legacy's range warnings (`Culvert.validate`), shown as typed; they never stopped its save. */
const RANGES: Partial<Record<NumberField, [number, number]>> = {
  culvertNumber: [1, 10],
  lengthMetres: [5, 100],
  gradient: [0, 20],
  inletCoverDepthMm: [300, 5_000],
  outletCoverDepthMm: [300, 5_000],
  openingHeightMm: [300, 3_000],
  openingWidthMm: [300, 6_000],
};

export const culvertWarningsOf = (values: CulvertValues): Messages => {
  const warnings: Messages = {};
  for (const [field, [low, high]] of Object.entries(RANGES) as [NumberField, [number, number]][]) {
    const value = toNumber(field, values[field]);
    if (value !== null && !Number.isNaN(value) && (value < low || value > high)) {
      warnings[field] =
        `${NUMBERS[field].label} is not in the range ${low.toLocaleString()} through ${high.toLocaleString()}.`;
    }
  }
  return warnings;
};

/** A new culvert's: nothing entered, Portable No. */
export const EMPTY_CULVERT_VALUES: CulvertValues = {
  portable: false,
  culvertNumber: '',
  lengthMetres: '',
  gradient: '',
  culvertTypeCode: '',
  materialCode: '',
  materialComment: '',
  inletCoverDepthMm: '',
  outletCoverDepthMm: '',
  openingHeightMm: '',
  openingWidthMm: '',
  headwallLocationCode: '',
  openBottomCode: '',
};

/** Every field the completeness rules want on a new culvert, as if each were already stored. */
export const NEW_CULVERT_REQUIRED: CulvertStored = {
  culvertNumber: true,
  lengthMetres: true,
  openingHeightMm: true,
  openingWidthMm: true,
  culvertTypeCode: true,
  materialCode: true,
};

/** The values as the server takes them. */
export const toCulvertRequest = (values: CulvertValues): CulvertDetailsRequest => ({
  ...values,
  culvertNumber: toNumber('culvertNumber', values.culvertNumber),
  lengthMetres: toNumber('lengthMetres', values.lengthMetres),
  gradient: toNumber('gradient', values.gradient),
  inletCoverDepthMm: toNumber('inletCoverDepthMm', values.inletCoverDepthMm),
  outletCoverDepthMm: toNumber('outletCoverDepthMm', values.outletCoverDepthMm),
  openingHeightMm: toNumber('openingHeightMm', values.openingHeightMm),
  openingWidthMm: toNumber('openingWidthMm', values.openingWidthMm),
});
