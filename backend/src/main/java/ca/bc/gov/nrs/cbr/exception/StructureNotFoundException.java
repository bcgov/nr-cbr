package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A structure that was asked for by id does not exist — HTTP 404.
 *
 * <p>A live case on a delete: the id comes from a results page that may have been on screen for
 * some time, and someone else may have deleted the structure in between. Legacy's own delete
 * crashed on it, reading the name of the structure it had just failed to find.
 */
public class StructureNotFoundException extends ResponseStatusException {

  public StructureNotFoundException(long structureId) {
    super(HttpStatus.NOT_FOUND, "Structure " + structureId + " does not exist.");
  }
}
