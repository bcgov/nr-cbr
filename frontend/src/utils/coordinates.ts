/**
 * Coordinate conversion for the site form — degrees/minutes/seconds, decimal degrees and UTM.
 *
 * <p>Ported from legacy's `ca.bc.gov.mof.cbr.util.coord` package: `LongLatCoordinate` for the DMS
 * arithmetic and `CoordinateUtils` for the projection, which is in turn Sami Salkosuo's converter
 * from IBM developerWorks. Legacy calls both over AJAX (`showSite.do?actionMapping=utm` and
 * `…=longlat`); this runs in the browser instead. <b>The maths has no data access</b> — it is a
 * closed-form projection on two constants — so a round trip to the server buys nothing but latency,
 * and a 2007 codebase reaching for the server for arithmetic is an artefact of where its code could
 * live rather than a decision about where it belongs.
 *
 * <p><b>Legacy converts the two directions with two different libraries.</b> Longitude/latitude to
 * UTM goes through Salkosuo's `LatLon2UTM`; UTM to longitude/latitude goes through
 * `com.vividsolutions.spatial.projection.UniversalTransverseMercator` on the GRS80 spheroid. Both
 * are the standard transverse Mercator series and both are documented as accurate to a metre —
 * GRS80 and WGS84 differ only in the eleventh digit of the flattening. Salkosuo's own inverse ships
 * in the same file and is what is ported below, so the pair here round-trips exactly where legacy's
 * mismatched pair does not.
 */

/** WGS84 equatorial radius, in metres. `a` in every formula below. */
const EQUATORIAL_RADIUS = 6378137;

/** WGS84 polar radius, in metres. */
const POLAR_RADIUS = 6356752.314;

/** The UTM scale factor on the central meridian. */
const SCALE_FACTOR = 0.9996;

/** First eccentricity of the spheroid. */
const E = Math.sqrt(1 - (POLAR_RADIUS / EQUATORIAL_RADIUS) ** 2);

/** Second eccentricity squared — `e'²`, which the series below wants directly. */
const E1SQ = (E * E) / (1 - E * E);

/** One arc-second in radians, which Salkosuo's forward series is written in terms of. */
const SIN1 = 4.84814e-6;

/** Meridional arc coefficients, as legacy hard-codes them. */
const A0 = 6367449.146;
const B0 = 16038.42955;
const C0 = 16.83261333;
const D0 = 0.021984404;
const E0 = 0.000312705;

const toRadians = (degrees: number) => (degrees * Math.PI) / 180;

/** One coordinate as the form holds it: three boxes, all strings. */
export type Dms = {
  degrees: number;
  minutes: number;
  seconds: number;
};

export type Utm = {
  zone: number;
  easting: number;
  northing: number;
};

/**
 * Degrees, minutes and seconds as one decimal degree value.
 *
 * <p>Legacy's `LongLatCoordinate.dmsToDecimalDegree`. The sign lives on the degrees and carries to
 * the whole value — minutes and seconds are always counted as magnitudes, so −123° 30′ is
 * −123.5 rather than −122.5.
 */
export const dmsToDecimal = ({ degrees, minutes, seconds }: Dms): number => {
  const magnitude = Math.abs(degrees) + minutes / 60 + seconds / 3600;
  return degrees < 0 ? -magnitude : magnitude;
};

/**
 * A decimal degree value back as three parts.
 *
 * <p>Legacy's `LongLatCoordinate.decimalDegreeToDms`, truncation included: the degrees are the
 * integer part *towards zero*, so −123.5 gives −123 degrees and 30 minutes rather than −124 and 30.
 * The minutes and seconds are then magnitudes, which is why the sign has to be read off the degrees
 * alone.
 */
export const decimalToDms = (decimal: number): Dms => {
  const degrees = Math.trunc(decimal);
  const minuteDecimal = Math.abs((decimal - degrees) * 60);
  const minutes = Math.floor(minuteDecimal);
  return { degrees, minutes, seconds: (minuteDecimal - minutes) * 60 };
};

/**
 * The UTM longitude zone a meridian falls in.
 *
 * <p>Six degrees per zone, numbered from the antimeridian. British Columbia spans zones 7 to 11.
 */
const longitudeZone = (longitude: number): number =>
  Math.trunc(longitude < 0 ? (180 + longitude) / 6 + 1 : longitude / 6 + 31);

/**
 * Decimal longitude and latitude as a UTM coordinate.
 *
 * <p>Salkosuo's `LatLon2UTM.convertLatLonToUTM`, term for term. <b>Easting and northing truncate to
 * whole metres</b>, as legacy's `(int)` casts do — `UTM_EASTING` and `UTM_NORTHING` are
 * `NUMBER(10)` with no scale, so a fraction could not be stored in any case.
 *
 * <p>Returns null outside the legal domain rather than throwing: this is called on every keystroke
 * in a coordinate box, where half-entered values are ordinary.
 */
