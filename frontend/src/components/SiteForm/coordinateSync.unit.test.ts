import { describe, expect, it } from 'vitest';

import { fillMissingCoordinates, syncCoordinates } from './coordinateSync';
import { EMPTY_SITE, type SiteFormValues } from './types';

/** A site with one complete longitude/latitude pair and no UTM. Victoria, BC. */
const withLatLon = (overrides: Partial<SiteFormValues> = {}): SiteFormValues => ({
  ...EMPTY_SITE,
  longitudeDegrees: '123',
  longitudeMinutes: '21',
  longitudeSeconds: '56.2',
  latitudeDegrees: '48',
  latitudeMinutes: '25',
  latitudeSeconds: '42.2',
  ...overrides,
});

/** The same point, as UTM. */
const withUtm = (overrides: Partial<SiteFormValues> = {}): SiteFormValues => ({
  ...EMPTY_SITE,
  utmZone: '10',
  utmEasting: '472954',
  utmNorthing: '5363981',
  ...overrides,
});

/**
 * Both notations filled in, and deliberately naming different places.
 *
 * <p>The northing is six hundred kilometres up the coast from the longitude/latitude pair, so any
 * rule that overwrites one from the other is visible rather than a rounding difference.
 */
const withBoth = (): SiteFormValues => ({
  ...withLatLon(),
  utmZone: '10',
  utmEasting: '472954',
  utmNorthing: '5900000',
});

describe('syncCoordinates', () => {
  it('fills UTM when a longitude box is edited', () => {
    const synced = syncCoordinates(withLatLon(), 'longitudeSeconds');

    expect(synced.utmZone).toBe('10');
    expect(Number(synced.utmEasting)).toBeCloseTo(472954, -1);
    expect(Number(synced.utmNorthing)).toBeCloseTo(5363981, -1);
  });

  it('fills longitude and latitude when a UTM box is edited', () => {
    const synced = syncCoordinates(withUtm(), 'utmNorthing');

    expect(synced.longitudeDegrees).toBe('123');
    expect(synced.latitudeDegrees).toBe('48');
    expect(synced.longitudeMinutes).toBe('21');
    expect(synced.latitudeMinutes).toBe('25');
  });

  it('writes longitude back unsigned, which is how the box is entered', () => {
    // Legacy's `Math.abs(...)` in `SiteAction.longlat`. A minus sign in the box would be rejected
    // by the form's own range rule on the next keystroke.
    const synced = syncCoordinates(withUtm(), 'utmZone');

    expect(synced.longitudeDegrees).not.toContain('-');
  });

  it('rounds the seconds it writes to one decimal place', () => {
    // `NumberFormat.setMaximumFractionDigits(1)`. Without it the box fills with sixteen digits of
    // floating-point noise the user then has to read past.
    const synced = syncCoordinates(withUtm(), 'utmEasting');

    expect(synced.longitudeSeconds).toMatch(/^\d+(\.\d)?$/);
    expect(synced.latitudeSeconds).toMatch(/^\d+(\.\d)?$/);
  });

  it('converts in one direction only, so the two rules cannot fight', () => {
    // Both sets are complete and they disagree. Editing a UTM box must rewrite longitude/latitude
    // and leave UTM exactly as typed — legacy picks the direction from `utmUpdate` the same way.
    const synced = syncCoordinates(withBoth(), 'utmNorthing');

    expect(synced.utmNorthing).toBe('5900000');
    // Rewritten from the UTM northing, so no longer the latitude that was typed.
    expect(synced.latitudeDegrees).toBe('53');
  });

  it('changes nothing when the edited set is incomplete', () => {
    // Half-way through typing a coordinate. Rewriting the other notation now would be acting on a
    // point the user has not finished naming.
    const partial = withLatLon({ latitudeSeconds: '' });

    expect(syncCoordinates(partial, 'latitudeSeconds')).toEqual(partial);
  });

  it('changes nothing when a box holds something that is not a number', () => {
    const bad = withUtm({ utmEasting: 'abc' });

    expect(syncCoordinates(bad, 'utmEasting')).toEqual(bad);
  });

  it('ignores a field that is not a coordinate at all', () => {
    const site = withLatLon({ crossingName: 'Deadman Creek' });

    expect(syncCoordinates(site, 'crossingName')).toEqual(site);
  });
});

describe('fillMissingCoordinates', () => {
  it('derives UTM for a record that has only longitude and latitude', () => {
    expect(fillMissingCoordinates(withLatLon()).utmZone).toBe('10');
  });

  it('derives longitude and latitude for a record that has only UTM', () => {
    expect(fillMissingCoordinates(withUtm()).latitudeDegrees).toBe('48');
  });

  it('leaves a record that carries both exactly as it was recorded', () => {
    // Even where the two disagree: the difference is a fact about the record, and overwriting one
    // with the other would destroy it silently.
    const both = withBoth();

    expect(fillMissingCoordinates(both)).toEqual(both);
  });

  it('leaves a record with neither alone', () => {
    expect(fillMissingCoordinates(EMPTY_SITE)).toEqual(EMPTY_SITE);
  });

  it('fills nothing from a partial pair', () => {
    const partial = withLatLon({ longitudeMinutes: '' });

    expect(fillMissingCoordinates(partial)).toEqual(partial);
  });
});
