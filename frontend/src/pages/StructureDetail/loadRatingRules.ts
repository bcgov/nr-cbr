import { overLimitError } from '@/utils/textLimits';

import type { Messages } from './formFields';
import type { LoadRatingDetailsRequest } from './structureResponse';

/** The vehicle that needs words. */
export const OTHER_VEHICLE = 'OTHER';
/** `DESIGN_LOAD_RATING NUMBER(6)`. */
const RATING_MAX = 999_999;
/** `DESIGN_VEHICLE_CMT VARCHAR2(2000)`. */
export const TEXT_MAX = 2000;

/** Load Rating Details' values as typed. */
export type LoadRatingValues = {
  loadPostingSignsCode: string;
  reviewRequired: boolean;
  designVehicleCode: string;
  designVehicleComment: string;
  designLoadRating: string;
};

/** A new structure's, as legacy's page started one: posting signs Unknown, review required. */
export const NEW_LOAD_RATING_VALUES: LoadRatingValues = {
  loadPostingSignsCode: 'UNK',
  reviewRequired: true,
  designVehicleCode: '',
  designVehicleComment: '',
  designLoadRating: '',
};

/**
 * Legacy's advice on Load Rating Review Required (`StructureAction.java:716-738`), which never
 * stopped its save: an error when there is no rating and no review asked for, a warning when there
 * is a rating and a review is still asked for.
 */
export const reviewAdvice = (reviewRequired: boolean, hasRatings: boolean) => {
  if (!reviewRequired && !hasRatings) {
    return {
      invalid: true,
      invalidText: 'Required field: with no load rating, a review should be required.',
    };
  }
  if (reviewRequired && hasRatings) {
    return {
      warn: true,
      warnText: 'Load rating review is required although a rating is recorded.',
    };
  }
  return {};
};

/** What the chosen vehicle means for the design load rating. */
export type DesignRating = {
  /** The vehicle fixes none, so the rating is typed. */
  typed: boolean;
  /** The rating a vehicle fixes: its own when just chosen, the one stored when it is unchanged. */
  fixed: number | null;
  /** The vehicle is Other, which needs its words. */
  other: boolean;
};

/**
 * The design load rating's state for these values. Choosing a vehicle that fixes the design rating
 * sets it; one left as it was keeps the rating stored, as legacy's page. A vehicle that fixes none
 * leaves it to be typed.
 */
export const designRatingOf = (
  values: LoadRatingValues,
  vehicles: { code: string; designLoadRating: number | null }[],
  storedVehicleCode: string,
  storedRating: number | null,
): DesignRating => {
  const chosen = vehicles.find((vehicle) => vehicle.code === values.designVehicleCode);
  const own = chosen?.designLoadRating ?? null;
  const vehicleChanged = values.designVehicleCode !== storedVehicleCode;
  return {
    typed: own === null,
    fixed: own === null || vehicleChanged ? own : storedRating,
    other: values.designVehicleCode === OTHER_VEHICLE,
  };
};

/** A typed design load rating's error: a whole number from 1 up to the column's limit. */
const typedRatingError = (typed: string): string | undefined => {
  if (!/^\d+$/.test(typed) || Number(typed) > RATING_MAX) {
    return `Original Design Load Rating must be a whole number up to ${RATING_MAX}.`;
  }
  return Number(typed) < 1
    ? 'Original Design Load Rating GVW (tonnes) must be greater than or equal to 1.'
    : undefined;
};

/** The section's blocking rules, checked as the values change. */
export const loadRatingErrorsOf = (
  values: LoadRatingValues,
  design: DesignRating,
): Messages<LoadRatingValues> => {
  const errors: Messages<LoadRatingValues> = {};
  if (!values.loadPostingSignsCode) {
    errors.loadPostingSignsCode = 'Are Load Posting Signs Warranted? is required.';
  }
  if (design.other) {
    errors.designVehicleComment = values.designVehicleComment.trim()
      ? overLimitError(values.designVehicleComment, TEXT_MAX) || undefined
      : 'Design Vehicle Configuration Details is required.';
  }
  const typed = values.designLoadRating.trim();
  if (design.typed && typed) errors.designLoadRating = typedRatingError(typed);
  return errors;
};

/** The values as the server takes them. */
export const toLoadRatingRequest = (
  values: LoadRatingValues,
  design: DesignRating,
): LoadRatingDetailsRequest => {
  const typed = values.designLoadRating.trim();
  const typedValue = typed ? Number(typed) : null;
  return {
    ...values,
    designVehicleComment: design.other ? values.designVehicleComment : '',
    designLoadRating: design.typed ? typedValue : design.fixed,
  };
};