export const latLonToUtm = (longitude: number, latitude: number): Utm | null => {
  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude >= 180) {
    return null;
  }

  const phi = toRadians(latitude);
  const nu = EQUATORIAL_RADIUS / Math.sqrt(1 - (E * Math.sin(phi)) ** 2);

  // Arc-seconds from the zone's central meridian, scaled as the series below expects.
  const zone = longitudeZone(longitude);
  const centralMeridian = 6 * zone - 183;
  const p = ((longitude - centralMeridian) * 3600) / 10000;

  const meridionalArc =
    A0 * phi -
    B0 * Math.sin(2 * phi) +
    C0 * Math.sin(4 * phi) -
    D0 * Math.sin(6 * phi) +
    E0 * Math.sin(8 * phi);

  const k1 = meridionalArc * SCALE_FACTOR;
  const k2 = (nu * Math.sin(phi) * Math.cos(phi) * SIN1 ** 2 * SCALE_FACTOR * 100000000) / 2;
  const k3 =
    ((SIN1 ** 4 * nu * Math.sin(phi) * Math.cos(phi) ** 3) / 24) *
    (5 - Math.tan(phi) ** 2 + 9 * E1SQ * Math.cos(phi) ** 2 + 4 * E1SQ ** 2 * Math.cos(phi) ** 4) *
    SCALE_FACTOR *
    1e16;
  const k4 = nu * Math.cos(phi) * SIN1 * SCALE_FACTOR * 10000;
  const k5 =
    (SIN1 * Math.cos(phi)) ** 3 *
    (nu / 6) *
    (1 - Math.tan(phi) ** 2 + E1SQ * Math.cos(phi) ** 2) *
    SCALE_FACTOR *
    1e12;

  const easting = 500000 + (k4 * p + k5 * p ** 3);
  let northing = k1 + k2 * p * p + k3 * p ** 4;
  if (latitude < 0) {
    northing += 10000000;
  }

  return { zone, easting: Math.trunc(easting), northing: Math.trunc(northing) };
};

/**
 * A UTM coordinate as decimal longitude and latitude.
 *
 * <p>Salkosuo's `UTM2LatLon.convertUTMToLatLong`, minus the latitude-band letter: every CBR site is
 * in the northern hemisphere, and the band is only ever consulted to decide that. Legacy's own form
 * never sends one — `SiteAction.longlat` takes the zone number alone — so there is nothing here to
 * drop.
 *
 * <p>Returns null for a zone outside 1–60, which is the one input that makes the series meaningless
 * rather than merely wrong.
 */
export const utmToLatLon = ({
  zone,
  easting,
  northing,
}: Utm): { longitude: number; latitude: number } | null => {
  if (!Number.isFinite(zone) || zone < 1 || zone > 60) {
    return null;
  }

  const arc = northing / SCALE_FACTOR;
  const mu = arc / (EQUATORIAL_RADIUS * (1 - E ** 2 / 4 - (3 * E ** 4) / 64 - (5 * E ** 6) / 256));

  const ei = (1 - Math.sqrt(1 - E * E)) / (1 + Math.sqrt(1 - E * E));
  const ca = (3 * ei) / 2 - (27 * ei ** 3) / 32;
  const cb = (21 * ei ** 2) / 16 - (55 * ei ** 4) / 32;
  const cc = (151 * ei ** 3) / 96;
  const cd = (1097 * ei ** 4) / 512;

  const phi1 =
    mu +
    ca * Math.sin(2 * mu) +
    cb * Math.sin(4 * mu) +
    cc * Math.sin(6 * mu) +
    cd * Math.sin(8 * mu);

  const n0 = EQUATORIAL_RADIUS / Math.sqrt(1 - (E * Math.sin(phi1)) ** 2);
  const r0 = (EQUATORIAL_RADIUS * (1 - E * E)) / (1 - (E * Math.sin(phi1)) ** 2) ** 1.5;
  const fact1 = (n0 * Math.tan(phi1)) / r0;

  const a1 = 500000 - easting;
  const dd0 = a1 / (n0 * SCALE_FACTOR);
  const fact2 = (dd0 * dd0) / 2;

  const t0 = Math.tan(phi1) ** 2;
  const q0 = E1SQ * Math.cos(phi1) ** 2;
  const fact3 = ((5 + 3 * t0 + 10 * q0 - 4 * q0 * q0 - 9 * E1SQ) * dd0 ** 4) / 24;
  const fact4 =
    ((61 + 90 * t0 + 298 * q0 + 45 * t0 * t0 - 252 * E1SQ - 3 * q0 * q0) * dd0 ** 6) / 720;

  const latitude = (180 * (phi1 - fact1 * (fact2 + fact3 + fact4))) / Math.PI;

  const lof1 = a1 / (n0 * SCALE_FACTOR);
  const lof2 = ((1 + 2 * t0 + q0) * dd0 ** 3) / 6;
  const lof3 = ((5 - 2 * q0 + 28 * t0 - 3 * q0 ** 2 + 8 * E1SQ + 24 * t0 ** 2) * dd0 ** 5) / 120;
  const longitude = 6 * zone - 183 - ((lof1 - lof2 + lof3) / Math.cos(phi1)) * (180 / Math.PI);

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return null;
  }
  return { longitude, latitude };
};
