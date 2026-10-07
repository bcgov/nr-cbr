import { describe, expect, it } from 'vitest';

import { formatDateTime, formatShortDate } from './date';

describe('formatShortDate', () => {
  it('formats an ISO date', () => {
    expect(formatShortDate('2002-12-01')).toBe('Dec 1, 2002');
  });
});

describe('formatDateTime', () => {
  it('formats an ISO timestamp with its time', () => {
    expect(formatDateTime('2024-06-03T14:05:00')).toBe('Jun 3, 2024 2:05 PM');
  });

  it('formats a bare date without a time', () => {
    expect(formatDateTime('2024-06-03')).toBe('Jun 3, 2024');
  });

  it('returns blank for nothing, and anything else unchanged', () => {
    expect(formatDateTime(null)).toBe('');
    expect(formatDateTime('not a date')).toBe('not a date');
  });
});
