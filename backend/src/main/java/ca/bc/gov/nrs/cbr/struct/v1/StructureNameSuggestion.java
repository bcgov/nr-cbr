package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * A structure offered as the user types a Structure # — Replacement Details' type-ahead.
 *
 * @param id            the {@code CROSSING_STRUCTURE_ID}
 * @param structureName the Structure #, {@code CROSSING_STRUCTURE_NAME}
 * @param archived      {@code ACTIVE_IND = 'N'}; still offered, as legacy took any structure
 */
public record StructureNameSuggestion(String id, String structureName, boolean archived) {}
