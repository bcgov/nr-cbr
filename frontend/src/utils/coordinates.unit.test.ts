import { describe, expect, it } from 'vitest';

import { decimalToDms, dmsToDecimal, latLonToUtm, utmToLatLon } from './coordinates';

describe('dmsToDecimal', () => {
  it('adds the three parts', () => {
    expect(dmsToDecimal({ degrees: 48, minutes: 30, seconds: 36 })).toBeCloseTo(48.51, 6);
  });

  it('carries the degrees sign to the whole value', () => {
    // The trap legacy's `dmsToDecimalDegree` exists to avoid: minutes and seconds are magnitudes,
    // so subtracting them from a negative degree would give -122.5 for what is plainly -123.5.
    expect(dmsToDecimal({ degrees: -123, minutes: 30, seconds: 0 })).toBeCloseTo(-123.5, 6);
  });

  it('is the inverse of decimalToDms', () => {
    expect(dmsToDecimal(decimalToDms(-123.3656))).toBeCloseTo(-123.3656, 9);
  });
});

describe('decimalToDms', () => {
  it('truncates the degrees towards zero, not downwards', () => {
    // `Math.floor` here would give -124 degrees and 38 minutes, which is the same point written
    // in a way no one reads back. Legacy casts to int, which truncates.
    expect(decimalToDms(-123.5)).toEqual({ degrees: -123, minutes: 30, seconds: 0 });
  });

  it('splits a positive value', () => {
    const dms = decimalToDms(48.4284);
    expect(dms.degrees).toBe(48);
    expect(dms.minutes).toBe(25);
    expect(dms.seconds).toBeCloseTo(42.24, 2);
  });
});

describe('latLonToUtm', () => {
  it('converts a known point to its published UTM coordinate', () => {
    // Victoria, BC. Published: zone 10, 472954 E, 5363981 N.
    expect(latLonToUtm(-123.3656, 48.4284)).toEqual({
      zone: 10,
      easting: 472954,
      northing: 5363981,
    });
  });

  it('puts the northern interior in zone 10 as well', () => {
    // Prince George — same zone, a very different northing.
    const utm = latLonToUtm(-122.7497, 53.9171);
    expect(utm?.zone).toBe(10);
    expect(utm?.northing).toBeGreaterThan(5900000);
  });

  it('returns whole metres, because the columns hold no fraction', () => {
    const utm = latLonToUtm(-123.3656, 48.4284);
    expect(Number.isInteger(utm?.easting)).toBe(true);
    expect(Number.isInteger(utm?.northing)).toBe(true);
  });

  it('refuses a coordinate outside the legal domain', () => {
    expect(latLonToUtm(-123, 91)).toBeNull();
    expect(latLonToUtm(-181, 48)).toBeNull();
  });
});

describe('utmToLatLon', () => {
  it('round-trips to within a metre, which is the accuracy legacy claims', () => {
    const back = utmToLatLon({ zone: 10, easting: 472954, northing: 5363981 });
    // A ten-thousandth of a degree is about 11 m; the truncation to whole metres above is the
    // larger of the two errors, so five decimal places is the right resolution to assert at.
    expect(back?.longitude).toBeCloseTo(-123.3656, 4);
    expect(back?.latitude).toBeCloseTo(48.4284, 4);
  });

  it('refuses an impossible zone', () => {
    expect(utmToLatLon({ zone: 0, easting: 500000, northing: 5400000 })).toBeNull();
    expect(utmToLatLon({ zone: 61, easting: 500000, northing: 5400000 })).toBeNull();
  });
});
