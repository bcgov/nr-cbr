package ca.bc.gov.nrs.cbr.repository.v1;

import java.math.BigDecimal;

/**
 * A bridge record's total length and pier count, for a report that lists a line per bridge record.
 *
 * @param crossingStructureId the structure the bridge belongs to
 * @param totalLength         {@code TOTAL_BRIDGE_LENGTH}, metres
 * @param pierCount           how many {@code FOREST_SERVICE_BRIDGE_PIER} rows the bridge has
 */
public record StructureBridgeLength(Long crossingStructureId, BigDecimal totalLength, Long pierCount) {}
