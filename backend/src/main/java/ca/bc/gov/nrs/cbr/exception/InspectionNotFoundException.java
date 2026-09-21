package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * An inspection that was asked for by id does not exist — HTTP 404.
 *
 * <p>A live case rather than a theoretical one, for the same reason as
 * {@link SiteNotFoundException}: the delete is reached from a results table that may have been on
 * screen for some time, and an offline inspection is exactly the kind of row another user may have
 * dealt with in the meantime.
 */
public class InspectionNotFoundException extends ResponseStatusException {

  public InspectionNotFoundException(Long inspectionId) {
    super(HttpStatus.NOT_FOUND, "Inspection " + inspectionId + " does not exist.");
  }
}
