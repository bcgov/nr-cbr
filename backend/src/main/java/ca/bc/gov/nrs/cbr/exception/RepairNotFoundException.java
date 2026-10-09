package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A repair asked for by id does not exist on the structure it was asked for under — HTTP
 * 404. Checked against the structure, not only by id, so a repair is reached only through the
 * structure it belongs to. A live case on a delete: someone else may have deleted it already.
 */
public class RepairNotFoundException extends ResponseStatusException {

  public RepairNotFoundException(long structureId, long repairId) {
    super(HttpStatus.NOT_FOUND,
        "Repair " + repairId + " does not exist on structure " + structureId + ".");
  }
}
