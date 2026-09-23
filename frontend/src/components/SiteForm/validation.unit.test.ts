import { describe, expect, it } from 'vitest';

import { EMPTY_SITE, type SiteFormValues } from './types';
import {
  crossFieldErrors,
  crossFieldWarnings,
  fieldErrors,
  fieldWarnings,
  savedSiteConflicts,
} from './validation';

/** A site with every required field filled, so each case can break exactly one thing. */
const valid = (overrides: Partial<SiteFormValues> = {}): SiteFormValues => ({
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
  // Both coordinates, in full. Legacy requires all six boxes of them — see `missingCoordinates`.
  longitudeDegrees: '122',
  longitudeMinutes: '30',
  longitudeSeconds: '15.5',
  latitudeDegrees: '53',
  latitudeMinutes: '55',
  latitudeSeconds: '0',
  ...overrides,
});

describe('fieldErrors', () => {
  it('passes a complete site', () => {
    expect(fieldErrors(valid())).toEqual({});
  });

  it.each([
    ['siteId', 'Site #'],
    ['crossingSiteStatusCode', 'Status'],
    ['crossingSiteTypeCode', 'Site Type'],
    ['structureInspectionStatusCode', 'Inspection Status'],
    ['forestFileId', 'Project File ID#'],
    ['roadSectionId', 'Br.'],
  ])('requires %s', (field, label) => {
    const errors = fieldErrors(valid({ [field]: '' } as Partial<SiteFormValues>));

    expect(errors[field as keyof SiteFormValues]).toBe(`${label} is required.`);
  });

  it('treats whitespace as blank', () => {
    expect(fieldErrors(valid({ siteId: '   ' })).siteId).toBe('Site # is required.');
  });

  describe('a storage site', () => {
    // It holds portable structures rather than spanning anything, so it has no crossing to name
    // and no point on a road to measure to. Legacy exempts both in SiteForm.validate.
    it('needs neither a crossing name nor a kilometre mark', () => {
      const storage = valid({
        crossingSiteTypeCode: 'STRG',
        structureInspectionStatusCode: 'DNI',
        crossingName: '',
        pointOfCommencementDistance: '',
      });

      expect(fieldErrors(storage)).toEqual({});
    });
  });

  describe('a recreation site', () => {
    it('needs no forest district', () => {
      // It is identified by its project rather than by a district's road network.
      expect(fieldErrors(valid({ crossingSiteTypeCode: 'REC', orgUnitNo: '' }))).toEqual({});
    });

    it('needs no Br.', () => {
      // A "Br." is a branch of a road and a recreation site has no road. Asterisked unconditionally
      // in the JSP, but no rule enforces it and `site.jsp:661` hides its error div for REC — so
      // legacy could not report it there even if one were raised.
      expect(fieldErrors(valid({ crossingSiteTypeCode: 'REC', roadSectionId: '' }))).toEqual({});
    });

    it('still needs its project file, which narrows the district list', () => {
      expect(
        fieldErrors(valid({ crossingSiteTypeCode: 'REC', forestFileId: '' })).forestFileId,
      ).toBe('Project File ID# is required.');
    });
  });

  it('requires Br. for every other site type', () => {
    expect(
      fieldErrors(valid({ crossingSiteTypeCode: 'CRS', roadSectionId: '' })).roadSectionId,
    ).toBe('Br. is required.');
    expect(
      fieldErrors(
        valid({
          crossingSiteTypeCode: 'STRG',
          structureInspectionStatusCode: 'DNI',
          roadSectionId: '',
        }),
      ).roadSectionId,
    ).toBe('Br. is required.');
  });

  it('requires a crossing name and kilometres for every other site type', () => {
    const errors = fieldErrors(
      valid({ crossingSiteTypeCode: 'CRS', crossingName: '', pointOfCommencementDistance: '' }),
    );

    expect(errors.crossingName).toBe('Crossing Name is required.');
    expect(errors.pointOfCommencementDistance).toBe('Kilometres is required.');
  });

  it('requires a forest district for every other site type', () => {
    expect(fieldErrors(valid({ crossingSiteTypeCode: 'CRS', orgUnitNo: '' })).orgUnitNo).toBe(
      'Forest District is required.',
    );
  });

  describe('the kilometre boxes', () => {
    it('accept a decimal that fits NUMBER(8,2)', () => {
      expect(fieldErrors(valid({ pointOfCommencementDistance: '999999.99' }))).toEqual({});
      expect(fieldErrors(valid({ userKm: '0.5' }))).toEqual({});
    });

    it('reject anything that is not one', () => {
      // Narrower than legacy, which accepts whatever `new Double(...)` parses — `1e3`, a leading
      // minus, and values far too large for the column all pass its check and then match nothing.
      expect(fieldErrors(valid({ pointOfCommencementDistance: 'abc' }))).toHaveProperty(
        'pointOfCommencementDistance',
      );
      expect(fieldErrors(valid({ userKm: '1e3' }))).toHaveProperty('userKm');
      expect(fieldErrors(valid({ userKm: '-5' }))).toHaveProperty('userKm');
      expect(fieldErrors(valid({ userKm: '12.345' }))).toHaveProperty('userKm');
    });

    it('leave User Kilometres alone when it is empty, because it is optional', () => {
      expect(fieldErrors(valid({ userKm: '' })).userKm).toBeUndefined();
    });
  });

  describe('the coordinates', () => {
    // Legacy's own comment above the check reads "// Making longitude mandatory", and the same for
    // latitude. The columns are nullable, so this is an application rule rather than a constraint.
    it.each([
      ['Longitude', 'longitudeDegrees'],
      ['Longitude', 'longitudeMinutes'],
      ['Longitude', 'longitudeSeconds'],
      ['Latitude', 'latitudeDegrees'],
      ['Latitude', 'latitudeMinutes'],
      ['Latitude', 'latitudeSeconds'],
    ])('require %s, and refuse it with %s missing', (label, field) => {
      const errors = fieldErrors(valid({ [field]: '' } as Partial<SiteFormValues>));
      const first = label === 'Longitude' ? 'longitudeDegrees' : 'latitudeDegrees';

      expect(errors[first as keyof SiteFormValues]).toBe(
        `${label} is required — degrees, minutes and seconds.`,
      );
    });

    it('marks the other two parts without repeating the sentence', () => {
      // A coordinate is one value in three boxes. Three copies of the message says nothing the
      // first did not; leaving the empty boxes unmarked would hide which is missing.
      const errors = fieldErrors(valid({ latitudeMinutes: '', latitudeSeconds: '' }));

      expect(errors.latitudeDegrees).toContain('Latitude is required');
      expect(errors).toHaveProperty('latitudeMinutes', '');
      expect(errors).toHaveProperty('latitudeSeconds', '');
    });

    it('requires them of a storage site too, which legacy exempts from nothing here', () => {
      const storage = valid({
        crossingSiteTypeCode: 'STRG',
        structureInspectionStatusCode: 'DNI',
        crossingName: '',
        pointOfCommencementDistance: '',
        longitudeDegrees: '',
      });

      expect(storage).toBeDefined();
      expect(fieldErrors(storage).longitudeDegrees).toContain('Longitude is required');
    });

    it('does not nag while the user is still typing them', () => {
      // "Too little" — the coordinate may yet be finished, so it waits for Save like every other
      // required field.
      expect(fieldErrors(valid({ longitudeSeconds: '' }), 'typing')).toEqual({});
    });

    it('lets a range message win over the required one on the same box', () => {
      // Both could apply at once: degrees out of range while seconds is still blank. The range is
      // the more specific complaint and the one the user can act on.
      const errors = fieldErrors(valid({ latitudeDegrees: '91', latitudeSeconds: '' }));

      expect(errors.latitudeDegrees).toBe('Degrees must be 0–90.');
    });

    it('accept a value inside the notation', () => {
      expect(
        fieldErrors(
          valid({
            longitudeDegrees: '122',
            longitudeMinutes: '30',
            longitudeSeconds: '15.5',
            latitudeDegrees: '53',
            latitudeMinutes: '55',
            latitudeSeconds: '0',
            utmZone: '10',
            utmEasting: '532000',
            utmNorthing: '5975000',
          }),
        ),
      ).toEqual({});
    });

    it('lets minutes and seconds of 60 or more through, as legacy does', () => {
      // Out of range for the notation, but `Site.validateLatitudeDMS` files it as a WARNING and
      // `SiteForm.validate` — the only thing that can refuse the save — never looks at it. The
      // amber message is asserted in the fieldWarnings block below.
      expect(fieldErrors(valid({ latitudeMinutes: '60' })).latitudeMinutes).toBeUndefined();
      expect(fieldErrors(valid({ latitudeSeconds: '60' })).latitudeSeconds).toBeUndefined();
    });

    it('still rejects a part that is not a number at all', () => {
      // `errors.numeric` in `SiteForm.validate`, which does refuse the save.
      expect(fieldErrors(valid({ latitudeMinutes: 'abc' }))).toHaveProperty('latitudeMinutes');
      expect(fieldErrors(valid({ utmEasting: 'abc' }))).toHaveProperty('utmEasting');
    });

    it('accept an unsigned longitude, which is how it is entered', () => {
      // Every site in the province is west of Greenwich, so the value is negated when stored —
      // rejecting an unsigned number here would reject what the database holds.
      expect(fieldErrors(valid({ longitudeDegrees: '122' })).longitudeDegrees).toBeUndefined();
    });

    it('judge a part only once it is filled', () => {
      // The three boxes are one value; complaining about the other two while the user types the
      // first is noise.
      expect(fieldErrors(valid({ latitudeDegrees: '53' }))).toEqual({});
    });
  });
});

