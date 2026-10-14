package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * A manual load rating to add, or its new values — legacy's Add Load Rating and Edit Manual Load
 * Rating.
 *
 * @param rating        tonnes GVW, a whole number from 0 to 999999
 * @param reasonCode    {@code STRCTRE_LOAD_RATING_RSN_CODE}, required
 * @param reasonComment required for {@code OTHR}, at most 4000 bytes
 */
public record LoadRatingRequest(Long rating, String reasonCode, String reasonComment) {}
