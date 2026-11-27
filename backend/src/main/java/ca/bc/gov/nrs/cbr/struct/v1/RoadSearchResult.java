package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * One row of the road search: a road section from LRMOPS.
 *
 * @param forestServiceRoad the section's name; may be null
 * @param forestFileId      "Project File ID#": LRMOPS's road id
 * @param roadSectionId     "Br.": LRMOPS's section id
 * @param tenureType        LRMOPS's road tenure type as stored — {@code RP-RETIRE} for a retired
 *                          road permit, as the Tenure Type dropdown lists it
 * @param districts         the codes of the Forest Districts the section lies in, e.g.
 *                          {@code DPG}, alphabetically: usually one, two when it crosses a
 *                          district boundary; empty when LRMOPS records none
 */
public record RoadSearchResult(
    String forestServiceRoad,
    String forestFileId,
    String roadSectionId,
    String tenureType,
    List<String> districts) {}
