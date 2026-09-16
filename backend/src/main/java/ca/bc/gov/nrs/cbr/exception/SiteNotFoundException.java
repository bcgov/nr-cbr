package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A site that was asked for by id does not exist — HTTP 404.
 *
 * <p>Legacy's equivalent is the {@code errors.not.exist} message, "Record does not exist.", which it
 * shows when a delete arrives for a site that is already gone. That is a live case rather than a
 * theoretical one: the delete is reached from a results table that may have been on screen for some
 * time, and someone else may have deleted the same site in between.
 *
 * <p>Extends {@link ResponseStatusException} so the status and the message travel together and no
 * exception-handling advice is needed to translate them. Spring renders it as an RFC 7807
 * {@code ProblemDetail}, which {@code spring.mvc.problemdetails} already enables.
 */
public class SiteNotFoundException extends ResponseStatusException {

  public SiteNotFoundException(String siteId) {
    super(HttpStatus.NOT_FOUND, "Site " + siteId + " does not exist.");
  }
}
