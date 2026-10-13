package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * A pier to add, or the new values of one — legacy's pier form on the Spans &amp; Piers tab.
 *
 * @param number   {@code PIER_NUMBER}: 1 to 9999999999, unique on the bridge
 * @param typeCode {@code PIER_TYPE_CODE}: a current type, or the pier's own when it has expired
 */
public record PierRequest(Long number, String typeCode) {}
