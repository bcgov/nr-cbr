package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A span or pier delete the bridge will not allow, or a span or pier for a structure with no bridge
 * record — HTTP 409. The message is legacy's sentence, for the screen to show as it stands: "Span
 * can't be deleted, it is associated with an inspection."
 */
public class SpanPierConflictException extends ResponseStatusException {

  public SpanPierConflictException(String reason) {
    super(HttpStatus.CONFLICT, reason);
  }
}
