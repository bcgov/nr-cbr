package ca.bc.gov.nrs.cbr.struct.v1;

import lombok.Builder;

/**
 * What the road search dialog asks on.
 *
 * <p>Every one is optional and every one is a partial, case-insensitive match, answered by the LRM
 * Ops API. All blank is a legitimate search that returns the first page of everything.
 *
 * <p>No client number: LRMOPS records the tenure holder's name only.
 */
@Builder
public record RoadSearchCriteria(
    String forestServiceRoad,
    String forestFileId,
    String roadSectionId,
    /** LRMOPS's road tenure type, matched exactly: {@code FSR}, {@code FSR-RETIRE}, {@code RP}, … */
    String tenureType,
    String clientName) {}
