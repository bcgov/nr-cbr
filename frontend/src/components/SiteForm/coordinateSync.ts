/**
 * Keeping the two coordinate notations on the site form in step.
 *
 * <p>Legacy does this in `setValidateResponse` (`site.jsp:325-337`): after every change it asks
 * whether one pair is complete and the other is either empty or stale, and if so fills the second
 * from the first over AJAX. <b>It is the reason the form can insist on longitude and latitude while
 * leaving UTM optional</b> — a user who works in UTM types three boxes and the six they are
 * required to fill appear.
 *
 * <p>Driven by which box the user touched, exactly as legacy is: `checkItem` sets `longLatUpdate`
 * or `utmUpdate` from the changed field's name, and those two flags decide the direction. Without
 * them the two rules would each undo the other.
 */

import type { SiteFormValues } from './types';

import { decimalToDms, dmsToDecimal, latLonToUtm, utmToLatLon } from '@/utils/coordinates';

/** The six boxes one longitude/latitude pair is spread across. */
export const LAT_LON_FIELDS = [
  'longitudeDegrees',
  'longitudeMinutes',
  'longitudeSeconds',
  'latitudeDegrees',
  'latitudeMinutes',
  'latitudeSeconds',
] as const satisfies readonly (keyof SiteFormValues)[];

/** The three boxes one UTM coordinate is spread across. */
export const UTM_FIELDS = [
  'utmZone',
  'utmEasting',
  'utmNorthing',
] as const satisfies readonly (keyof SiteFormValues)[];

const isBlank = (value: string) => value.trim() === '';

const allBlank = (values: SiteFormValues, fields: readonly (keyof SiteFormValues)[]) =>
  fields.every((field) => isBlank(values[field] as string));

/** Whether every box of a set holds something that parses as a number. */
const allNumeric = (values: SiteFormValues, fields: readonly (keyof SiteFormValues)[]) =>
  fields.every((field) => {
    const value = (values[field] as string).trim();
    return value !== '' && Number.isFinite(Number(value));
  });

const num = (values: SiteFormValues, field: keyof SiteFormValues) =>
  Number((values[field] as string).trim());

/**
 * Seconds as legacy writes them back — at most one decimal place.
 *
 * <p>`NumberFormat.setMaximumFractionDigits(1)` in `SiteAction.longlat`. Trailing zeros go, so a
 * whole number of seconds reads `12` rather than `12.0`, which is what `NumberFormat` does and what
 * the box would hold if a person had typed it.
 */
const formatSeconds = (seconds: number) => String(Math.round(seconds * 10) / 10);

/**
 * The UTM boxes computed from the longitude/latitude boxes.
 *
 * <p>Longitude is entered unsigned and is west of Greenwich, so it is negated on the way in — the
 * same `if (longDec > 0) longDec = -longDec` that opens `SiteAction.utm`.
 */
const utmFromLatLon = (values: SiteFormValues): Partial<SiteFormValues> | null => {
  if (!allNumeric(values, LAT_LON_FIELDS)) return null;

  const longitude = dmsToDecimal({
    degrees: -Math.abs(num(values, 'longitudeDegrees')),
    minutes: num(values, 'longitudeMinutes'),
    seconds: num(values, 'longitudeSeconds'),
  });
  const latitude = dmsToDecimal({
    degrees: num(values, 'latitudeDegrees'),
    minutes: num(values, 'latitudeMinutes'),
    seconds: num(values, 'latitudeSeconds'),
  });

  const utm = latLonToUtm(longitude, latitude);
  if (!utm) return null;

  return {
    utmZone: String(utm.zone),
    utmEasting: String(utm.easting),
    utmNorthing: String(utm.northing),
  };
};

/**
 * The six longitude/latitude boxes computed from the three UTM boxes.
 *
 * <p>Longitude degrees come back unsigned, because that is how the field is entered — legacy's
 * `Math.abs(...)` in `SiteAction.longlat`, and the reason the sign is re-applied above rather than
 * carried in the box.
 */
const latLonFromUtm = (values: SiteFormValues): Partial<SiteFormValues> | null => {
  if (!allNumeric(values, UTM_FIELDS)) return null;

  const point = utmToLatLon({
    zone: num(values, 'utmZone'),
    easting: num(values, 'utmEasting'),
    northing: num(values, 'utmNorthing'),
  });
  if (!point) return null;

  const longitude = decimalToDms(point.longitude);
  const latitude = decimalToDms(point.latitude);

  return {
    longitudeDegrees: String(Math.abs(longitude.degrees)),
    longitudeMinutes: String(Math.abs(longitude.minutes)),
    longitudeSeconds: formatSeconds(longitude.seconds),
    latitudeDegrees: String(latitude.degrees),
    latitudeMinutes: String(latitude.minutes),
    latitudeSeconds: formatSeconds(latitude.seconds),
  };
};

const isLatLonField = (field: keyof SiteFormValues) =>
  (LAT_LON_FIELDS as readonly string[]).includes(field);

const isUtmField = (field: keyof SiteFormValues) =>
  (UTM_FIELDS as readonly string[]).includes(field);

/**
 * The form after a coordinate box changed, with the other notation brought up to date.
 *
 * <p>One direction only, chosen by which box was edited: touching longitude or latitude recomputes
 * UTM, touching UTM recomputes longitude and latitude. Anything else is returned untouched.
 *
 * <p><b>An incomplete or unparseable set converts to nothing and changes nothing.</b> A user
 * half-way through typing a northing has not asked for their latitude to be rewritten, and the
 * boxes they have already filled must not be cleared because the one in hand is not finished yet.
 */
export const syncCoordinates = (
  values: SiteFormValues,
  changed: keyof SiteFormValues,
): SiteFormValues => {
  const derived = isLatLonField(changed)
    ? utmFromLatLon(values)
    : isUtmField(changed)
      ? latLonFromUtm(values)
      : null;

  return derived ? { ...values, ...derived } : values;
};

/**
 * The form as it should first appear, with a missing notation filled in from the one that is there.
 *
 * <p>Legacy runs the same check on load — `onInitialLoad` calls `validate()`, which lands in
 * `setValidateResponse` and fills whichever side is empty, then sets `contentChanged = false` so
 * the fill does not count as an edit. Applying it to the values a page starts from rather than
 * after mounting keeps that second half true here: the derived boxes are part of the baseline, so
 * opening a record and closing it again is not "unsaved changes".
 *
 * <p>Fills only a side that is <em>entirely</em> empty. A stored site with both notations keeps
 * both exactly as they were recorded, even where they disagree — the difference is a fact about
 * the record, and silently rewriting it would destroy it.
 */
export const fillMissingCoordinates = (values: SiteFormValues): SiteFormValues => {
  if (allBlank(values, UTM_FIELDS)) {
    const utm = utmFromLatLon(values);
    if (utm) return { ...values, ...utm };
  }
  if (allBlank(values, LAT_LON_FIELDS)) {
    const latLon = latLonFromUtm(values);
    if (latLon) return { ...values, ...latLon };
  }
  return values;
};
