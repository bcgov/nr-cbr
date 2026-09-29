import {
  INSPECTION_STATUS,
  SITE_STATUS,
  SITE_TEXT_LIMITS,
  SITE_TYPE,
  UNINSPECTABLE_STATUSES,
  type SiteFormValues,
} from './types';

import { overLimitError } from '@/utils/textLimits';
import { isNumberInProgress, type ValidationMode } from '@/utils/validation';

export type SiteErrors = Partial<Record<keyof SiteFormValues, string>>;

/**
 * What the cross-field rules need to know that is not on the form.
 *
 * <p>Only one thing so far: whether the site carries structures. Two of legacy's status rules turn
 * on it and neither can be decided from the form alone, because a structure is a separate record
 * the site merely owns.
 */
export type SiteContext = {
  /**
   * Structures standing on this site — `Site.hasStructures()`, which counts only
   * `ACTIVE_IND = 'Y'`.
   *
   * <p>Zero on Add Site, always: a site that does not exist yet owns nothing.
   */
  activeStructureCount?: number;
};

/** A decimal that fits `CROSSING_SITE`'s `NUMBER(8,2)` — six integer digits and two decimals. */
const KILOMETRE = /^\d{1,6}(\.\d{1,2})?$/;

/** Whole numbers, for the coordinate degrees and minutes and for the UTM values. */
const WHOLE = /^\d+$/;

/** Seconds carry a fraction. */
const SECONDS = /^\d{1,2}(\.\d{1,2})?$/;

/**
 * Fields that must be filled in, and what to call them when they are not.
 *
 * <p>Three of them are conditional and so are not listed here: Crossing Name and Kilometres are
 * excused for a storage site, which has neither, and Forest District is excused for a recreation
 * site. Legacy applies the same three exemptions.
 */
const ALWAYS_REQUIRED = {
  siteId: 'Site #',
  crossingSiteStatusCode: 'Status',
  crossingSiteTypeCode: 'Site Type',
  structureInspectionStatusCode: 'Inspection Status',
  forestFileId: 'Project File ID#',
} as const satisfies Partial<Record<keyof SiteFormValues, string>>;

const blank = (value: string) => value.trim() === '';

/**
 * The fields that must be filled in, each judged on its own.
 *
 * <p>Three of them are conditional, and the condition is the site type: a storage site has no
 * crossing to name and no point on a road to measure to, and a recreation site is identified by
 * its project rather than by a district's road network. Legacy applies the same three exemptions.
 */
const requiredErrors = (site: SiteFormValues): SiteErrors => {
  const errors: SiteErrors = {};

  for (const [field, label] of Object.entries(ALWAYS_REQUIRED)) {
    if (blank(site[field as keyof SiteFormValues] as string)) {
      errors[field as keyof SiteFormValues] = `${label} is required.`;
    }
  }

  const conditional: [keyof SiteFormValues, string, boolean][] = [
    ['crossingName', 'Crossing Name', site.crossingSiteTypeCode !== SITE_TYPE.STORAGE],
    ['pointOfCommencementDistance', 'Kilometres', site.crossingSiteTypeCode !== SITE_TYPE.STORAGE],
    ['orgUnitNo', 'Forest District', site.crossingSiteTypeCode !== SITE_TYPE.RECREATION],
    // Asterisked unconditionally in the JSP, but no rule anywhere enforces it and `site.jsp:661`
    // hides its error div for a recreation site — so legacy could not report it there even if one
    // were raised. A "Br." is a branch of a road, and a recreation site has no road. Same call as
    // Kilometres and Crossing Name on a storage site: follow the rule, not the marker.
    ['roadSectionId', 'Br.', site.crossingSiteTypeCode !== SITE_TYPE.RECREATION],
  ];
  for (const [field, label, applies] of conditional) {
    if (applies && blank(site[field] as string)) {
      errors[field] = `${label} is required.`;
    }
  }

  return Object.assign(errors, missingCoordinates(site));
};

/**
 * The two kilometre boxes, which must hold a decimal that fits `NUMBER(8,2)`.
 *
 * <p>`"12."` is a number half written, so it is left alone until the user moves on. `"12.345"` and
 * `"abc"` are not — no further typing rescues either, and the third decimal place is gone the
 * moment it is typed.
 */
const kilometreErrors = (site: SiteFormValues, mode: ValidationMode): SiteErrors => {
  const errors: SiteErrors = {};

  for (const field of ['pointOfCommencementDistance', 'userKm'] as const) {
    const value = site[field].trim();
    const halfWritten = mode === 'typing' && isNumberInProgress(value);
    if (value !== '' && !halfWritten && !KILOMETRE.test(value)) {
      errors[field] = 'Must be a number, e.g. 12.5';
    }
  }

  return errors;
};

