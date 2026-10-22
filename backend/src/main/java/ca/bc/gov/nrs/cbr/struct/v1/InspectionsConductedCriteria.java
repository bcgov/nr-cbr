package ca.bc.gov.nrs.cbr.struct.v1;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/**
 * The Inspections Conducted reports' criteria (legacy CBR002 Summary and CBR001 Detail), as query
 * parameters. Every one optional; {@code inspectionTypeCode} is the Detail's only.
 *
 * <p>Inspection Date is a range of months, {@code yyyy/mm}, as on Inspection Search — the start
 * month from its 1st, the end month through its last day.
 */
@Builder
public record InspectionsConductedCriteria(
    String structureName,
    String siteTypeCode,
    String inspectionReportStatusCode,
    String inspectionTypeCode,
    @Pattern(regexp = InspectionSearchCriteria.MONTH, message = InspectionSearchCriteria.INVALID_MONTH)
    String inspectionDateStart,
    @Pattern(regexp = InspectionSearchCriteria.MONTH, message = InspectionSearchCriteria.INVALID_MONTH)
    String inspectionDateEnd,
    String orgUnit,
    String managementOrgUnit,
    String businessAreaOrgUnit,
    String forestFileId,
    String roadSectionId,
    String clientNumber,
    String clientLocationCode,
    String inspectorName,
    Boolean closeProximity,
    Boolean mostRecentInspections,
    Boolean professionalInspection,
    InspectionsConductedSort sortBy) {

  /** The order chosen, or the report's own default. */
  public InspectionsConductedSort sortOr(InspectionsConductedSort fallback) {
    return sortBy == null ? fallback : sortBy;
  }
}
