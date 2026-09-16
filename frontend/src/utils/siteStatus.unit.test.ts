import { describe, expect, it } from 'vitest';

import { siteStatusLabel, siteStatusTagType } from './siteStatus';

describe('siteStatusTagType', () => {
  it.each([
    ['ACT', 'green'],
    ['BAR', 'red'],
    ['PP', 'blue'],
    ['DAC', 'cool-gray'],
    ['ARC', 'cool-gray'],
    ['LRM', 'cool-gray'],
  ] as const)('colours %s as %s', (code, expected) => {
    expect(siteStatusTagType(code)).toBe(expected);
  });

  it('greys a code it does not recognise rather than guessing', () => {
    // Two of the eight rows in CROSSING_SITE_STATUS_CODE are not named anywhere in the legacy
    // source or the schema repo. Grey is the honest answer for those: a readable pill with no
    // claim about what the status means, rather than a colour that might say the opposite.
    expect(siteStatusTagType('WAT')).toBe('gray');
  });

  it('tolerates a missing or oddly-cased code', () => {
    expect(siteStatusTagType(null)).toBe('gray');
    expect(siteStatusTagType(undefined)).toBe('gray');
    expect(siteStatusTagType('')).toBe('gray');
    expect(siteStatusTagType(' act ')).toBe('green');
  });
});

describe('siteStatusLabel', () => {
  it('reads the description', () => {
    expect(siteStatusLabel('ACT', 'Active')).toBe('Active');
  });

  it('falls back to the code when nothing decoded it', () => {
    // The status join is a left join, so a code missing from the table is possible. Showing the
    // code beats an empty pill, which reads as "no status" when the truth is "a status nothing
    // could decode".
    expect(siteStatusLabel('ACT', null)).toBe('ACT');
    expect(siteStatusLabel('ACT', '   ')).toBe('ACT');
  });

  it('is empty when the site genuinely has no status', () => {
    expect(siteStatusLabel(null, null)).toBe('');
  });
});
