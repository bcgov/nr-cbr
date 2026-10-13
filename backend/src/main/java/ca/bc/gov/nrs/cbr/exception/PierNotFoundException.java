package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A pier asked for by id is not one of the structure's — HTTP 404. Checked against the structure,
 * not only by id, so a pier is reached only through the bridge it belongs to, where legacy changed
 * or deleted any pier by its id alone.
 */
public class PierNotFoundException extends ResponseStatusException {

  public PierNotFoundException(long structureId, long pierId) {
    super(HttpStatus.NOT_FOUND,
        "Pier " + pierId + " does not exist on structure " + structureId + ".");
  }
}
