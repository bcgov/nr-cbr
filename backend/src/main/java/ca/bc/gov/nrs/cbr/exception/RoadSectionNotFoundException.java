package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * No road section for the {@code (FOREST_FILE_ID, ROAD_SECTION_ID)} pair asked for.
 *
 * <p><b>Ordinary, not exceptional.</b> The site form asks on every change of either box, so most of
 * what it asks about is a pair the user is still part-way through typing. It is also what an
 * environment provisioned from nr-mof-db answers to everything, because the view there is stubbed
 * to a single all-null row — see {@link ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity}.
 *
 * <p>The caller is expected to treat it as "no road of that name yet" and show nothing, rather than
 * as a failure worth reporting.
 */
public class RoadSectionNotFoundException extends ResponseStatusException {

  public RoadSectionNotFoundException(String forestFileId, String roadSectionId) {
    super(HttpStatus.NOT_FOUND,
        "No road section " + forestFileId + "-" + roadSectionId + " was found.");
  }
}
