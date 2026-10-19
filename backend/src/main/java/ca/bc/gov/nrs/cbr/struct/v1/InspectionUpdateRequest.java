package ca.bc.gov.nrs.cbr.struct.v1;

import java.time.LocalDate;

/**
 * The Inspection card of an inspection's page, as edited — legacy's Inspection tab, less its type,
 * which is never changed, and less a P.Eng's review, which {@link InspectionReviewRequest} carries.
 *
 * @param statusCode                     In Progress (PRO) or Submitted (SUB), or the status it has
 * @param inspectionDate                 not in the future; one inspection of a structure a day
 * @param professionalInspectionRequired a Level 2 user's to change
 */
public record InspectionUpdateRequest(
    String statusCode,
    LocalDate inspectionDate,
    String inspectorName,
    String inspectorCompanyName,
    Long cost,
    Boolean professionalInspectionRequired) {}
