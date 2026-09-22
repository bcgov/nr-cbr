package ca.bc.gov.nrs.cbr.struct.v1;

import java.math.BigDecimal;

/**
 * One crossing site, as the detail screen reads it.
 *
 * <p><b>Stored values, not display values.</b> Codes come back as codes and org units as numbers,
 * because the screen already holds every code table it needs to decode them and is the only thing
 * that knows how they should read. Two exceptions earn their place: the road name and the
 * maintainer are on tables the screen has no other reason to fetch, and legacy goes after both the
 * same way — its road join, and the {@code getClientDetails()} call {@code site.jsp} makes on load.
 *
 * <p><b>The coordinates are decimal degrees</b>, as the columns hold them. Longitude is negative.
 * The form enters and shows degrees/minutes/seconds, and converts at that edge — the same split
 * legacy makes, where `LongLatCoordinate` sits in the form and the column stores a decimal.
 *
 * @param maintainerLabel the Designated Maintainer as one line, or null when the site names none
 * @param forestServiceRoad the road's name from {@code CBR_ROAD_SECTION_VW}; null when the
 *                          snapshot does not carry that section yet
 */
public record SiteDetailResponse(
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
    String forestServiceRoad,
    String clientNumber,
    String clientLocnCode,
    String maintainerLabel,
    boolean capitalRoad,
    BigDecimal longitude,
    BigDecimal latitude,
    Integer utmZone,
    Long utmEasting,
    Long utmNorthing,
    String pointOfAccessDescription,
    String ntsMapSheetNumber,
    String trimMapSheetNumber) {}
