import { fillMissingCoordinates } from '@/components/SiteForm/coordinateSync';
import { EMPTY_SITE, SITE_TYPE, type SiteFormValues } from '@/components/SiteForm/types';

/** One crossing site as `/api/v1/sites/{siteId}` returns it. Stored values, not display values. */
export type SiteDetailResponse = {
  siteId: string;
  crossingName: string | null;
  pointOfCommencementDistance: string | number | null;
  userKm: string | number | null;
  crossingSiteStatusCode: string | null;
  structureInspectionStatusCode: string | null;
  crossingSiteTypeCode: string | null;
  specialAccessRqmtCode: string | null;
  orgUnitNo: number | null;
  managementOrgUnitNo: number | null;
  businessAreaOrgUnitNo: number | null;
  forestFileId: string | null;
  roadSectionId: string | null;
  forestServiceRoad: string | null;
  /** Structures standing on the site — what the Proposed and Deactivated rules turn on. */
  activeStructureCount: number;
  /** Those structures' ids, oldest first — where Display Structures goes. */
  activeStructureIds: string[];
  clientNumber: string | null;
  clientLocnCode: string | null;
  maintainerLabel: string | null;
  capitalRoad: boolean;
  /** Decimal degrees. Negative — every site in the province is west of Greenwich. */
  longitude: string | number | null;
  latitude: string | number | null;
  utmZone: number | null;
  utmEasting: number | null;
  utmNorthing: number | null;
  pointOfAccessDescription: string | null;
};

/** A stored value as the server sends it: text, a number, or nothing. */
type StoredValue = string | number | null | undefined;

const blank = (value: StoredValue) =>
  value === null || value === undefined || String(value).trim() === '';

/**
 * Whether a structure may be added to the site — legacy's three checks on Site Detail's Add
 * Structure button, which the server repeats on save: the site is complete (legacy's
 * `Site.isComplete()`: status, inspection status, district and type set, and a crossing name and
 * kilometres unless it is a storage site), and it is not Proposed or closed (deactivated).
 */
export const canAddStructureTo = (site: SiteDetailResponse): boolean => {
  if (
    blank(site.crossingSiteStatusCode) ||
    blank(site.structureInspectionStatusCode) ||
    site.orgUnitNo === null ||
    blank(site.crossingSiteTypeCode)
  ) {
    return false;
  }
  const complete =
    site.crossingSiteTypeCode === SITE_TYPE.STORAGE ||
    (!blank(site.crossingName) && !blank(site.pointOfCommencementDistance));
  return complete && !['PP', 'DAC'].includes(site.crossingSiteStatusCode ?? '');
};

const asText = (value: StoredValue): string =>
  value === null || value === undefined ? '' : String(value);

/**
 * Decimal degrees split into the three boxes the form holds.
 *
 * <p><b>Unsigned.</b> The sign carries no information a British Columbian site can vary — they are
 * all west and all north — and the form asks for degrees, minutes and seconds without one. Legacy
 * splits the same way, in `SiteForm.setDto`, and negates again on the way back.
 *
 * <p>Seconds keep two decimals, which is roughly 30 mm on the ground and the precision the column
 * holds: `NUMBER(9,6)` degrees is a shade over a tenth of a second.
 */
export const toDms = (
  decimal: StoredValue,
): { degrees: string; minutes: string; seconds: string } => {
  if (decimal === null || decimal === undefined || decimal === '') {
    return { degrees: '', minutes: '', seconds: '' };
  }
  const value = Math.abs(Number(decimal));
  if (!Number.isFinite(value)) {
    return { degrees: '', minutes: '', seconds: '' };
  }
  const degrees = Math.floor(value);
  const minutesExact = (value - degrees) * 60;
  const minutes = Math.floor(minutesExact);
  // Rounded before it is shown, not after: 59.999 seconds displayed as "60" is a value the form's
  // own validation would then reject.
  const seconds = Math.round((minutesExact - minutes) * 60 * 100) / 100;

  return {
    degrees: String(degrees),
    minutes: String(minutes),
    seconds: String(seconds),
  };
};

/**
 * The server's answer as the form holds it.
 *
 * <p>Everything becomes a string, because that is what an input holds — see `SiteFormValues`. The
 * codes and org unit numbers come across untouched; the screen decodes them against the code
 * tables it already has.
 */
export const toFormValues = (site: SiteDetailResponse): SiteFormValues => {
  const longitude = toDms(site.longitude);
  const latitude = toDms(site.latitude);

  // Whichever notation the record is missing is derived here rather than after mounting, so it
  // is part of the baseline the form compares against. Legacy fills the same gap on load — its
  // `onInitialLoad` runs the live validate, which triggers the conversion — and then resets
  // `contentChanged`, which is the same effect: opening a record and leaving does not count as an
  // edit. A record carrying both notations keeps both, even where they disagree.
  return fillMissingCoordinates({
    ...EMPTY_SITE,
    siteId: site.siteId,
    crossingSiteStatusCode: asText(site.crossingSiteStatusCode),
    crossingSiteTypeCode: asText(site.crossingSiteTypeCode),
    structureInspectionStatusCode: asText(site.structureInspectionStatusCode),
    forestFileId: asText(site.forestFileId),
    roadSectionId: asText(site.roadSectionId),
    orgUnitNo: asText(site.orgUnitNo),
    clientNumber: asText(site.clientNumber),
    clientLocationCode: asText(site.clientLocnCode),
    maintainerLabel: asText(site.maintainerLabel),
    managementOrgUnitNo: asText(site.managementOrgUnitNo),
    pointOfCommencementDistance: asText(site.pointOfCommencementDistance),
    userKm: asText(site.userKm),
    crossingName: asText(site.crossingName),
    businessAreaOrgUnitNo: asText(site.businessAreaOrgUnitNo),
    specialAccessRqmtCode: asText(site.specialAccessRqmtCode),
    capitalRoad: site.capitalRoad,
    pointOfAccessDescription: asText(site.pointOfAccessDescription),
    longitudeDegrees: longitude.degrees,
    longitudeMinutes: longitude.minutes,
    longitudeSeconds: longitude.seconds,
    latitudeDegrees: latitude.degrees,
    latitudeMinutes: latitude.minutes,
    latitudeSeconds: latitude.seconds,
    utmZone: asText(site.utmZone),
    utmEasting: asText(site.utmEasting),
    utmNorthing: asText(site.utmNorthing),
  });
};
