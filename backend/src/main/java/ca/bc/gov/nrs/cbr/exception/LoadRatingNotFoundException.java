package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A manual load rating asked for by id is not one of the structure's — HTTP 404. A rating an
 * inspection brought is not found here either: it is changed through its inspection.
 */
public class LoadRatingNotFoundException extends ResponseStatusException {

  public LoadRatingNotFoundException(long structureId, long loadRatingId) {
    super(HttpStatus.NOT_FOUND, "Manual load rating " + loadRatingId
        + " does not exist on structure " + structureId + ".");
  }
}