describe("fieldErrors in 'typing' mode", () => {
  // Only the rules no further typing can satisfy. Everything else waits, because a form filled in
  // the ordinary way passes through every one of those states on the way to being correct.

  it('says nothing about a field that is merely still empty', () => {
    expect(fieldErrors({ ...EMPTY_SITE }, 'typing')).toEqual({});
  });

  it('leaves a number the user is part-way through writing alone', () => {
    expect(fieldErrors(valid({ pointOfCommencementDistance: '12.' }), 'typing')).toEqual({});
    expect(fieldErrors(valid({ userKm: '' }), 'typing')).toEqual({});
  });

  it('reports a value no further typing can rescue, immediately', () => {
    // "abc" is not a number and never will be; the third decimal is gone the moment it is typed.
    expect(fieldErrors(valid({ userKm: 'abc' }), 'typing')).toHaveProperty('userKm');
    expect(fieldErrors(valid({ userKm: '12.345' }), 'typing')).toHaveProperty('userKm');
  });

  it('reports a coordinate out of range as it is typed', () => {
    // 91 degrees of latitude is wrong on the keystroke that makes it 91, not on the one after.
    expect(fieldErrors(valid({ latitudeDegrees: '91' }), 'typing')).toHaveProperty(
      'latitudeDegrees',
    );
  });

  it('reports a value past the column limit as it is typed', () => {
    expect(
      fieldErrors(valid({ pointOfAccessDescription: 'a'.repeat(256) }), 'typing'),
    ).toHaveProperty('pointOfAccessDescription');
  });

  it('agrees with settled mode about everything it does report', () => {
    // The two run one rule set, so they can never disagree about what a field allows — typing
    // simply says less.
    const site = valid({ userKm: 'abc', latitudeMinutes: '75' });
    const typing = fieldErrors(site, 'typing');

    expect(fieldErrors(site, 'settled')).toMatchObject(typing);
  });
});