/**
 * Free text past the byte limit of the column behind it.
 *
 * <p>The counter beside the field has been saying so as the user typed; this is what stops the
 * save, because nothing truncates on their behalf.
 */
const textLimitErrors = (site: SiteFormValues): SiteErrors => {
  const errors: SiteErrors = {};

  for (const [field, limit] of Object.entries(SITE_TEXT_LIMITS)) {
    const message = overLimitError(site[field as keyof SiteFormValues] as string, limit);
    if (message) {
      errors[field as keyof SiteFormValues] = message;
    }
  }

  return errors;
};

/**
 * Per-field messages: what is missing, and what is the wrong shape.
 *
 * <p>Beside the box rather than at the top of the page. Legacy collects every one of these into a
 * single `ActionMessages.GLOBAL_MESSAGE` list rendered above the form, so a user with three
 * problems reads three sentences and then has to find the three boxes they name.
 *
 * <p>`mode` decides whether the "too little" rules run — see {@link ValidationMode}. Those are the
 * required ones, and they are the whole of what `'typing'` leaves out: an empty box is where every
 * field starts, so saying so on the first keystroke would mark the form red for anyone filling it
 * in normally. Everything here is one rule set either way, so what the form says while the user
 * types can never contradict what it says when they press Save.
 *
 * <p><b>Order is load-bearing.</b> The range rules come last so that a specific complaint wins
 * over a general one on the same box — degrees out of range while seconds is still blank reads as
 * "must be 0–90", not "Latitude is required".
 */
export const fieldErrors = (
  site: SiteFormValues,
  mode: ValidationMode = 'settled',
): SiteErrors => ({
  ...(mode === 'settled' ? requiredErrors(site) : {}),
  ...kilometreErrors(site, mode),
  ...textLimitErrors(site),
  ...coordinateErrors(site, mode),
});

/** Longitude and Latitude, each as its three parts. */
const COORDINATES = [
  ['Longitude', ['longitudeDegrees', 'longitudeMinutes', 'longitudeSeconds']],
  ['Latitude', ['latitudeDegrees', 'latitudeMinutes', 'latitudeSeconds']],
] as const satisfies readonly (readonly [string, readonly (keyof SiteFormValues)[]])[];

/**
 * Both coordinates are mandatory, and so is every part of each.
 *
 * <p>Legacy says so in its own words — `// Making longitude mandatory` above the check in
 * `SiteForm.validate`, and the same again for latitude — and it refuses the save when any one of
 * the three boxes is blank, for every site type. <b>The columns are nullable</b>
 * (`LONGITUDE NUMBER(9,6)`, `LATITUDE NUMBER(8,6)`), so this is an application rule rather than a
 * constraint; the database would take a site with no position at all.
 *
 * <p>The message lands on the degrees box and the other two are marked without repeating it. A
 * coordinate is one value in three boxes, so three copies of the same sentence under three boxes
 * says nothing the first did not — but leaving the empty ones unmarked would hide which of them is
 * missing.
 */
const missingCoordinates = (site: SiteFormValues): SiteErrors => {
  const errors: SiteErrors = {};
  for (const [label, parts] of COORDINATES) {
    if (!parts.some((part) => blank(site[part] as string))) {
      continue;
    }
    parts.forEach((part, index) => {
      errors[part] = index === 0 ? `${label} is required — degrees, minutes and seconds.` : '';
    });
  }
  return errors;
};

/**
 * The coordinate boxes, for the rules that refuse a save.
 *
 * <p><b>Longitude is entered unsigned and stored negative.</b> Every site in the province is west
 * of Greenwich, so legacy negates whatever is typed rather than asking for a minus sign — and
 * rejecting one here would reject the value the database holds.
 *
 * <p>Only two things here are errors, and both are legacy's: a box that is not a number
 * (`errors.numeric` in `SiteForm.validate`) and degrees past the bounds of the notation
 * (`errors.range`, ±180 and ±90). <b>Everything else about a coordinate is a warning</b> — see
 * {@link coordinateWarnings}. Legacy checks minutes and seconds for range in `Site.validate`,
 * which paints the form and does not stop the save.
 *
 * <p>A part is judged only when it is filled. The three boxes of a coordinate are one value, and
 * complaining about the two still empty while the user types the first is noise.
 */
