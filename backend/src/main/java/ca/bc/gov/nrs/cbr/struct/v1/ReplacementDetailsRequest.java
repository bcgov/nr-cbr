package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * An edit to the Details tab's Replacement Details — a P.Eng's, as legacy's page.
 *
 * @param closureYear         {@code ESTIMATED_CLOSURE_DATE}, a 4-digit year
 * @param replacementYear     {@code LIGHT_VEHICLE_TRFFC_RPLCMNT_DT}, not after closure
 * @param loadRestrictionYear {@code FULL_LOG_HAUL_TRFFC_RPLCMNT_DT}, not after replacement or
 *                            closure; required once it has a value
 * @param cost                {@code ESTIMATED_REPLACEMENT_COST}, whole dollars to 99,999,999
 * @param costComment         {@code ESTIMATED_REPLACEMENT_COST_CMT}, at most 2000 bytes
 */
public record ReplacementDetailsRequest(
    Integer closureYear,
    Integer replacementYear,
    Integer loadRestrictionYear,
    Long cost,
    String costComment) {}
