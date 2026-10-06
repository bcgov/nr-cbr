package ca.bc.gov.nrs.cbr.struct.v1;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The structures to archive — {@code PUT /api/v1/structures/archive}.
 *
 * @param structureIds the {@code CROSSING_STRUCTURE_ID}s ticked on Structure Search; at least one,
 *                     as legacy answers "No structures have been selected." to none
 */
public record StructureArchiveRequest(@NotEmpty List<@NotNull Long> structureIds) {}