describe('crossFieldErrors', () => {
  // All three are SiteForm.validate's, and all three say the same thing differently: whether a site
  // gets inspected has to agree with what it is and what state it is in.

  it.each(['ACT', 'BAR'])('refuses a standing crossing (%s) marked Do Not Inspect', (status) => {
    const messages = crossFieldErrors(
      valid({
        crossingSiteTypeCode: 'CRS',
        crossingSiteStatusCode: status,
        structureInspectionStatusCode: 'DNI',
      }),
    );

    // On Inspection Status, which is legacy's field for all three rules.
    expect(Object.keys(messages)).toEqual(['structureInspectionStatusCode']);
    expect(messages.structureInspectionStatusCode).toContain('must be set to Inspect');
  });

  it.each(['TRN', 'DAC', 'UCON', 'PP', 'ARC', 'LRM'])(
    'refuses a crossing that is %s but marked Inspect',
    (status) => {
      const messages = crossFieldErrors(
        valid({
          crossingSiteTypeCode: 'CRS',
          crossingSiteStatusCode: status,
          structureInspectionStatusCode: 'INS',
        }),
      );

      expect(Object.keys(messages)).toEqual(['structureInspectionStatusCode']);
      expect(messages.structureInspectionStatusCode).toContain('Do Not Inspect');
    },
  );

  it('refuses a storage site marked Inspect', () => {
    const messages = crossFieldErrors(
      valid({ crossingSiteTypeCode: 'STRG', structureInspectionStatusCode: 'INS' }),
    );

    expect(Object.keys(messages)).toEqual(['structureInspectionStatusCode']);
    expect(messages.structureInspectionStatusCode).toContain('Storage site');
  });

  it('allows the combinations that agree', () => {
    expect(
      crossFieldErrors(
        valid({ crossingSiteStatusCode: 'ACT', structureInspectionStatusCode: 'INS' }),
      ),
    ).toEqual({});
    expect(
      crossFieldErrors(
        valid({ crossingSiteStatusCode: 'DAC', structureInspectionStatusCode: 'DNI' }),
      ),
    ).toEqual({});
    expect(
      crossFieldErrors(
        valid({ crossingSiteTypeCode: 'STRG', structureInspectionStatusCode: 'DNI' }),
      ),
    ).toEqual({});
  });

  it('says nothing about a recreation site, which legacy leaves to the user', () => {
    // The only rule mentioning REC in legacy is about *Active* recreation sites and lives on the
    // server, where it can see the structures. Nothing on this form may invent one.
    expect(
      crossFieldErrors(
        valid({ crossingSiteTypeCode: 'REC', structureInspectionStatusCode: 'DNI' }),
      ),
    ).toEqual({});
  });
});

