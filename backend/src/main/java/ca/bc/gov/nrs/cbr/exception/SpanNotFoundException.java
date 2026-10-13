package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A span asked for by id is not one of the structure's — HTTP 404. Checked against the structure,
 * not only by id, so a span is reached only through the bridge it belongs to, where legacy changed
 * or deleted any span by its id alone.
 */
public class SpanNotFoundException extends ResponseStatusException {

  public SpanNotFoundException(long structureId, long spanId) {
    super(HttpStatus.NOT_FOUND,
        "Span " + spanId + " does not exist on structure " + structureId + ".");
  }
}
