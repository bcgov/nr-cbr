package ca.bc.gov.nrs.cbr.struct.v1;

import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Comment;
import java.time.LocalDate;
import java.util.List;

/**
 * The top of the structure page's Inspections tab — its planned-inspection comments, when it is
 * next to be inspected, and its completed close proximity inspections;
 * {@code GET /api/v1/structures/{structureId}/inspection-schedule}, legacy's
 * {@code inspectionTab.jsp} above the inspection table.
 *
 * <p>The frontend's {@code inspectionsResponse.ts} is the other half of this contract.
 *
 * @param plannedInspectionComments   newest first
 * @param closeProximityRequired      {@code CLOSE_PROXIMITY_IND = 'Y'}
 * @param closeProximityEquipment     special equipment a close proximity inspection needs
 * @param nextCloseProximityDate      the next planned close proximity inspection
 * @param nextRoutineDate             the next planned routine inspection
 * @param routineFrequencyYears       years between routine inspections, 1 to 6
 * @param completedCloseProximity     newest first
 */
public record StructureInspectionScheduleResponse(
    List<Comment> plannedInspectionComments,
    boolean closeProximityRequired,
    CodeValue closeProximityEquipment,
    LocalDate nextCloseProximityDate,
    LocalDate nextRoutineDate,
    Integer routineFrequencyYears,
    List<CloseProximityInspection> completedCloseProximity) {

  /** A close proximity inspection that was done, and who recorded it. */
  public record CloseProximityInspection(String id, LocalDate completed, String userId) {}
}
