import { describe, expect, it } from 'vitest';

import { inspectionStatusLabel, inspectionStatusTagType, isOffline } from './inspectionStatus';

describe('inspectionStatusTagType', () => {
  it.each([
    ['OFL', 'purple'],
    ['PRO', 'blue'],
    ['SUB', 'teal'],
    ['RVD', 'green'],
    ['REJ', 'red'],
  ])('colours %s as %s', (code, expected) => {
    expect(inspectionStatusTagType(code)).toBe(expected);
  });

  it('colours the dead ACC status the same as RVD', () => {
    // Saving an ACC inspection rewrites it to RVD, and a downstream LRM view reads the two as one.
    // Colouring them differently would suggest a distinction the data does not carry.
    expect(inspectionStatusTagType('ACC')).toBe(inspectionStatusTagType('RVD'));
  });

  it('is grey for a status it does not know, rather than guessing', () => {
    expect(inspectionStatusTagType('WAT')).toBe('gray');
    expect(inspectionStatusTagType('')).toBe('gray');
    expect(inspectionStatusTagType(null)).toBe('gray');
    expect(inspectionStatusTagType(undefined)).toBe('gray');
  });

  it('ignores case and surrounding space', () => {
    expect(inspectionStatusTagType(' ofl ')).toBe('purple');
  });
});

describe('inspectionStatusLabel', () => {
  it('prefers the description', () => {
    expect(inspectionStatusLabel('SUB', 'Submitted')).toBe('Submitted');
  });

  it('falls back to the code, so an undecodable status is not a blank pill', () => {
    expect(inspectionStatusLabel('SUB', '')).toBe('SUB');
    expect(inspectionStatusLabel('SUB', null)).toBe('SUB');
  });

  it('is empty when there is nothing to show', () => {
    expect(inspectionStatusLabel(null, null)).toBe('');
  });
});

describe('isOffline', () => {
  it('recognises the offline status the results table turns on', () => {
    expect(isOffline('OFL')).toBe(true);
    expect(isOffline(' ofl ')).toBe(true);
  });

  it('is false for everything else', () => {
    expect(isOffline('SUB')).toBe(false);
    expect(isOffline('')).toBe(false);
    expect(isOffline(null)).toBe(false);
  });
});
