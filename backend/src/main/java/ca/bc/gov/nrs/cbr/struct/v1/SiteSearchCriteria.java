package ca.bc.gov.nrs.cbr.struct.v1;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/**
 * The 17 criteria the Site Search screen offers, bound from query parameters.
 *
 * <p>Field-for-field with the legacy `site_search.jsp` form and with the frontend's
 * `SiteSearchCriteria`, so a criterion keeps one name from the input box to the {@code WHERE}
 * clause. Every field is optional — the legacy search runs with none set and returns everything,
 * and there is no scoping to narrow it: any CBR user may search every site in the province
 * (cbr-auth-and-roles.local.md §3.3).
 *
 * <p><b>Bound as one object rather than 17 {@code @RequestParam}s</b>, which is a deliberate
 * departure from nr-frep's flat parameter lists. At this count a flat signature has to be repeated
 * identically in the interface, the controller override and the service call, and a transposition
 * between two same-typed neighbours — {@code kiloStart}/{@code kiloEnd},
 * {@code forestFileId}/{@code roadSectionId} — compiles and returns the wrong rows silently. The
 * wire contract is unchanged: Spring binds these from the same query string either way.
 *
 * <p>All fields are {@link String} even where the column is numeric. The criteria are pasted,
 * partial and sometimes wildcarded, and {@code CBR.FIND_SITES_BY_CRITERIA} takes a
 * {@code CBR_VARCHAR2_ARRAY} of binds regardless — parsing here would reject input the legacy screen
 * accepts.
 *
 * <p><b>{@code @Builder} because there are twenty components and seventeen of them are
 * {@link String}.</b> Spring binds instances from the query string and never calls the constructor
 * positionally, but tests and any future caller do, and at this width a transposed pair of
 * same-typed neighbours — {@code kiloStart}/{@code kiloEnd},
 * {@code forestFileId}/{@code roadSectionId} — compiles cleanly and returns the wrong rows. That is
 * the same failure this record was shaped to avoid in the service signature; it should not be
 * reintroduced at the point of construction.
 *
 * <p>The four kilometre bounds are the exception: they are compared numerically, so a value that is
 * not a number cannot mean anything and is rejected with a 400 naming the field. Legacy checks the
 * same thing — {@code SiteSearchForm.validate()} runs each through {@code new Double(...)} — but
 * reports it as one page-level "Kilometres must be numeric" message for the pair. Per-field is the
 * only difference.
 */
@Builder
public record SiteSearchCriteria(
    String siteId,
    String siteStatusCode,
    String forestFileId,
    /** "Br." on the legacy form — the road section, shown beside Project File ID#. */
    String roadSectionId,
    String structureInspectionStatusCode,
    String forestServiceRoad,
    /** Designated Maintainer Client Number. */
    String clientNumber,
    String crossingName,
    String clientLocationCode,
    /** Forest District. */
    String orgUnit,
    /** Kilometres from — {@code CROSSING_SITE.POINT_OF_COMMENCEMENT_DISTANCE}, inclusive. */
    @Pattern(regexp = DECIMAL, message = INVALID_KILOMETRES)
    String kiloStart,
    /** Kilometres to, inclusive. */
    @Pattern(regexp = DECIMAL, message = INVALID_KILOMETRES)
    String kiloEnd,
    String managementOrgUnit,
    /** User kilometres from — {@code CROSSING_SITE.USER_KM}, inclusive. */
    @Pattern(regexp = DECIMAL, message = INVALID_USER_KILOMETRES)
    String userKmStart,
    /** User kilometres to, inclusive. */
    @Pattern(regexp = DECIMAL, message = INVALID_USER_KILOMETRES)
    String userKmEnd,
    String specialAccessCode,
    Boolean incomplete,
    String siteTypeCode,
    Boolean capitalRoad,
    /** Designated Maintainer, by name rather than client number. */
    String primaryUserName
) {

  /**
   * An unset value, or a decimal that fits {@code NUMBER(8,2)} — up to six integer digits and two
   * decimal places.
   *
   * <p>Narrower than legacy, which accepts anything {@code new Double(...)} parses: {@code 1e3},
   * a leading {@code -}, and values far too large for the column all pass its check and then either
   * match nothing or fail in Oracle. Bounding the pattern to the column means a value that gets past
   * here can actually be compared against one.
   *
   * <p>The empty alternative matters: these arrive from a form, so an untouched field is {@code ""}
   * rather than absent, and {@code @Pattern} only skips {@code null}.
   *
   * <p>Paired with {@code isDecimal} in the frontend's {@code SiteSearch/validation.ts}. Both must
   * say the same thing — the frontend so the user is told before submitting, this one because the
   * frontend is not the only caller.
   */
  public static final String DECIMAL = "^$|^\\d{1,6}(\\.\\d{1,2})?$";

  static final String INVALID_KILOMETRES = "Kilometres must be a number, e.g. 12.5";
  static final String INVALID_USER_KILOMETRES = "User Kilometres must be a number, e.g. 12.5";

  /** True when no criterion is set — the legacy "search everything" case. */
  public boolean isEmpty() {
    return isBlank(siteId)
        && isBlank(siteStatusCode)
        && isBlank(forestFileId)
        && isBlank(roadSectionId)
        && isBlank(structureInspectionStatusCode)
        && isBlank(forestServiceRoad)
        && isBlank(clientNumber)
        && isBlank(crossingName)
        && isBlank(clientLocationCode)
        && isBlank(orgUnit)
        && isBlank(kiloStart)
        && isBlank(kiloEnd)
        && isBlank(managementOrgUnit)
        && isBlank(userKmStart)
        && isBlank(userKmEnd)
        && isBlank(specialAccessCode)
        && isBlank(siteTypeCode)
        && isBlank(primaryUserName)
        && !Boolean.TRUE.equals(incomplete)
        && !Boolean.TRUE.equals(capitalRoad);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