const coordinateErrors = (site: SiteFormValues, mode: ValidationMode): SiteErrors => {
  const errors: SiteErrors = {};

  const numeric = (field: keyof SiteFormValues, pattern: RegExp) => {
    const value = (site[field] as string).trim();
    if (value === '') return false;
    // A malformed number reports as it is typed; only a half-written one waits.
    if (mode === 'typing' && isNumberInProgress(value)) return false;
    if (!pattern.test(value)) {
      errors[field] = 'Must be a number.';
      return false;
    }
    return true;
  };

  const degrees = (field: keyof SiteFormValues, max: number) => {
    if (!numeric(field, WHOLE)) return;
    if (Number((site[field] as string).trim()) > max) {
      errors[field] = `Degrees must be 0–${max}.`;
    }
  };

  degrees('longitudeDegrees', 180);
  degrees('latitudeDegrees', 90);
  numeric('longitudeMinutes', WHOLE);
  numeric('longitudeSeconds', SECONDS);
  numeric('latitudeMinutes', WHOLE);
  numeric('latitudeSeconds', SECONDS);
  numeric('utmZone', WHOLE);
  numeric('utmEasting', WHOLE);
  numeric('utmNorthing', WHOLE);

  return errors;
};

/**
 * What legacy paints in amber and saves anyway.
 *
 * <p>Two families, and the distinction is worth keeping straight. The first is <b>notation</b>:
 * sixty minutes is an hour and sixty seconds is a minute, so a value at or past either is a
 * mis-keying rather than a place. The second is <b>geography</b>: British Columbia spans roughly
 * 48°–60°N and 114°–140°W and sits in UTM zones 8 to 12, so a coordinate outside those is a real
 * point somewhere else in the world — most often a digit transposed, occasionally a site genuinely
 * recorded from the wrong sheet.
 *
 * <p><b>Warnings, not errors, because legacy makes them warnings</b> (`Site.validate`,
 * `validateLongitudeDMS`, `validateLatitudeDMS`). The bounds are advisory by design: a site a
 * kilometre over the Alberta border is a legitimate record, and refusing it would make the form
 * unusable for exactly the crossings whose position is most worth double-checking.
 */
const coordinateWarnings = (site: SiteFormValues): SiteErrors => {
  const warnings: SiteErrors = {};

  const ranged = (field: keyof SiteFormValues, min: number, max: number, message: string) => {
    const value = (site[field] as string).trim();
    if (value === '' || !Number.isFinite(Number(value))) return;
    const magnitude = Math.abs(Number(value));
    if (magnitude < min || magnitude > max) {
      warnings[field] = message;
    }
  };

  // Notation.
  ranged('longitudeMinutes', 0, 59, 'Minutes are usually 0–59.');
  ranged('latitudeMinutes', 0, 59, 'Minutes are usually 0–59.');
  ranged('longitudeSeconds', 0, 59.99, 'Seconds are usually 0–59.9.');
  ranged('latitudeSeconds', 0, 59.99, 'Seconds are usually 0–59.9.');

  // Geography. Longitude is entered unsigned, so these compare magnitudes.
  ranged('longitudeDegrees', 114, 140, 'Outside BC — longitude is usually 114–140.');
  ranged('latitudeDegrees', 48, 60, 'Outside BC — latitude is usually 48–60.');
  ranged('utmZone', 8, 12, 'BC is in UTM zones 8–12.');
  ranged('utmEasting', 250000, 750000, 'Outside BC — easting is usually 250,000–750,000.');
  ranged('utmNorthing', 5300000, 6700000, 'Outside BC — northing is usually 5,300,000–6,700,000.');

  return warnings;
};

/**
 * Everything the form says in amber: a value it will store, and would like looked at first.
 *
 * <p>Warnings never gate Save. That is the whole of what separates them from errors, and it is
 * legacy's line rather than one drawn here — `Site.validate` collects these into the same JSON the
 * live check returns, and `SiteForm.validate`, the only thing that can refuse the save, does not
 * check any of them.
 */
export const fieldWarnings = (site: SiteFormValues): SiteErrors => coordinateWarnings(site);

/**
 * The rules that read two fields at once, as messages on one of them.
 *
 * <p><b>Inline, not a banner.</b> Every validation message on this form lives beside the box it
 * concerns, and these are no exception — legacy agrees, stamping
 * {@code fieldName = "structureInspectionStatusCode"} on all of them in `SiteAction.validate` and
 * writing each into that field's own error div.
 *
 * <p><b>Inspection Status carries them</b>, which is legacy's choice and the practical one: in each
 * rule it is the field that is wrong for the site rather than the site that is wrong for the field.
 *
 * <p><b>Shown live, without waiting for Save.</b> nr-frep draws the line in the same place for its
 * duplicate-label check: a clash between values the user has deliberately chosen is not a field
 * left unfinished, so holding it back until Save means telling them about a choice they made
 * twenty fields ago. Nothing can fire on a half-filled form because every rule below names a value
 * in all three boxes, so all three must already be chosen.
 */
