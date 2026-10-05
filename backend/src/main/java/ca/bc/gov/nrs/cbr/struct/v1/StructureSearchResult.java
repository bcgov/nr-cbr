package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * One row of Structure Search — the columns legacy's results table shows, with the maintainer as
 * one value rather than legacy's second row.
 *
 * @param id                   {@code CROSSING_STRUCTURE_ID}
 * @param structureName        "Structure #"
 * @param siteId               the site the structure stands on now; links to the site
 * @param orgUnitCode          District Code
 * @param orgUnitName          the district's name, shown beside its code
 * @param forestServiceRoad    the road section's name
 * @param kilometres           the site's point of commencement distance, at its column's scale
 * @param crossingName         the site's crossing name
 * @param structureTypeClass   Type/Class — the description, not the code
 * @param forestFileId         Project File ID#
 * @param roadSectionId        Br.
 * @param clientNumber         the Designated Maintainer's client number
 * @param clientLocationCode   and location
 * @param clientName           and name, when Forest Client has one for the number
 */
public record StructureSearchResult(
    String id,
    String structureName,
    String siteId,
    String orgUnitCode,
    String orgUnitName,
    String forestServiceRoad,
    String kilometres,
    String crossingName,
    String structureTypeClass,
    String forestFileId,
    String roadSectionId,
    String clientNumber,
    String clientLocationCode,
    String clientName,
    /*
     * What stops this structure being deleted — "inspections", "repairs" and so on; empty when
     * nothing does. Null for a caller who cannot delete, for whom it is not worked out at all.
     */
    List<String> deleteBlockers) {}
