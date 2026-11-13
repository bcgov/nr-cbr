import { describe, expect, it } from 'vitest';

import { areasAfterDistrictChange, districtsKey, joinValues, restoreCriteria } from './multiSelect';

describe('joinValues', () => {
  it('sends the picks comma-separated, without blanks', () => {
    expect(joinValues(['ACT', ' BAR ', ''])).toBe('ACT,BAR');
    expect(joinValues([])).toBe('');
  });
});

describe('districtsKey', () => {
  it('sorts the districts, so the order they were picked in shares one cache entry', () => {
    expect(districtsKey(['21', '18'])).toBe('18,21');
    expect(districtsKey(['18', '21'])).toBe('18,21');
  });

  it('takes a single district as it is', () => {
    expect(districtsKey(' 18 ')).toBe('18');
    expect(districtsKey([])).toBe('');
  });
});

describe('areasAfterDistrictChange', () => {
  it('keeps the areas when a district is added', () => {
    expect(areasAfterDistrictChange(['18'], ['18', '21'], ['26'])).toEqual(['26']);
  });

  it('clears them when a district is removed, since one may be its area', () => {
    expect(areasAfterDistrictChange(['18', '21'], ['21'], ['26'])).toEqual([]);
    expect(areasAfterDistrictChange(['18'], ['21'], ['26'])).toEqual([]);
  });
});

describe('restoreCriteria', () => {
  const empty = { orgUnit: [] as string[], siteId: '', capitalRoad: false };

  it('turns a value saved before the filter became a multi-select into a list of it', () => {
    expect(restoreCriteria(empty, { orgUnit: '18' as unknown as string[] })).toEqual({
      orgUnit: ['18'],
      siteId: '',
      capitalRoad: false,
    });
  });

  it('turns a blank saved value into no picks, and keeps lists and other fields', () => {
    expect(restoreCriteria(empty, { orgUnit: '' as unknown as string[] }).orgUnit).toEqual([]);
    expect(restoreCriteria(empty, { orgUnit: ['18', '21'], siteId: '62-001' })).toEqual({
      orgUnit: ['18', '21'],
      siteId: '62-001',
      capitalRoad: false,
    });
    expect(restoreCriteria(empty, null)).toEqual(empty);
  });
});
