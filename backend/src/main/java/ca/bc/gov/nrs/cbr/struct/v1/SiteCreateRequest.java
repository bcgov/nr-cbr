package ca.bc.gov.nrs.cbr.struct.v1;

import java.math.BigDecimal;

/**
 * A new crossing site, as the Add Site form submits it.
 *
 * <p>Mirrors {@link SiteDetailResponse} field for field, minus the three the server owns — the road
 * name and the maintainer label are read back from other tables, and the structure count belongs to
 * records this request cannot create. What goes in comes back out.
 *
 * <p><b>Decimal degrees, not degrees/minutes/seconds.</b> The columns hold decimals and the detail
 * response returns decimals; the form is the one place that deals in three boxes, and it converts at
 * that edge. Legacy splits it the same way — `SiteForm.getSiteDTO` does the DMS arithmetic before
 * anything reaches `INSERT_SITE`.
 *
 * <p><b>Longitude is negative.</b> Every site in the province is west of Greenwich. The form enters
 * it unsigned and negates on the way out, exactly as `SiteForm` does; a positive value arriving here
 * is a caller that skipped that step, and {@code SiteValidator} says so rather than quietly flipping
 * the sign on a coordinate someone may have meant.
 *
 * <p>Every field is nullable. Which of them may be null depends on the site type and is decided by
 * {@code SiteValidator}, not by the shape of this record — a missing Crossing Name is a message the
 * user can act on, where a deserialization failure is a 400 with nothing useful in it.
 *
 * @param siteId       {@code CROSSING_SITE_ID}, a natural key the user types. Stored upper-cased,
 *                     as {@code INSERT_SITE} does with {@code UPPER(P_SITE_ID)}
 * @param capitalRoad  {@code CAPITAL_ROAD_IND}. A primitive because the column is
 *                     {@code NOT NULL DEFAULT 'N'} — there is no third state to carry
 */
public record SiteCreateRequest(
    String siteId,
    String crossingName,
    BigDecimal pointOfCommencementDistance,
    BigDecimal userKm,
    String crossingSiteStatusCode,
    String structureInspectionStatusCode,
    String crossingSiteTypeCode,
    String specialAccessRqmtCode,
    Long orgUnitNo,
    Long managementOrgUnitNo,
    Long businessAreaOrgUnitNo,
    String forestFileId,
    String roadSectionId,
    String clientNumber,
    String clientLocnCode,
    boolean capitalRoad,
    BigDecimal longitude,
    BigDecimal latitude,
    Integer utmZone,
    Long utmEasting,
    Long utmNorthing,
    String pointOfAccessDescription) {}
