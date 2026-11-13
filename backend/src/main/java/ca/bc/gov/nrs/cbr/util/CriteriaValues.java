package ca.bc.gov.nrs.cbr.util;

import java.util.Arrays;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * The values of a criterion that may hold several — a multi-select on a search or report form.
 *
 * <p>The form sends the picks comma-separated in the one parameter, {@code siteStatusCode=ACT,BAR},
 * and Spring binds repeated parameters ({@code siteStatusCode=ACT&siteStatusCode=BAR}) into a
 * {@code String} the same way, so the criteria records keep their {@code String} fields and a
 * single value still means what it always did. No code or org unit number contains a comma.
 */
public final class CriteriaValues {

  private static final String SEPARATOR = ",";

  /**
   * The "Is null" pick — records with no value at all. Offered on BCTS Business Area (UAT pick
   * list, 2026-11); never an org unit number, so it cannot clash with one.
   */
  public static final String NONE = "NONE";

  private CriteriaValues() {}

  /** The non-blank values, trimmed, in the order given, without repeats; empty when unset. */
  public static List<String> of(String value) {
    if (!StringUtils.hasText(value)) {
      return List.of();
    }
    return Arrays.stream(value.split(SEPARATOR))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .distinct()
        .toList();
  }
}
