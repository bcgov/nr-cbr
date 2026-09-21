package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * One suggestion in the Designated Maintainer lookup.
 *
 * <p><b>A location, not a client.</b> The pair is what {@code CROSSING_SITE} records and what the
 * search filters on, so a client with three locations produces three suggestions — the same shape
 * legacy's lookup popup returned, and the reason it displayed an address beside each row.
 *
 * <p><b>The label is composed by the caller, not here.</b> {@code clientLocnName} and {@code city}
 * are carried so the UI can tell one of a client's locations from another; how they are arranged is
 * a presentation decision, and nr-frep's equivalent {@code clientLabel} lives in the frontend for
 * the same reason.
 *
 * <p>No acronym, unlike nr-frep's: {@code CLIENT_ACRONYM} is on {@code FOREST_CLIENT}, which
 * {@code FSA_CBR_READ_WRITE_ROLE} cannot read. Legacy CBR's own lookup did not search it either.
 *
 * @param clientNumber       {@code CROSSING_SITE.CLIENT_NUMBER}, zero-padded to 8
 * @param clientLocnCode     {@code CROSSING_SITE.CLIENT_LOCN_CODE}, two characters
 * @param clientName         the maintainer's name; a surname when the client is an individual
 * @param clientLocnName     division or joint-venture name, often null
 * @param city               where that location is
 */
public record ClientLookupResult(
    String clientNumber,
    String clientLocnCode,
    String clientName,
    String clientLocnName,
    String city) {}
