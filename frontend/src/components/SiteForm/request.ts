/**
 * The site form as the create endpoint wants it, and the way back from a refusal.
 *
 * <p>The form holds strings, because that is what a text input holds. The API takes numbers and
 * decimal degrees, because that is what the columns hold. This is the one place the two meet.
 */

import { EMPTY_SITE, type SiteFormValues } from './types';

import type { SiteErrors } from './validation';

import { dmsToDecimal } from '@/utils/coordinates';

/** A new site, as `POST /api/v1/sites` takes it. Field names match the form's. */
export type SiteCreateRequest = {
  siteId: string;
  crossingName: string | null;
  pointOfCommencementDistance: number | null;
  userKm: number | null;
  crossingSiteStatusCode: string | null;
  structureInspectionStatusCode: string | null;
  crossingSiteTypeCode: string | null;
  specialAccessRqmtCode: string | null;
  orgUnitNo: number | null;
  managementOrgUnitNo: number | null;
  businessAreaOrgUnitNo: number | null;
  forestFileId: string | null;
  roadSectionId: string | null;
  clientNumber: string | null;
  clientLocnCode: string | null;
  capitalRoad: boolean;
  /** Decimal degrees, negative. */
  longitude: number | null;
  latitude: number | null;
  utmZone: number | null;
  utmEasting: number | null;
  utmNorthing: number | null;
  pointOfAccessDescription: string | null;
};

const text = (value: string): string | null => (value.trim() === '' ? null : value.trim());

const number = (value: string): number | null => {
  const trimmed = value.trim();
  if (trimmed === '' || !Number.isFinite(Number(trimmed))) return null;
  return Number(trimmed);
};

/**
 * One coordinate's three boxes as a single decimal, or null if any of them is empty.
 *
 * <p>All three or nothing: two thirds of a coordinate is not a position, and sending the partial
 * value would store a point the user never named. The server requires both coordinates anyway, so a
 * null here becomes "Longitude is required" rather than a silently truncated place.
 */
const coordinate = (
  degrees: string,
  minutes: string,
  seconds: string,
  sign: 1 | -1,
): number | null => {
  const parts = [number(degrees), number(minutes), number(seconds)];
  if (parts.some((part) => part === null)) return null;
  return (
    sign *
    Math.abs(
      dmsToDecimal({
        degrees: parts[0] as number,
        minutes: parts[1] as number,
        seconds: parts[2] as number,
      }),
    )
  );
};

/**
 * The form as a request.
 *
 * <p><b>Longitude is negated here</b>, which is where legacy negates it too — `SiteForm.getSiteDTO`
 * flips the sign before anything reaches `INSERT_SITE`. The box is entered unsigned because every
 * site in the province is west of Greenwich, so the minus is a fact about British Columbia rather
 * than something to ask the user for.
 *
 * <p>Empty boxes travel as null rather than as `""`. The distinction is the difference between "not
 * recorded" and "recorded as nothing", and it is the server's job to store the first as `NULL`.
 */
export const toCreateRequest = (site: SiteFormValues): SiteCreateRequest => ({
  siteId: site.siteId.trim().toUpperCase(),
  crossingName: text(site.crossingName),
  pointOfCommencementDistance: number(site.pointOfCommencementDistance),
  userKm: number(site.userKm),
  crossingSiteStatusCode: text(site.crossingSiteStatusCode),
  structureInspectionStatusCode: text(site.structureInspectionStatusCode),
  crossingSiteTypeCode: text(site.crossingSiteTypeCode),
  specialAccessRqmtCode: text(site.specialAccessRqmtCode),
  orgUnitNo: number(site.orgUnitNo),
  managementOrgUnitNo: number(site.managementOrgUnitNo),
  businessAreaOrgUnitNo: number(site.businessAreaOrgUnitNo),
  forestFileId: text(site.forestFileId),
  roadSectionId: text(site.roadSectionId),
  clientNumber: text(site.clientNumber),
  clientLocnCode: text(site.clientLocationCode),
  capitalRoad: site.capitalRoad,
  longitude: coordinate(site.longitudeDegrees, site.longitudeMinutes, site.longitudeSeconds, -1),
  latitude: coordinate(site.latitudeDegrees, site.latitudeMinutes, site.latitudeSeconds, 1),
  utmZone: number(site.utmZone),
  utmEasting: number(site.utmEasting),
  utmNorthing: number(site.utmNorthing),
  pointOfAccessDescription: text(site.pointOfAccessDescription),
});

/** The fields the server can complain about that the form does not hold under the same name. */
const SERVER_ONLY_FIELDS: Record<string, keyof SiteFormValues> = {
  clientLocnCode: 'clientLocationCode',
  longitude: 'longitudeDegrees',
  latitude: 'latitudeDegrees',
};

/**
 * A refused save as messages the form can show beside the right boxes.
 *
 * <p>The server keys its `fieldErrors` by request field name, which is the form's name for all but
 * three: the client location code is spelled differently, and a coordinate is one server field
 * against six boxes here — its message goes on the degrees box, as `missingCoordinates` puts the
 * form's own there.
 *
 * <p><b>Anything unrecognized is dropped</b> rather than guessed at. A message with nowhere to go
 * would otherwise vanish silently *and* leave Save looking like it did nothing; the caller shows
 * the response's own sentence when this comes back empty.
 */
export const toFieldErrors = (fieldErrors: Record<string, string>): SiteErrors => {
  const errors: SiteErrors = {};
  for (const [field, message] of Object.entries(fieldErrors)) {
    const key = SERVER_ONLY_FIELDS[field] ?? field;
    if (key in EMPTY_SITE) {
      errors[key as keyof SiteFormValues] = message;
    }
  }
  return errors;
};
