package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * An inspection that has been reviewed or rejected is no longer changed from its page — HTTP 409.
 * Legacy disables its page once an inspection is Reviewed; here the server says so too.
 */
public class InspectionLockedException extends ResponseStatusException {

  /**
   * @param status what it is — "Reviewed"
   * @param what   what is refused — "its repairs … can no longer be changed from it"
   */
  public InspectionLockedException(Long inspectionId, String status, String what) {
    super(HttpStatus.CONFLICT,
        "Inspection " + inspectionId + " is " + status + ": " + what + ".");
  }
}
