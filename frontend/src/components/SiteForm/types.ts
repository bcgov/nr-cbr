/**
 * The Add Site form, field for field with the legacy `site.jsp`.
 *
 * <p>Everything is a string, including the numbers and the coordinate parts, because that is what
 * a text input holds — an empty box is `''`, not `0` or `undefined`, and a half-typed `12.` is
 * neither a number nor an error until the user stops typing. Conversion belongs at the edge that
 * sends this, as it does on Site Search.
 */
export type SiteFormValues = {
  /** `CROSSING_SITE_ID`. A natural key the user types, upper-cased as they leave the box. */
  siteId: string;
  crossingSiteStatusCode: string;
  crossingSiteTypeCode: string;
  structureInspectionStatusCode: string;
  forestFileId: string;
  /** "Br." on the form — the road section. */
  roadSectionId: string;
  /** Forest District, or Recreation District when the site type is `REC`. Same column either way. */
  orgUnitNo: string;
  /** Designated Maintainer — filled by the client lookup, which sets both halves of the key. */
  clientNumber: string;
  clientLocationCode: string;
  /** The label the lookup displays. Display state; see `SiteSearchCriteria.maintainerLabel`. */
  maintainerLabel: string;
  managementOrgUnitNo: string;
  pointOfCommencementDistance: string;
  userKm: string;
  crossingName: string;
  businessAreaOrgUnitNo: string;
  specialAccessRqmtCode: string;
  capitalRoad: boolean;
  /** "Site Details" on the form — `POINT_OF_ACCESS_DESC`. */
  pointOfAccessDescription: string;
  longitudeDegrees: string;
  longitudeMinutes: string;
  longitudeSeconds: string;
  latitudeDegrees: string;
  latitudeMinutes: string;
  latitudeSeconds: string;
  utmZone: string;
  utmEasting: string;
  utmNorthing: string;
};

/**
 * Site type codes the form branches on.
 *
 * <p>Named because three rules turn on them and a bare `'STRG'` in a condition says nothing about
 * why. The full list comes from the code table; these are only the ones with behaviour attached.
 */
export const SITE_TYPE = {
  /** A crossing — a bridge or culvert. The ordinary case, and the one with inspection rules. */
  CROSSING: 'CRS',
  /** A recreation site. Has a Project Name instead of a road, and no Forest District. */
  RECREATION: 'REC',
  /** A storage site. Holds portable structures, so it has no crossing and no kilometre mark. */
  STORAGE: 'STRG',
} as const;

/** Inspection statuses. Only two exist, and every cross-field rule is about which one applies. */
export const INSPECTION_STATUS = {
  INSPECT: 'INS',
  DO_NOT_INSPECT: 'DNI',
} as const;

/**
 * Site statuses that decide whether a crossing must be inspected.
 *
 * <p>`ACTIVE` and `BARRICADED` mean the crossing is still there to be driven over; the rest mean it
 * is gone, never built, or someone else's. Legacy spells both lists out in `SiteForm.validate`.
 */
export const SITE_STATUS = {
  ACTIVE: 'ACT',
  BARRICADED: 'BAR',
  /** Not built yet — so it may carry no structures at all. */
  PROPOSED: 'PP',
  /** Closed, but its structures may still be standing. */
  DEACTIVATED: 'DAC',
} as const;

/** The statuses a crossing may not be inspected under. */
export const UNINSPECTABLE_STATUSES = ['TRN', 'DAC', 'UCON', 'PP', 'ARC', 'LRM'];

/**
 * Byte limits of the columns behind the free-text fields.
 *
 * <p>Bytes, because that is how the columns are declared — `POINT_OF_ACCESS_DESC` is
 * `VARCHAR2(255 BYTE)`. See `byteLength` for why the distinction matters.
 */
export const SITE_TEXT_LIMITS = {
  pointOfAccessDescription: 255,
} as const satisfies Partial<Record<keyof SiteFormValues, number>>;

export const EMPTY_SITE: SiteFormValues = {
  siteId: '',
  crossingSiteStatusCode: '',
  crossingSiteTypeCode: '',
  structureInspectionStatusCode: '',
  forestFileId: '',
  roadSectionId: '',
  orgUnitNo: '',
  clientNumber: '',
  clientLocationCode: '',
  maintainerLabel: '',
  managementOrgUnitNo: '',
  pointOfCommencementDistance: '',
  userKm: '',
  crossingName: '',
  businessAreaOrgUnitNo: '',
  specialAccessRqmtCode: '',
  capitalRoad: false,
  pointOfAccessDescription: '',
  longitudeDegrees: '',
  longitudeMinutes: '',
  longitudeSeconds: '',
  latitudeDegrees: '',
  latitudeMinutes: '',
  latitudeSeconds: '',
  utmZone: '',
  utmEasting: '',
  utmNorthing: '',
};
