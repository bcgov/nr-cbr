package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * A new repair — legacy's "Repair Item" dialog from Add Repair. No status: a new repair is
 * Suggested, as legacy locks it. No completed date or actual cost, which only a completed repair
 * has.
 *
 * @param priorityCode {@code REPAIR_PRIORITY_CODE}, required
 * @param estimate     whole dollars, 0 to 999,999, or null
 * @param typeCode     {@code STRUCTURE_REPAIR_TYPE_CODE}, required
 * @param quantity     0 to 999,999, or null
 * @param description  at most 2000 bytes; required for the type {@code 800A}
 */
public record RepairCreateRequest(
    String priorityCode,
    Long estimate,
    String typeCode,
    Long quantity,
    String description) {}
