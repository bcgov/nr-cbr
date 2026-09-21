package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * An inspection can only be deleted while it is offline — HTTP 409.
 *
 * <p><b>This check does not exist in legacy, and that is the point of it.</b> The rule is real —
 * {@code inspection_search.jsp:339} renders the delete control only for a row whose current status
 * is {@code OFL} — but it is enforced nowhere else. {@code InspectionAction.delete()} checks only
 * that the id parses as a number, so a caller holding {@code /deleteInspection} who sent
 * {@code deleteInspection.do?actionMapping=delete&inspectionId=N} by hand could delete any
 * inspection, reviewed ones included, along with its entire status history. The privilege was
 * enforced; the status was not.
 *
 * <p>Deleting an offline inspection means cancelling a checkout that is never coming back — a
 * device lost or wiped, or an inspection started by mistake that now blocks its structure, because
 * {@code ExistingOfflineInspectionException} refuses a second checkout while one is outstanding.
 * Deleting a submitted or reviewed one means destroying a completed inspection and its audit trail,
 * which is a different act entirely and not one this endpoint offers.
 *
 * <p>409 and not 400: the request is well formed and the caller is entitled to make it; the state of
 * the inspection is what refuses. Told 400, a user would look for something wrong with what they
 * sent.
 */
public class InspectionNotOfflineException extends ResponseStatusException {

  public InspectionNotOfflineException(Long inspectionId, String status) {
    super(HttpStatus.CONFLICT, "Inspection " + inspectionId + " is " + status
        + " and cannot be deleted. Only an offline inspection can be deleted.");
  }
}
