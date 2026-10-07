package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A document asked for by id does not exist on the structure it was asked for under — HTTP 404.
 *
 * <p>Checked against the structure, not only by id, so a file is reached only through the structure
 * it belongs to.
 */
public class DocumentNotFoundException extends ResponseStatusException {

  public DocumentNotFoundException(long structureId, long fileId) {
    super(HttpStatus.NOT_FOUND,
        "Document " + fileId + " does not exist on structure " + structureId + ".");
  }
}