describe('fieldWarnings', () => {
  it('says nothing about a site inside British Columbia', () => {
    expect(fieldWarnings(valid())).toEqual({});
  });

  it.each([
    ['latitudeMinutes', '60'],
    ['longitudeMinutes', '75'],
    ['latitudeSeconds', '60'],
    ['longitudeSeconds', '61.5'],
  ])('flags %s of %s as outside the notation', (field, value) => {
    expect(fieldWarnings(valid({ [field]: value }))).toHaveProperty(field);
  });

  it.each([
    ['longitudeDegrees', '95'],
    ['longitudeDegrees', '150'],
    ['latitudeDegrees', '40'],
    ['latitudeDegrees', '70'],
  ])('flags %s of %s as outside the province', (field, value) => {
    expect(fieldWarnings(valid({ [field]: value }))).toHaveProperty(field);
  });

  it.each([
    ['utmZone', '7'],
    ['utmZone', '13'],
    ['utmEasting', '100000'],
    ['utmEasting', '900000'],
    ['utmNorthing', '5000000'],
    ['utmNorthing', '7000000'],
  ])('flags a UTM %s of %s', (field, value) => {
    expect(fieldWarnings(valid({ [field]: value }))).toHaveProperty(field);
  });

  it('accepts a UTM coordinate inside the province', () => {
    expect(
      fieldWarnings(valid({ utmZone: '10', utmEasting: '472954', utmNorthing: '5363981' })),
    ).toEqual({});
  });

  it('never blocks the save — warnings and errors are disjoint here', () => {
    // The point of the tier: every field this marks is a field `fieldErrors` leaves alone.
    const outside = valid({ latitudeDegrees: '70', utmZone: '7', longitudeMinutes: '75' });
    expect(fieldWarnings(outside)).not.toEqual({});
    expect(fieldErrors(outside)).toEqual({});
  });

  it('leaves an empty box alone', () => {
    // UTM is optional, and an untouched form must not open covered in amber.
    expect(fieldWarnings(valid({ utmZone: '', utmEasting: '', utmNorthing: '' }))).toEqual({});
  });
});

