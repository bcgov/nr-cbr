package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * No road section for the {@code (FOREST_FILE_ID, ROAD_SECTION_ID)} pair asked for.
 *
 * <p><b>Ordinary, not exceptional.</b> The site form asks on every change of either box, so most of
 * what it asks about is a pair the user is still part-way through typing. The answer comes from the
 * LRM Ops API; when that cannot answer at all, see {@link RoadLookupUnavailableException}.
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
