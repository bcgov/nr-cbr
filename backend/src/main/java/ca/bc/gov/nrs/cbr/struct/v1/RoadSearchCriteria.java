package ca.bc.gov.nrs.cbr.struct.v1;

import lombok.Builder;

/**
 * What the road search dialog asks on.
 *
 * <p>Every one is optional, and every one but the tenure type and the district is a partial,
 * case-insensitive match, answered by the LRM Ops API. All blank is a legitimate search that returns
 * the first page of everything.
 *
 * <p>No client: LRMOPS records the tenure holder's name as free text, entered when the tenure was
 * created and not changed when the road passes to someone else, so the business asked for the
 * district instead (2026-11-26).
 */
@Builder
public record RoadSearchCriteria(
    String forestServiceRoad,
    String forestFileId,
    String roadSectionId,
    /** LRMOPS's road tenure type, matched exactly: {@code FSR}, {@code FSR-RETIRE}, {@code RP}, … */
    String tenureType,
    /**
     * The Forest District's org unit number, as the site's Forest District stores it. A section
     * crossing a district boundary is found under each of its districts.
     */
    Long district) {}