describe('crossFieldWarnings', () => {
  it('asks about an active recreation site that is not inspected', () => {
    expect(
      crossFieldWarnings(
        valid({
          crossingSiteTypeCode: 'REC',
          crossingSiteStatusCode: 'ACT',
          structureInspectionStatusCode: 'DNI',
        }),
      ),
    ).toHaveProperty('structureInspectionStatusCode');
  });

  it('says nothing when the recreation site is inspected', () => {
    expect(
      crossFieldWarnings(
        valid({
          crossingSiteTypeCode: 'REC',
          crossingSiteStatusCode: 'ACT',
          structureInspectionStatusCode: 'INS',
        }),
      ),
    ).toEqual({});
  });

  it('does not stop the save, unlike the equivalent rule for a crossing', () => {
    const site = valid({
      crossingSiteTypeCode: 'REC',
      crossingSiteStatusCode: 'ACT',
      structureInspectionStatusCode: 'DNI',
    });
    expect(crossFieldErrors(site)).toEqual({});
  });
});

describe('savedSiteConflicts', () => {
  it('refuses a Proposed site that already has structures', () => {
    const conflicts = savedSiteConflicts(valid({ crossingSiteStatusCode: 'PP' }), {
      activeStructureCount: 2,
    });

    expect(conflicts.errors.crossingSiteStatusCode).toContain('2');
    expect(conflicts.warnings).toEqual({});
  });

  it('only warns about a Deactivated site that still has them', () => {
    // `SiteForm.validate` has no `errors.site.deactivated` at all — legacy paints this and saves.
    // A closed crossing legitimately keeps its bridge until someone removes it.
    const conflicts = savedSiteConflicts(valid({ crossingSiteStatusCode: 'DAC' }), {
      activeStructureCount: 1,
    });

    expect(conflicts.errors).toEqual({});
    expect(conflicts.warnings).toHaveProperty('crossingSiteStatusCode');
  });

  it('says nothing when the site carries no structures', () => {
    expect(savedSiteConflicts(valid({ crossingSiteStatusCode: 'PP' }), {})).toEqual({
      errors: {},
      warnings: {},
    });
  });

  it('says nothing for any other status', () => {
    expect(
      savedSiteConflicts(valid({ crossingSiteStatusCode: 'ACT' }), { activeStructureCount: 3 }),
    ).toEqual({ errors: {}, warnings: {} });
  });

  it('is not reachable from the rules Add Site runs', () => {
    // The whole point of keeping these separate. Add Site calls only crossFieldErrors and
    // crossFieldWarnings, and neither takes a structure count — so a rule legacy nests inside
    // `if (actionType.equals(UPDATE))` cannot fire on a create here either.
    const proposed = valid({ crossingSiteStatusCode: 'PP', structureInspectionStatusCode: 'DNI' });

    expect(crossFieldErrors(proposed)).toEqual({});
    expect(crossFieldWarnings(proposed)).toEqual({});
  });
});
