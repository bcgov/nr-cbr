package ca.bc.gov.nrs.cbr.struct.v1;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/**
 * The 19 criteria the Inspection Search screen offers, bound from query parameters.
 *
 * <p>Field-for-field with the legacy `inspection_search.jsp` form, with
 * {@code InspectionSearchForm.createSearch()}, and with the frontend's `InspectionSearchCriteria`,
 * so a criterion keeps one name from the input box to the {@code WHERE} clause.
 *
 * <p>Bound as one object rather than 21 {@code @RequestParam}s, and built with {@code @Builder},
 * for the reasons set out on {@link SiteSearchCriteria}: at this width a transposed pair of
 * same-typed neighbours compiles and returns the wrong rows.
 *
 * <p><b>Road Responsibility is deliberately absent.</b> {@code InspectionSearchForm} still carries
 * {@code roadResponsibilityCode} — it validates it, maps it into the search and reads it back — but
 * the block that renders the dropdown is commented out in the JSP, so no user has been able to set
 * it for years. Porting it would add a criterion the screen it came from does not have.
 */
@Builder
public record InspectionSearchCriteria(
    String siteId,
    /** "Structure #" on the legacy form, though the value is {@code CROSSING_STRUCTURE_NAME}. */
    String structureName,
    String structureTypeClassCode,
    /** Close Proximity Inspection Required? — a one-way filter, see the specification. */
    Boolean closeProximity,
    String inspectionTypeCode,
    /** Most Recent Inspections Only? */
    Boolean mostRecentInspections,
    String inspectionReportStatusCode,
    /** Previously Reviewed Inspections Only? — legacy {@code findChangedReviewed}. */
    Boolean findChangedReviewed,
    String forestFileId,
    /** "Br." on the legacy form — the road section, shown beside Project File ID#. */
    String roadSectionId,
    /** Include Inspections for Structures at Previous Sites? — changes what {@link #siteId} means. */
    Boolean findMovedStructures,
    String forestServiceRoad,
    /** Inclusive month bound, {@code yyyy/MM}. */
    @Pattern(regexp = MONTH, message = INVALID_MONTH)
    String inspectionDateStart,
    /** Inclusive month bound, {@code yyyy/MM}. */
    @Pattern(regexp = MONTH, message = INVALID_MONTH)
    String inspectionDateEnd,
    /** Forest District. */
    String orgUnitNo,
    String managementOrgUnitNo,
    /** BCTS Business Area — a third, separate org-unit filter, not a child of the district. */
    String businessAreaOrgUnitNo,
    String inspectorName,
    String inspectionReviewerId,
    String sortBy
) {

  /**
   * An unset value, or a month as {@code yyyy/M} or {@code yyyy/MM}.
   *
   * <p>A one-digit month is accepted because legacy's {@code SimpleDateFormat("yyyy/MM")} accepts
   * it, and Oracle's {@code TO_DATE(:1, 'yyyy/MM')} does too.
   *
   * <p>The empty alternative matters: these arrive from a form, so an untouched field is {@code ""}
   * rather than absent, and {@code @Pattern} only skips {@code null}.
   *
   * <p>Paired with {@code MONTH_PATTERN} in the frontend's {@code InspectionSearch/validation.ts}.
   * Both must say the same thing — the frontend so the user is told before submitting, this one
   * because the frontend is not the only caller.
   */
  public static final String MONTH = "^$|^\\d{4}/(0?[1-9]|1[0-2])$";

  static final String INVALID_MONTH = "Enter a month as yyyy/mm, e.g. 2026/01";

  /** Legacy {@code sortBy}: structure name ascending, then inspection date descending. */
  public static final String STRUCTURE_ID_DATE_SORT = "structureIdDateSort";

  /** Legacy {@code sortBy}: project file, road section, kilometre ascending, then date descending. */
  public static final String PROJECT_BRANCH_KM_DATE_SORT = "projectBranchKmDateSort";

  /**
   * True when nothing is set that would narrow the search.
   *
   * <p><b>Nothing refuses a search on this.</b> Legacy did — {@code InspectionSearchForm.validate()}
   * raises {@code errors.search.select} — but that guard existed because the legacy query was
   * unpaginated, and it is not reproduced here or on Site Search. This survives as the one honest
   * way to say "the caller asked for everything" in a log line, which is worth being able to see.
   *
   * <p>Two fields are deliberately not counted. {@link #sortBy} is not a criterion — it always has
   * a value, and legacy does not count it either. {@link #findMovedStructures} is not one either: it
   * changes what {@link #siteId} matches against rather than narrowing anything, so on its own it
   * does nothing at all.
   */
  public boolean isEmpty() {
    return isBlank(siteId)
        && isBlank(structureName)
        && isBlank(structureTypeClassCode)
        && isBlank(inspectionTypeCode)
        && isBlank(inspectionReportStatusCode)
        && isBlank(forestFileId)
        && isBlank(roadSectionId)
        && isBlank(forestServiceRoad)
        && isBlank(inspectionDateStart)
        && isBlank(inspectionDateEnd)
        && isBlank(orgUnitNo)
        && isBlank(managementOrgUnitNo)
        && isBlank(businessAreaOrgUnitNo)
        && isBlank(inspectorName)
        && isBlank(inspectionReviewerId)
        && !Boolean.TRUE.equals(closeProximity)
        && !Boolean.TRUE.equals(mostRecentInspections)
        && !Boolean.TRUE.equals(findChangedReviewed);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
