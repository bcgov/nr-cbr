package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * An edit to the Information tab — the two fields of legacy's header a user may change there.
 * Type/Class, the header's third, is edited with the bridge on the Details tab.
 *
 * @param structureName {@code CROSSING_STRUCTURE_NAME}: required, at most 14 bytes, unique among
 *                      all structures; changed only at Level 2
 * @param siteId        {@code CROSSING_SITE_ID}: a site that exists, is complete and is not
 *                      Proposed; required once the structure has one
 */
public record StructureInformationRequest(String structureName, String siteId) {}
