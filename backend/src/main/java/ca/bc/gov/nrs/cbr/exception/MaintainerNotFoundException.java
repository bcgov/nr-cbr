package ca.bc.gov.nrs.cbr.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A designated maintainer named by client number and location does not exist — HTTP 400.
 *
 * <p>Legacy's "Primary User Client Number/Location is invalid." The screen only offers locations
 * that exist, so this is reached by a request built some other way, or by a location removed
 * while the dialog was open.
 */
public class MaintainerNotFoundException extends ResponseStatusException {

  public MaintainerNotFoundException(String clientNumber, String clientLocationCode) {
    super(HttpStatus.BAD_REQUEST, "Designated maintainer " + clientNumber + "-"
        + clientLocationCode + " does not exist.");
  }
}
