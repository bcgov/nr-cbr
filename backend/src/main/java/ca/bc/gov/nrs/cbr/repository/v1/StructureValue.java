package ca.bc.gov.nrs.cbr.repository.v1;

/**
 * One value per structure, from a query that answers for many structures at once — what a report
 * reads beside each row, a batch at a time, rather than a query per row.
 *
 * @param crossingStructureId the structure the value belongs to
 * @param value               a length, a date or a code, as the query selects it
 */
public record StructureValue<T>(Long crossingStructureId, T value) {}
