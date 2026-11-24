package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * One road section, as the site form reads it.
 *
 * @param forestFileId      "Project File ID#" on the form
 * @param roadSectionId     "Br." on the form
 * @param forestServiceRoad the section's name — what the form displays. May be null
 * @param tenureType        LRMOPS's tenure type as stored — {@code RP-RETIRE} for a retired road
 *                          permit, as Road Search shows it. May be null
 * @param clientName        the tenure holder's name. May be null
 * @param districts         the Forest Districts the section lies in, from LRMOPS. Usually one,
 *                          which the form fills in; a section crossing a district boundary has
 *                          two or more, and the user picks one of them. May be empty
 */
public record RoadSectionResponse(
    String forestFileId,
    String roadSectionId,
    String forestServiceRoad,
    String tenureType,
    String clientName,
    List<RoadDistrict> districts) {

  public RoadSectionResponse {
    districts = districts == null ? List.of() : List.copyOf(districts);
  }

  /**
   * A Forest District a road section lies in.
   *
   * @param orgUnitNo the district's org unit number, as {@code CROSSING_SITE.ORG_UNIT_NO} stores it
   * @param code      the district's code, e.g. {@code DPG}
   * @param lengthMetres how much of the section lies in this district; null when LRMOPS's answer
   *                  does not say
   */
  public record RoadDistrict(Long orgUnitNo, String code, Double lengthMetres) {}
}
