package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A structure cannot be deleted because other records still belong to it — HTTP 409.
 *
 * <p>The message names what, so the screen can show it as it stands: "Structure B100 has
 * inspections and repairs and cannot be deleted."
 */
public class StructureInUseException extends ResponseStatusException {

  public StructureInUseException(String reason) {
    super(HttpStatus.CONFLICT, reason);
  }
}
