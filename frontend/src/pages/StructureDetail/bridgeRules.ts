import { overLimitError } from '@/utils/textLimits';

import type { Messages as FormMessages } from './formFields';
import type { BridgeDetailsRequest } from './structureResponse';

export const DURABLE = 'PB';
export const OTHER = 'OTH';
/** The two bridge Type/Classes, in legacy's order. */
export const BRIDGE_TYPES = ['PB', 'TB'];
/** Superstructures a portable structure may have — legacy's `errors.structure.portablestructure`. */
const PORTABLE_SUPERSTRUCTURES = new Set(['SBB', 'SIB', 'STBA', 'TU', 'TT']);
/** What a Durable Bridge may not be made of. */
const TEMPORARY_SUPERSTRUCTURES = new Set(['LGST', 'RAIL', 'TU']);
const TEMPORARY_ABUTMENTS = new Set(['CLT', 'PT', 'ST']);
/** The comment columns, in bytes. */
export const TEXT_MAX = 2000;

/** The form's values as typed: the lengths are text until saved. */
export type BridgeValues = {
  typeClassCode: string;
  portable: boolean;
  needleBeams: boolean;
  totalBridgeLength: string;
  superstructureTypeCode: string;
  superstructureComment: string;
  deckTypeCode: string;
  deckTypeComment: string;
  deckWidth: string;
  runningSurfaceCode: string;
  curbTypeCode: string;
  curbTypeComment: string;
  leftAbutmentCode: string;
  rightAbutmentCode: string;
  abutmentComment: string;
};
type Field = keyof BridgeValues;
type Messages = BridgeMessages;

/** A typed length as a number, null when blank, or NaN when it is not a number with ≤ `scale` decimals. */
export const toMetres = (typed: string, scale: number): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  const pattern = new RegExp(String.raw`^\d+(\.\d{0,${scale}})?$`);
  return pattern.test(trimmed) ? Number(trimmed) : Number.NaN;
};

/** What was stored, so a field that had a value is required (legacy's tier rule). */
export type BridgeStored = { [K in Field]?: boolean };

/** The form's blocking rules — the server's, checked as the values change. */
export const bridgeErrorsOf = (values: BridgeValues, stored: BridgeStored): Messages => {
  const errors: Messages = {};
  const durable = values.typeClassCode === DURABLE;
  const metres = (field: Field, label: string, scale: number, max: number) => {
    const value = toMetres(values[field] as string, scale);
    if (value === null) {
      if (stored[field]) errors[field] = `${label} is required.`;
    } else if (Number.isNaN(value)) {
      errors[field] = `${label} must be a number of metres, with up to ${scale} decimal places.`;
    } else if (value > max) {
      errors[field] = `${label} can be at most ${max} metres.`;
    }
  };
  metres('totalBridgeLength', 'Bridge Length', 3, 99_999.999);
  metres('deckWidth', 'Deck Width', 2, 99_999.99);

  const code = (field: Field, label: string) => {
    if (!values[field] && stored[field]) errors[field] = `${label} is required.`;
  };
  code('superstructureTypeCode', 'Superstructure');
  code('deckTypeCode', 'Deck Type');
  code('runningSurfaceCode', 'Running Surface');
  code('leftAbutmentCode', 'Left Abutment');
  code('rightAbutmentCode', 'Right Abutment');
  if (!values.curbTypeCode && durable) {
    errors.curbTypeCode = 'Curb Type is required for a Durable Bridge.';
  }

  const other = (when: boolean, field: Field, label: string) => {
    if (!when) return;
    const text = values[field] as string;
    if (!text.trim()) errors[field] = `${label} is required.`;
    else if (overLimitError(text, TEXT_MAX)) errors[field] = overLimitError(text, TEXT_MAX);
  };
  other(values.superstructureTypeCode === OTHER, 'superstructureComment', 'Superstructure Details');
  other(values.deckTypeCode === OTHER, 'deckTypeComment', 'Deck Type Details');
  other(values.curbTypeCode === OTHER, 'curbTypeComment', 'Curb Type Description');
  other(
    values.leftAbutmentCode === OTHER || values.rightAbutmentCode === OTHER,
    'abutmentComment',
    'Abutment Details',
  );

  if (
    values.portable &&
    !errors.superstructureTypeCode &&
    !PORTABLE_SUPERSTRUCTURES.has(values.superstructureTypeCode)
  ) {
    errors.superstructureTypeCode =
      'Only Steel Box Beam, Steel I-beam, Steel Truss/Bailey/Acrow, Timber - Untreated Stringers and Timber - Treated Stringers are allowed when Portable Structure is checked.';
  }
  if (durable) {
    if (
      !errors.superstructureTypeCode &&
      TEMPORARY_SUPERSTRUCTURES.has(values.superstructureTypeCode)
    ) {
      errors.superstructureTypeCode =
        'Superstructure: temporary materials are not permitted in a Durable Bridge.';
    }
    if (!errors.leftAbutmentCode && TEMPORARY_ABUTMENTS.has(values.leftAbutmentCode)) {
      errors.leftAbutmentCode =
        'Left Abutment: temporary materials are not permitted in a Durable Bridge.';
    }
    if (!errors.rightAbutmentCode && TEMPORARY_ABUTMENTS.has(values.rightAbutmentCode)) {
      errors.rightAbutmentCode =
        'Right Abutment: temporary materials are not permitted in a Durable Bridge.';
    }
  }
  return errors;
};

/** Legacy's range warnings, shown beside a field; they never stopped its save. */
export const bridgeWarningsOf = (values: BridgeValues): Messages => {
  const warnings: Messages = {};
  const length = toMetres(values.totalBridgeLength, 3);
  if (length !== null && !Number.isNaN(length) && length > 400) {
    warnings.totalBridgeLength = 'Bridge Length is not in the range 0 through 400.';
  }
  const width = toMetres(values.deckWidth, 2);
  if (width !== null && !Number.isNaN(width) && width > 12) {
    warnings.deckWidth = 'Deck Width is not in the range 0 through 12.';
  }
  return warnings;
};

export type BridgeMessages = FormMessages<BridgeValues>;

/** A new bridge's: nothing chosen, Portable and Needle Beams No. */
export const EMPTY_BRIDGE_VALUES: BridgeValues = {
  typeClassCode: '',
  portable: false,
  needleBeams: false,
  totalBridgeLength: '',
  superstructureTypeCode: '',
  superstructureComment: '',
  deckTypeCode: '',
  deckTypeComment: '',
  deckWidth: '',
  runningSurfaceCode: '',
  curbTypeCode: '',
  curbTypeComment: '',
  leftAbutmentCode: '',
  rightAbutmentCode: '',
  abutmentComment: '',
};

/** Every field the completeness rules want on a new bridge, as if each were already stored. */
export const NEW_BRIDGE_REQUIRED: BridgeStored = {
  totalBridgeLength: true,
  deckWidth: true,
  superstructureTypeCode: true,
  deckTypeCode: true,
  runningSurfaceCode: true,
  leftAbutmentCode: true,
  rightAbutmentCode: true,
};

/** The values as the server takes them. */
export const toBridgeRequest = (values: BridgeValues): BridgeDetailsRequest => ({
  ...values,
  totalBridgeLength: toMetres(values.totalBridgeLength, 3),
  deckWidth: toMetres(values.deckWidth, 2),
});
