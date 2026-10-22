package ca.bc.gov.nrs.cbr.struct.v1;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/**
 * The criteria of the Inspections Due reports (legacy CBR004 Summary and CBR003 Detailed) and the
 * Inspections – Blank Report (legacy CBR010), as query parameters. Every one optional;
 * {@code structureName} is the Blank's only, {@code noNextPlannedInspectionDate} and
 * {@code sortBy} the Due reports'.
 *
 * <p>Next Planned Inspection Date is a range of days, {@code yyyy/mm/dd} as legacy's, both ends
 * included. {@code specialEquipmentCode} {@value #NO_SPECIAL_EQUIPMENT} asks for structures that
 * need none — legacy's "None".
 */
@Builder
public record InspectionsDueCriteria(
    String structureName,
    @Pattern(regexp = DAY, message = INVALID_DAY)
    String nextPlannedInspectionDateStart,
    @Pattern(regexp = DAY, message = INVALID_DAY)
    String nextPlannedInspectionDateEnd,
    Boolean noNextPlannedInspectionDate,
    String siteTypeCode,
    String structureTypeClassCode,
    @Pattern(regexp = "^$|^[YN]$", message = "Close Proximity is Y or N")
    String closeProximity,
    String structureInspectionStatusCode,
    String siteStatusCode,
    String orgUnit,
    String managementOrgUnit,
    String businessAreaOrgUnit,
    String forestFileId,
    String roadSectionId,
    String clientNumber,
    String clientLocationCode,
    String specialAccessCode,
    String specialEquipmentCode,
    InspectionsDueSort sortBy) {

  /** A day, {@code yyyy/mm/dd}; a one-digit month or day is accepted, as legacy's parser did. */
  public static final String DAY = "^$|^\\d{4}/(0?[1-9]|1[0-2])/(0?[1-9]|[12]\\d|3[01])$";
  static final String INVALID_DAY = "Enter a date as yyyy/mm/dd, e.g. 2026/01/31";

  /** Special Equipment Requirements "None": structures with no requirement. */
  public static final String NO_SPECIAL_EQUIPMENT = "NONE";

  /** The order chosen, or legacy's default: Forest Service Road. */
  public InspectionsDueSort sortOrDefault() {
    return sortBy == null ? InspectionsDueSort.FOREST_SERVICE_ROAD : sortBy;
  }
}
