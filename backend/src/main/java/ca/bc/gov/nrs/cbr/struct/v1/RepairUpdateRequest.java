package ca.bc.gov.nrs.cbr.struct.v1;

import java.time.LocalDate;

/**
 * An edit to a repair — legacy's "Repair Item" dialog, opened from its number. The number itself is
 * not here: it is shown, not changed. Nor is the carried-forward flag, which only an inspection's
 * carry-forward sets.
 *
 * @param statusCode    {@code REPAIR_STATUS_CODE}; Required and Not Required only for a P.Eng
 * @param priorityCode  {@code REPAIR_PRIORITY_CODE}, required
 * @param completedDate required when the status is Completed; kept only then
 * @param estimate      whole dollars, 0 to 999,999, or null
 * @param actualCost    whole dollars, 0 to 999,999, or null; kept only when Completed
 * @param typeCode      {@code STRUCTURE_REPAIR_TYPE_CODE}, required
 * @param quantity      0 to 999,999, or null
 * @param description   at most 2000 bytes; required for the type {@code 800A}
 */
public record RepairUpdateRequest(
    String statusCode,
    String priorityCode,
    LocalDate completedDate,
    Long estimate,
    Long actualCost,
    String typeCode,
    Long quantity,
    String description) {}
