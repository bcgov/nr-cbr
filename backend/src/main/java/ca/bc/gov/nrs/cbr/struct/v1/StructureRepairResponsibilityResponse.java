package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * What a repair-responsibility update did.
 *
 * @param structureCount how many of the requested structures were updated — those that exist and
 *                       stand on a site
 * @param siteCount      how many sites' maintainer was set; fewer than the structures when several
 *                       stand on one site
 */
public record StructureRepairResponsibilityResponse(int structureCount, int siteCount) {}
