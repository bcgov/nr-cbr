package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * The repair just added.
 *
 * @param id     its {@code REPAIR_ID}
 * @param number the structure's next repair number, which it took
 */
public record RepairCreatedResponse(String id, long number) {}
