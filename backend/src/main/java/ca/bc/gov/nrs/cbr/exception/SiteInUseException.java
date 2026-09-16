package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A site cannot be deleted because something still hangs off it — HTTP 409.
 *
 * <p><b>409 and not 400.</b> The request is well formed and the caller is entitled to make it; the
 * state of the data is what refuses. Told 400, a user would look for something wrong with what they
 * typed.
 *
 * <p>The message names what is in the way, because the user's next action depends on it: archived
 * structures and close-proximity inspections are not visible from the search results, so "this site
 * cannot be deleted" on its own would be a dead end.
 */
public class SiteInUseException extends ResponseStatusException {

  public SiteInUseException(String reason) {
    super(HttpStatus.CONFLICT, reason);
  }
}
