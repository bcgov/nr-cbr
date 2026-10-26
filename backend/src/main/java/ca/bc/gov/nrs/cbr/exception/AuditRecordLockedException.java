package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * An inspection's audit record — the report PDF stored when it was saved — is kept as it was made:
 * never edited, never deleted — HTTP 409.
 */
public class AuditRecordLockedException extends ResponseStatusException {
  public AuditRecordLockedException(long fileId) {
    super(HttpStatus.CONFLICT, "Document " + fileId
        + " is an inspection audit record, so it cannot be changed or deleted.");
  }
}
