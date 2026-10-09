package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A comment asked for by id does not exist on the structure it was asked for under — HTTP 404.
 * Checked against the structure, not only by id, so a comment is reached only through the structure
 * it belongs to.
 */
public class CommentNotFoundException extends ResponseStatusException {

  public CommentNotFoundException(long structureId, long commentId) {
    super(HttpStatus.NOT_FOUND,
        "Comment " + commentId + " does not exist on structure " + structureId + ".");
  }
}
