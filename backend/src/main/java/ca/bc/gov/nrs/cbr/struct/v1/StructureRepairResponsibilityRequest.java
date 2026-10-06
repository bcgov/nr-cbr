package ca.bc.gov.nrs.cbr.struct.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A new designated maintainer for the sites of the ticked structures —
 * {@code PUT /api/v1/structures/repair-responsibility}.
 *
 * <p>The messages are legacy's, from {@code StructureSearchForm.validate}, under the label the
 * screen uses for the field.
 *
 * @param structureIds       the {@code CROSSING_STRUCTURE_ID}s ticked on Structure Search
 * @param clientNumber       the maintainer's client number
 * @param clientLocationCode which of its locations
 */
public record StructureRepairResponsibilityRequest(
    @NotEmpty(message = "No structures have been selected.") List<@NotNull Long> structureIds,
    @NotBlank(message = "Designated Maintainer is required.") String clientNumber,
    @NotBlank(message = "Maintainer Location is required.") String clientLocationCode) {}
