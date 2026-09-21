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
  roadSectionId: 'Br.',
} as const satisfies Partial<Record<keyof SiteFormValues, string>>;

const blank = (value: string) => value.trim() === '';

/**
 * Per-field messages: what is missing, and what is the wrong shape.
 *
 * <p>Beside the box rather than at the top of the page. Legacy collects every one of these into a
 * single `ActionMessages.GLOBAL_MESSAGE` list rendered above the form, so a user with three
 * problems reads three sentences and then has to find the three boxes they name.
 *
 * <p>`mode` decides whether the "too little" rules run — see {@link ValidationMode}. Everything
 * here is one rule set either way, so what the form says while the user types can never contradict
 * what it says when they press Save.
 */
export const fieldErrors = (site: SiteFormValues, mode: ValidationMode = 'settled'): SiteErrors => {
  const errors: SiteErrors = {};
  const settled = mode === 'settled';

  // Required: the archetypal "too little" rule. An empty box is where every field starts, so
  // saying so on the first keystroke would mark the form red for filling it in normally.
  if (settled) {
    for (const [field, label] of Object.entries(ALWAYS_REQUIRED)) {
      if (blank(site[field as keyof SiteFormValues] as string)) {
        errors[field as keyof SiteFormValues] = `${label} is required.`;
      }
    }

    // A storage site holds portable structures rather than spanning anything, so it has no
    // crossing to name and no point on a road to measure to.
    const isStorage = site.crossingSiteTypeCode === SITE_TYPE.STORAGE;
    if (!isStorage && blank(site.crossingName)) {
      errors.crossingName = 'Crossing Name is required.';
    }
    if (!isStorage && blank(site.pointOfCommencementDistance)) {
      errors.pointOfCommencementDistance = 'Kilometres is required.';
    }

    // A recreation site is identified by its project, not by a district's road network.
    if (site.crossingSiteTypeCode !== SITE_TYPE.RECREATION && blank(site.orgUnitNo)) {
      errors.orgUnitNo = 'Forest District is required.';
    }

    Object.assign(errors, missingCoordinates(site));
  }

  for (const field of ['pointOfCommencementDistance', 'userKm'] as const) {
    const value = site[field];
    if (blank(value)) continue;
    // "12." is a number half written, so it is left alone until the user moves on. "12.345" and
    // "abc" are not — no further typing rescues either, and the third decimal place is gone the
    // moment it is typed.
    if (!settled && isNumberInProgress(value)) continue;
    if (!KILOMETRE.test(value.trim())) {
      errors[field] = 'Must be a number, e.g. 12.5';
    }
  }

  // Over the column's byte limit. The counter beside the field has been saying so as the user
  // typed; this is what stops the save, because nothing truncates on their behalf.
  for (const [field, limit] of Object.entries(SITE_TEXT_LIMITS)) {
    const message = overLimitError(site[field as keyof SiteFormValues] as string, limit);
    if (message) {
      errors[field as keyof SiteFormValues] = message;
    }
  }

  Object.assign(errors, coordinateErrors(site, mode));
  return errors;
};

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
 * The coordinate boxes.
 *
 * <p><b>Longitude is entered unsigned and stored negative.</b> Every site in the province is west
 * of Greenwich, so legacy negates whatever is typed rather than asking for a minus sign — and
 * rejecting one here would reject the value the database holds.
 *
 * <p>A part is judged only when it is filled. The three boxes of a coordinate are one value, and
 * complaining about the two still empty while the user types the first is noise.
 */
const coordinateErrors = (site: SiteFormValues, mode: ValidationMode): SiteErrors => {
  const errors: SiteErrors = {};
  const ranged = (field: keyof SiteFormValues, pattern: RegExp, max: number, label: string) => {
    const value = (site[field] as string).trim();
    if (value === '') return;
    // A range rule is "too much" and reports as it is typed — 91 degrees of latitude is wrong on
    // the keystroke that makes it 91, not on the one after. Only a half-written number waits.
    if (mode === 'typing' && isNumberInProgress(value)) return;
    if (!pattern.test(value) || Number(value) > max) {
      errors[field] = `${label} must be 0–${max}.`;
    }
  };

  // British Columbia spans roughly 48–60°N and 114–139°W, but the bounds below are the bounds of
  // the notation rather than of the province: a site just outside them is a typo either way, and a
  // provincial boundary check belongs on the server, which can say where the point actually landed.
  ranged('longitudeDegrees', WHOLE, 180, 'Degrees');
  ranged('longitudeMinutes', WHOLE, 59, 'Minutes');
  ranged('longitudeSeconds', SECONDS, 59.99, 'Seconds');
  ranged('latitudeDegrees', WHOLE, 90, 'Degrees');
  ranged('latitudeMinutes', WHOLE, 59, 'Minutes');
  ranged('latitudeSeconds', SECONDS, 59.99, 'Seconds');
  ranged('utmZone', WHOLE, 60, 'UTM Zone');

  for (const field of ['utmEasting', 'utmNorthing'] as const) {
    const value = site[field].trim();
    if (value !== '' && !WHOLE.test(value)) {
      errors[field] = 'Must be a whole number.';
    }
  }
  return errors;
};

/**
 * Rules that no single box is at fault for.
 *
 * <p>All three are legacy's, from `SiteForm.validate`, and all three say the same thing in
 * different words: whether a site gets inspected has to agree with what the site *is* and what
 * state it is in. They are reported at the top of the form because the fix could be either of the
 * two fields involved, and marking one would be picking for the user.
 */
export const crossFieldErrors = (site: SiteFormValues): string[] => {
  const messages: string[] = [];
  const { crossingSiteTypeCode: type, crossingSiteStatusCode: status } = site;
  const inspection = site.structureInspectionStatusCode;

  const isCrossing = type === SITE_TYPE.CROSSING;
  const isStanding: boolean = status === SITE_STATUS.ACTIVE || status === SITE_STATUS.BARRICADED;

  if (isCrossing && isStanding && inspection === INSPECTION_STATUS.DO_NOT_INSPECT) {
    messages.push(
      'A Crossing site that is Active or Barricaded/Closed must have an Inspection Status of ' +
        'Inspect.',
    );
  }

  if (
    isCrossing &&
    UNINSPECTABLE_STATUSES.includes(status) &&
    inspection === INSPECTION_STATUS.INSPECT
  ) {
    messages.push(
      'A Crossing site that is Transferred, Deactivated (closed), Under Construction, Proposed, ' +
        'Archived or LRMOPS must have an Inspection Status of Do Not Inspect.',
    );
  }

  if (type === SITE_TYPE.STORAGE && inspection === INSPECTION_STATUS.INSPECT) {
    messages.push('A Storage site must have an Inspection Status of Do Not Inspect.');
  }

  return messages;
};
