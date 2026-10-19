import { addYears, toIso } from '@/pages/StructureDetail/dateBox';
import { overLimitError } from '@/utils/textLimits';

/** `LOAD_RATING NUMBER(6)`. */
const RATING_MAX = 999_999;
/** The reason that needs words. */
export const OTHER_REASON = 'OTHR';
/** Legacy's historical reason, never offered. */
const HISTORICAL_REASON = 'HIST';
/** `STRCTRE_LOAD_RATING_RSN_CMNT VARCHAR2(4000)`. */
export const REASON_TEXT_MAX = 4000;
/** Legacy's routine inspection frequencies, in years. */
export const FREQUENCIES = [1, 2, 3, 4, 5, 6];

/** The Review dialog's values as typed: the rating and dates as their boxes hold them. */
export type ReviewValues = {
  loadRating: string;
  reasonCode: string;
  reasonComment: string;
  nextRoutineDate: string;
  routineFrequencyYears: string;
  loadRatingReviewRequired: boolean;
};

export type ReviewField = Exclude<keyof ReviewValues, 'loadRatingReviewRequired'>;

type Reason = { code: string; description: string; deltas: string[] };

/** A typed rating as a number, null when blank, or NaN when it is not a whole number in range. */
export const toRating = (typed: string): number | null => {
  const trimmed = typed.trim();
  if (!trimmed) return null;
  return /^\d+$/.test(trimmed) && Number(trimmed) <= RATING_MAX ? Number(trimmed) : Number.NaN;
};

/** Legacy's grouping of the rating against the structure's current one: INC, DEC or NOC. */
export const deltaOf = (rating: number, current: number | null): string => {
  const change = rating - (current ?? 0);
  if (change > 0) return 'INC';
  if (change < 0) return 'DEC';
  return 'NOC';
};

/**
 * The reasons legacy's dialog offers: those that fit the change from the structure's current
 * rating, or any change — never HIST — and none until a rating is typed.
 */
export const reasonsFor = (
  reasons: Reason[],
  rating: number | null,
  current: number | null,
): Reason[] => {
  if (rating === null || Number.isNaN(rating)) return [];
  const delta = deltaOf(rating, current);
  return reasons.filter(
    (reason) =>
      reason.code !== HISTORICAL_REASON &&
      (reason.deltas.includes(delta) || reason.deltas.includes('UNDEF')),
  );
};

/** The next routine inspection legacy suggests: the inspection's date plus the frequency. */
export const suggestedNextDate = (inspectionDate: string | null, years: string): string =>
  inspectionDate && years ? addYears(inspectionDate, Number(years)) : '';

/**
 * How a planned year stands — legacy's colours: overdue (red) once past, due soon (yellow) within
 * five years, otherwise nothing.
 */
export const yearStanding = (
  year: number | null,
  thisYear: number,
): { kind: 'overdue'; years: number } | { kind: 'soon' } | null => {
  if (year === null) return null;
  const overdue = thisYear - year;
  if (overdue > 0) return { kind: 'overdue', years: overdue };
  return overdue > -5 ? { kind: 'soon' } : null;
};

/**
 * The dialog's rules — legacy's, which it checked only in the browser, and the server's: a rating,
 * a reason that fits when any does, Other's words, the frequency, and a routine inspection's next
 * planned date.
 */
export const reviewErrorsOf = (
  values: ReviewValues,
  offered: Reason[],
  routine: boolean,
): Partial<Record<ReviewField, string>> => {
  const errors: Partial<Record<ReviewField, string>> = {};
  const rating = toRating(values.loadRating);
  if (rating === null) errors.loadRating = 'Current Load Rating is required.';
  else if (Number.isNaN(rating)) {
    errors.loadRating = `Current Load Rating must be a whole number from 0 to ${RATING_MAX}.`;
  } else if (!values.reasonCode) {
    if (offered.length > 0) errors.reasonCode = 'Load Rating Reason is required.';
  } else if (!offered.some((reason) => reason.code === values.reasonCode)) {
    errors.reasonCode = 'Choose a reason that fits the change in load rating.';
  }
  if (values.reasonCode === OTHER_REASON) {
    errors.reasonComment = values.reasonComment.trim()
      ? overLimitError(values.reasonComment, REASON_TEXT_MAX) || undefined
      : 'Load Rating Reason Comment is required for Other.';
  }
  if (!values.routineFrequencyYears) {
    errors.routineFrequencyYears = 'Routine Inspection Frequency is required.';
  }
  if (routine) {
    if (!values.nextRoutineDate.trim()) {
      errors.nextRoutineDate = 'Next Planned Inspection Date is required.';
    } else if (toIso(values.nextRoutineDate) === null) {
      errors.nextRoutineDate = 'Next Planned Inspection Date must be a date, as yyyy/mm/dd.';
    }
  }
  return errors;
};
