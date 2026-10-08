package ca.bc.gov.nrs.cbr.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A record the server will not store, with a message for each field at fault — HTTP 400 with a
 * {@code fieldErrors} map, as {@link SiteValidationException} answers for a site. Every validation
 * message in CBR is shown beside the field it concerns, so the response says which field.
 *
 * <p>The field names are the request's own, which are the form's names too.
 */
public class FieldValidationException extends RuntimeException {

  /** What could not be saved, for the problem's title — "Monitoring item cannot be saved". */
  private final String title;
  /** Field name to message, in insertion order so the response reads in form order. */
  private final Map<String, String> fieldErrors;

  public FieldValidationException(String title, Map<String, String> fieldErrors) {
    super(fieldErrors.size() == 1
        ? fieldErrors.values().iterator().next()
        : fieldErrors.size() + " fields need attention.");
    this.title = title;
    this.fieldErrors = new LinkedHashMap<>(fieldErrors);
  }

  public String getTitle() {
    return title;
  }

  public Map<String, String> getFieldErrors() {
    return new LinkedHashMap<>(fieldErrors);
  }
}
