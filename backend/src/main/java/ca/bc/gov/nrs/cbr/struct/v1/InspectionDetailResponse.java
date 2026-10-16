package ca.bc.gov.nrs.cbr.struct.v1;

import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One inspection, as its page shows it — legacy's {@code showInspection.do} in UPDATE mode, read
 * only. The structure and its site are the structure page's to give ({@code structureId}); the
 * questions it was asked, Add Inspection's form endpoint.
 *
 * @param siteAtTimeOfInspection the structure's site when it was inspected
 * @param statusHistory          every status it has had, newest first — legacy's View History
 * @param review                 the P.Eng's review; empty values until it is reviewed
 * @param items                  the questions answered, stringers included
 * @param offline                out on the offline client: legacy offers no link to it
 */
public record InspectionDetailResponse(
    String id,
    String structureId,
    String structureName,
    String siteAtTimeOfInspection,
    CodeValue type,
    CodeValue status,
    List<StatusChange> statusHistory,
    LocalDate inspectionDate,
    String inspectorName,
    String inspectorCompanyName,
    Long cost,
    boolean professionalInspectionRequired,
    String inspectionComment,
    Review review,
    Hazards hazards,
    List<Item> items,
    UserAudit entered,
    UserAudit updated,
    boolean offline) {

  /** One row of the status history: the status, who set it and when. */
  public record StatusChange(CodeValue status, String changedBy, LocalDateTime changedAt) {}

  /**
   * The P.Eng's part: who reviewed it and when, the load rating recorded with it and its reason,
   * and the P.Eng's comments.
   */
  public record Review(
      String reviewedBy,
      LocalDate reviewedDate,
      BigDecimal loadRating,
      CodeValue loadRatingReason,
      String loadRatingReasonComment,
      String pengComment) {}

  /** The Hydraulic Hazards tab. Metres; each hazard Y as true. */
  public record Hazards(
      BigDecimal presentWaterDepth,
      BigDecimal presentWaterWidth,
      BigDecimal highWaterClearance,
      boolean highWater,
      boolean debris,
      boolean scour,
      boolean ice,
      boolean aggradation,
      String comment) {}

  /** One answered question, where it was asked: a span, a pier, a stringer. */
  public record Item(
      String subsectionCode,
      String questionCode,
      String spanId,
      String pierId,
      Long stringerNumber,
      String answer,
      String comment) {}
}
