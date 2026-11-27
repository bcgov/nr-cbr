package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.util.CriteriaValues;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * The Area and Forest District rule of the two largest reports, Inspections Conducted – Detail and
 * Inspections – Blank: an Area or at least one district — both is fine — and never more than
 * {@value #MAX} districts.
 *
 * <p>A province's run of either is tens of thousands of pages, more than the pod can fill and send
 * before the browser gives up. The business asked for the district to be required, up to eight of
 * them, then for an Area to do instead (2026-11-27). Legacy asked for neither: it ran its reports
 * on a separate report server.
 */
final class ReportDistricts {

  /** The most districts one run may ask for. */
  static final int MAX = 8;

  static final String FIELD = "orgUnit";
  static final String REQUIRED = "Select an Area or at least one Forest District.";
  static final String TOO_MANY = "Select no more than " + MAX + " Forest Districts.";

  private ReportDistricts() {}

  /**
   * Refuses the report unless its criteria name an Area or a district, and no more than
   * {@value #MAX} districts.
   *
   * @param area    the criteria's Area
   * @param orgUnit the criteria's Forest Districts, comma-separated
   * @throws FieldValidationException a 400 naming {@code orgUnit}, otherwise — the box the message
   *                                  is shown under
   */
  static void require(String area, String orgUnit) {
    int picked = CriteriaValues.of(orgUnit).size();
    if (picked == 0 && !StringUtils.hasText(area)) {
      throw refused(REQUIRED);
    }
    if (picked > MAX) {
      throw refused(TOO_MANY);
    }
  }

  private static FieldValidationException refused(String message) {
    return new FieldValidationException("Report cannot be run", Map.of(FIELD, message));
  }
}
