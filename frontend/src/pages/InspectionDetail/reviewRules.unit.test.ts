import { describe, expect, it } from 'vitest';

import {
  deltaOf,
  reasonsFor,
  reviewErrorsOf,
  suggestedNextDate,
  yearStanding,
  type ReviewValues,
} from './reviewRules';

const reasons = [
  { code: 'INSP', description: 'Inspection', deltas: ['INC'] },
  { code: 'DOWN', description: 'Downrated', deltas: ['DEC'] },
  { code: 'SAME', description: 'No change', deltas: ['NOC'] },
  { code: 'OTHR', description: 'Other', deltas: ['UNDEF'] },
  { code: 'HIST', description: 'Historical', deltas: ['UNDEF'] },
];

const values = (overrides: Partial<ReviewValues> = {}): ReviewValues => ({
  loadRating: '40',
  reasonCode: 'INSP',
  reasonComment: '',
  nextRoutineDate: '2027/05/01',
  routineFrequencyYears: '3',
  loadRatingReviewRequired: false,
  ...overrides,
});

describe('reasonsFor', () => {
  it("offers the change's reasons and any change's, never HIST, and none before a rating", () => {
    const codes = (rating: number | null, current: number | null) =>
      reasonsFor(reasons, rating, current).map((reason) => reason.code);
    expect(codes(60, 50)).toEqual(['INSP', 'OTHR']);
    expect(codes(40, 50)).toEqual(['DOWN', 'OTHR']);
    expect(codes(50, 50)).toEqual(['SAME', 'OTHR']);
    // No current rating counts as 0, as legacy.
    expect(deltaOf(0, null)).toBe('NOC');
    expect(codes(null, 50)).toEqual([]);
    expect(codes(Number.NaN, 50)).toEqual([]);
  });
});

describe('suggestedNextDate', () => {
  it('adds the frequency to the inspection date, as legacy', () => {
    expect(suggestedNextDate('2024-05-01', '3')).toBe('2027/05/01');
    expect(suggestedNextDate('2024-02-29', '1')).toBe('2025/02/28');
    expect(suggestedNextDate(null, '3')).toBe('');
    expect(suggestedNextDate('2024-05-01', '')).toBe('');
  });
});

describe('yearStanding', () => {
  it('is overdue once past, due soon within five years, otherwise nothing', () => {
    expect(yearStanding(2020, 2026)).toEqual({ kind: 'overdue', years: 6 });
    expect(yearStanding(2026, 2026)).toEqual({ kind: 'soon' });
    expect(yearStanding(2030, 2026)).toEqual({ kind: 'soon' });
    expect(yearStanding(2031, 2026)).toBeNull();
    expect(yearStanding(null, 2026)).toBeNull();
  });
});

describe('reviewErrorsOf', () => {
  const offered = reasonsFor(reasons, 40, 30);

  it('needs a rating, a fitting reason, the frequency and, routine, the next date', () => {
    expect(reviewErrorsOf(values(), offered, true)).toEqual({});
    expect(
      reviewErrorsOf(
        values({ loadRating: '', routineFrequencyYears: '', nextRoutineDate: '' }),
        [],
        true,
      ),
    ).toEqual({
      loadRating: 'Current Load Rating is required.',
      routineFrequencyYears: 'Routine Inspection Frequency is required.',
      nextRoutineDate: 'Next Planned Inspection Date is required.',
    });
    expect(reviewErrorsOf(values({ loadRating: '4.5' }), offered, true).loadRating).toMatch(
      /whole number/,
    );
    expect(reviewErrorsOf(values({ reasonCode: '' }), offered, true).reasonCode).toBe(
      'Load Rating Reason is required.',
    );
    expect(reviewErrorsOf(values({ reasonCode: 'DOWN' }), offered, true).reasonCode).toBe(
      'Choose a reason that fits the change in load rating.',
    );
    expect(reviewErrorsOf(values({ nextRoutineDate: '2027/02/30' }), offered, true)).toEqual({
      nextRoutineDate: 'Next Planned Inspection Date must be a date, as yyyy/mm/dd.',
    });
    // Not routine: no next date asked.
    expect(reviewErrorsOf(values({ nextRoutineDate: '' }), offered, false)).toEqual({});
  });

  it("needs Other's words, and no reason when none fits", () => {
    expect(reviewErrorsOf(values({ reasonCode: 'OTHR' }), offered, true)).toEqual({
      reasonComment: 'Load Rating Reason Comment is required for Other.',
    });
    expect(reviewErrorsOf(values({ reasonCode: '' }), [], true)).toEqual({});
  });
});
