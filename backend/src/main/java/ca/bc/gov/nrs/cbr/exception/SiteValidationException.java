package ca.bc.gov.nrs.cbr.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A site the server will not store, with a message for each field at fault — HTTP 400.
 *
 * <p><b>Keyed by field, not a list of sentences.</b> Every validation message in CBR is shown beside
 * the box it concerns, so an error the server raises has to say which box that is or the screen can
 * only fall back to a banner. Legacy does the same: `SiteAction.validate` stamps a `fieldName` on
 * every message it returns and `site.jsp` writes each into that field's own error div.
 *
 * <p>The field names are the request's own — {@code crossingName}, {@code longitude} — which are the
 * form's names too, so the browser can merge them straight into the map its own rules produce.
 *
 * <p><b>Why a server-side copy of rules the form already applies.</b> The form is a convenience, not
 * a gate: anything reaching this endpoint may have skipped it. Legacy is built the same way round —
 * its JavaScript validates nothing and `SiteForm.validate` decides.
 */
public class SiteValidationException extends RuntimeException {

  /** Field name to message, in insertion order so the response reads in form order. */
  private final Map<String, String> fieldErrors;

  public SiteValidationException(Map<String, String> fieldErrors) {
    super(summarize(fieldErrors));
    this.fieldErrors = new LinkedHashMap<>(fieldErrors);
  }

  public Map<String, String> getFieldErrors() {
    return Map.copyOf(fieldErrors);
  }

  /**
   * The sentence a caller sees when it does not read {@code fieldErrors}.
   *
   * <p>A single problem is quoted in full, because that is the whole of what went wrong and
   * repeating it is more useful than counting it. Several are counted instead: concatenating six
   * messages into one line produces something nobody reads, and a client that ignores the field map
   * has nowhere to put them anyway.
   */
  private static String summarize(Map<String, String> fieldErrors) {
    if (fieldErrors.size() == 1) {
      return fieldErrors.values().iterator().next();
    }
    return "This site cannot be saved: " + fieldErrors.size() + " fields need attention.";
  }
}
