package ca.bc.gov.nrs.cbr.struct.v1;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A new inspection — Add Inspection. Saved In Progress or Submitted: In Progress, the date,
 * inspector and company may wait until it is submitted. Reviewed and Rejected are a P.Eng's, on the
 * saved inspection.
 *
 * @param structureId                    the structure inspected; complete
 * @param typeCode                       ROUT (Level 1 and up) or UNP (Level 0 and up)
 * @param statusCode                     PRO In Progress or SUB Submitted
 * @param inspectionDate                 not in the future; one inspection of a structure a day
 * @param inspectorName                  at most 255
 * @param inspectorCompanyName           at most 255
 * @param cost                           whole dollars, 0 to 999,999
 * @param professionalInspectionRequired a Level 2 user's to set
 * @param inspectionComment              the inspector's, at most 4000
 * @param presentWaterDepth              Estimated Present Water Level Depth (m)
 * @param presentWaterWidth              Estimated Present Water Level Width (m)
 * @param highWaterClearance             Estimated High Water Level Clearance (m)
 * @param hazardComment                  at most 2000
 * @param items                          the questions answered on the Condition tab
 */
public record InspectionCreateRequest(
    Long structureId,
    String typeCode,
    String statusCode,
    LocalDate inspectionDate,
    String inspectorName,
    String inspectorCompanyName,
    Long cost,
    Boolean professionalInspectionRequired,
    String inspectionComment,
    BigDecimal presentWaterDepth,
    BigDecimal presentWaterWidth,
    BigDecimal highWaterClearance,
    Boolean highWater,
    Boolean debris,
    Boolean scour,
    Boolean ice,
    Boolean aggradation,
    String hazardComment,
    List<Item> items) {

  /**
   * One answered question.
   *
   * @param spanId         for a question asked of each span
   * @param pierId         for a Piers question
   * @param stringerNumber for a question asked of each stringer
   * @param answer         an answer code, or typed text where the question lists none
   * @param comment        required for a Poor or Unrated answer; at most 2000
   */
  public record Item(String subsectionCode, String questionCode, Long spanId, Long pierId,
      Long stringerNumber, String answer, String comment) {}
}
