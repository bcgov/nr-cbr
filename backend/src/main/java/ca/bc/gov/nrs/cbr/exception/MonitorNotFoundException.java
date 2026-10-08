package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A monitoring item asked for by id does not exist on the structure it was asked for under — HTTP
 * 404. Checked against the structure, not only by id, so an item is reached only through the
 * structure it belongs to. A live case on a delete: someone else may have deleted it already.
 */
public class MonitorNotFoundException extends ResponseStatusException {

  public MonitorNotFoundException(long structureId, long monitorId) {
    super(HttpStatus.NOT_FOUND,
        "Monitoring item " + monitorId + " does not exist on structure " + structureId + ".");
  }
}
