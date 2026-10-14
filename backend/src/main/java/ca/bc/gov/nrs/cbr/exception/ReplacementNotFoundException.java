package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The structure did not replace the structure asked about — HTTP 404. */
public class ReplacementNotFoundException extends ResponseStatusException {

  public ReplacementNotFoundException(long structureId, long replacedId) {
    super(HttpStatus.NOT_FOUND, "Structure " + structureId + " did not replace structure "
        + replacedId + ".");
  }
}
