package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The LRM Ops API could not answer: down, timing out, refusing CBR's token, or not configured in
 * this environment.
 *
 * <p>503, so the browser can tell it apart from a 404 ("no such road") and say "Road lookup is
 * unavailable right now." beside the field instead of "no road matches".
 */
public class RoadLookupUnavailableException extends ResponseStatusException {

  public RoadLookupUnavailableException(Throwable cause) {
    super(HttpStatus.SERVICE_UNAVAILABLE, "Road lookup is unavailable right now.", cause);
  }
}
