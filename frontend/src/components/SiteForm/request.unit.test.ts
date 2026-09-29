import { describe, expect, it } from 'vitest';

import { toCreateRequest, toFieldErrors } from './request';
import { EMPTY_SITE, type SiteFormValues } from './types';

const site = (overrides: Partial<SiteFormValues> = {}): SiteFormValues => ({
  ...EMPTY_SITE,
  siteId: 'BOWRON-001',
  crossingSiteStatusCode: 'ACT',
  crossingSiteTypeCode: 'CRS',
  structureInspectionStatusCode: 'INS',
  forestFileId: 'R00123',
  roadSectionId: '01',
  orgUnitNo: '18',
  crossingName: 'Deadman Creek',
  pointOfCommencementDistance: '12.50',
  longitudeDegrees: '122',
  longitudeMinutes: '30',
  longitudeSeconds: '15.5',
  latitudeDegrees: '53',
  latitudeMinutes: '55',
  latitudeSeconds: '0',
  ...overrides,
});

describe('toCreateRequest', () => {
  it('negates the longitude, which the box holds unsigned', () => {
    // `SiteForm.getSiteDTO` flips the sign before anything reaches INSERT_SITE. Every site in the
    // province is west of Greenwich, so the minus is a fact rather than something to ask for.
    expect(toCreateRequest(site()).longitude).toBeCloseTo(-122.504306, 6);
  });

  it('leaves the latitude positive', () => {
    expect(toCreateRequest(site()).latitude).toBeCloseTo(53.916667, 6);
  });

  it('sends a coordinate as null unless all three of its boxes are filled', () => {
    // Two thirds of a coordinate is not a position. Sending the partial value would store a point
    // the user never named; null becomes "Longitude is required", which is the truth.
    expect(toCreateRequest(site({ longitudeSeconds: '' })).longitude).toBeNull();
  });

  it('upper-cases the site number, as the server and the procedure both do', () => {
    expect(toCreateRequest(site({ siteId: 'bowron-001' })).siteId).toBe('BOWRON-001');
  });

  it('sends an empty optional box as null rather than as an empty string', () => {
    // "Not recorded" and "recorded as nothing" are different answers everywhere else in CBR.
    const request = toCreateRequest(site({ userKm: '', pointOfAccessDescription: '  ' }));

    expect(request.userKm).toBeNull();
    expect(request.pointOfAccessDescription).toBeNull();
  });

  it('sends the numbers as numbers', () => {
    const request = toCreateRequest(site({ userKm: '13.25', utmZone: '10' }));

    expect(request.pointOfCommencementDistance).toBe(12.5);
    expect(request.userKm).toBe(13.25);
    expect(request.utmZone).toBe(10);
    expect(request.orgUnitNo).toBe(18);
  });

  it('carries the client location code under the name the API uses', () => {
    const request = toCreateRequest(site({ clientNumber: '00001012', clientLocationCode: '01' }));

    expect(request.clientNumber).toBe('00001012');
    expect(request.clientLocnCode).toBe('01');
  });

  it('does not send the maintainer label, which exists only for the screen', () => {
    const request = toCreateRequest(site({ maintainerLabel: 'ACME · VANCOUVER' }));

    expect(request).not.toHaveProperty('maintainerLabel');
  });
});

describe('toFieldErrors', () => {
  it('keeps a message whose field the form holds under the same name', () => {
    expect(toFieldErrors({ crossingName: 'Crossing Name is required.' })).toEqual({
      crossingName: 'Crossing Name is required.',
    });
  });

  it('puts a coordinate message on the degrees box', () => {
    // One server field against six boxes here. The degrees box is where the form puts its own
    // coordinate message, so the two cannot end up in different places.
    expect(toFieldErrors({ longitude: 'Longitude is required.' })).toEqual({
      longitudeDegrees: 'Longitude is required.',
    });
    expect(toFieldErrors({ latitude: 'Latitude is required.' })).toEqual({
      latitudeDegrees: 'Latitude is required.',
    });
  });

  it('translates the client location code back to the form spelling', () => {
    expect(toFieldErrors({ clientLocnCode: 'Bad code.' })).toEqual({
      clientLocationCode: 'Bad code.',
    });
  });

  it('drops a field the form does not have rather than guessing', () => {
    // A message with nowhere to go would vanish silently and leave Save looking inert. The caller
    // shows the response's own sentence when nothing survives this.
    expect(toFieldErrors({ roadSegmentId: 'Unknown segment.' })).toEqual({});
  });

  it('keeps every message it can place', () => {
    expect(
      toFieldErrors({ siteId: 'Taken.', crossingName: 'Required.', nope: 'Dropped.' }),
    ).toEqual({ siteId: 'Taken.', crossingName: 'Required.' });
  });
});
