package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * A replacement added — legacy's Replacement History pop-up, a P.Eng's: "Structure # (this one)
 * replaces Structure #".
 *
 * @param replacedName the Structure # of the older structure, the one this structure replaced
 */
public record ReplacementRequest(String replacedName) {}