export const crossFieldErrors = (site: SiteFormValues): SiteErrors => {
  const { crossingSiteTypeCode: type, crossingSiteStatusCode: status } = site;
  const inspection = site.structureInspectionStatusCode;

  const isCrossing = type === SITE_TYPE.CROSSING;
  const isStanding: boolean = status === SITE_STATUS.ACTIVE || status === SITE_STATUS.BARRICADED;

  if (isCrossing && isStanding && inspection === INSPECTION_STATUS.DO_NOT_INSPECT) {
    return {
      structureInspectionStatusCode:
        'An Active or Barricaded/Closed Crossing site must be set to Inspect.',
    };
  }

  if (
    isCrossing &&
    UNINSPECTABLE_STATUSES.includes(status) &&
    inspection === INSPECTION_STATUS.INSPECT
  ) {
    return {
      structureInspectionStatusCode:
        'A Crossing site that is Transferred, Deactivated, Under Construction, Proposed, ' +
        'Archived or LRMOPS must be set to Do Not Inspect.',
    };
  }

  if (type === SITE_TYPE.STORAGE && inspection === INSPECTION_STATUS.INSPECT) {
    return { structureInspectionStatusCode: 'A Storage site must be set to Do Not Inspect.' };
  }

  return {};
};

/**
 * The cross-field rule legacy states as a warning rather than an error.
 *
 * <p>A recreation site that is Active but marked Do Not Inspect. The three rules in
 * {@link crossFieldErrors} are about crossings and storage sites, where the pairing is simply
 * contradictory; this one is not. Recreation sites are administered by a different part of the
 * ministry and plenty of them legitimately carry no inspection obligation, so legacy asks the
 * question and takes the answer either way (`SiteAction.java:530-537`, `ServiceManager.WARNING`).
 */
export const crossFieldWarnings = (site: SiteFormValues): SiteErrors => {
  if (
    site.crossingSiteTypeCode === SITE_TYPE.RECREATION &&
    site.crossingSiteStatusCode === SITE_STATUS.ACTIVE &&
    site.structureInspectionStatusCode === INSPECTION_STATUS.DO_NOT_INSPECT
  ) {
    return {
      structureInspectionStatusCode:
        'This Recreation site is Active but not inspected. Check that is intended.',
    };
  }
  return {};
};

/**
 * The two rules that exist only for a site that is already stored.
 *
 * <p><b>Both are unreachable from Add Site, in legacy and here.</b> Each turns on whether the site
 * carries structures, and a site being created carries none — so legacy nests them inside
 * `if (actionType.equals(SiteForm.UPDATE))` (`SiteAction.java:539`, the rules at 613 and 621) and
 * guards the save-gate copy on `site != null`, the stored record (`SiteForm.java:311`). Kept out of
 * {@link crossFieldErrors} and {@link crossFieldWarnings} for that reason rather than passed a zero
 * count from Add Site: a rule that cannot apply should be absent, not defaulted into silence.
 *
 * <p>On Status, which is legacy's field for both and the only thing on the form the user can change
 * to resolve either — the structures themselves are elsewhere.
 *
 * <p>Two tiers. <b>Proposed is an error</b> — the status contradicts a fact about the site, and
 * legacy refuses the save. <b>Deactivated is a warning</b>, and is warning-only:
 * `SiteForm.validate` has no `errors.site.deactivated` at all, so legacy paints it and saves
 * anyway. A closed crossing legitimately keeps its bridge until someone removes it.
 */
export const savedSiteConflicts = (
  site: SiteFormValues,
  context: SiteContext,
): { errors: SiteErrors; warnings: SiteErrors } => {
  const structures = context.activeStructureCount ?? 0;
  if (structures === 0) {
    return { errors: {}, warnings: {} };
  }

  if (site.crossingSiteStatusCode === SITE_STATUS.PROPOSED) {
    return {
      errors: {
        crossingSiteStatusCode:
          `A Proposed site cannot have structures, and this one has ${structures}. ` +
          'Change the status, or archive the structures first.',
      },
      warnings: {},
    };
  }

  if (site.crossingSiteStatusCode === SITE_STATUS.DEACTIVATED) {
    return {
      errors: {},
      warnings: {
        crossingSiteStatusCode: `This site is Deactivated but still has ${structures} structure(s).`,
      },
    };
  }

  return { errors: {}, warnings: {} };
};
