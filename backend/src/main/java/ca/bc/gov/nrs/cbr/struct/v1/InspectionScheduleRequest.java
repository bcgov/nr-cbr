package ca.bc.gov.nrs.cbr.struct.v1;

import java.time.LocalDate;

/**
 * An edit to the structure's inspection schedule — the Inspection Schedule card of the structure
 * page's Inspections tab, legacy's fields above its inspection table.
 *
 * <p>As legacy: a Level 2 user (and up) sets all of it; a Level 1 user only the frequency, the rest
 * kept as stored — see {@code StructureInspectionsService.updateSchedule}.
 *
 * @param closeProximityRequired whether a close proximity inspection is required
 * @param closeProximityEquipmentCode {@code SPECIAL_EQUIPMENT_RQMT_CODE}, or blank for none; kept
 *     as stored while no close proximity inspection is required
 * @param nextCloseProximityDate the next planned close proximity inspection; kept as stored while
 *     none is required
 * @param nextRoutineDate        the next planned routine inspection
 * @param routineFrequencyYears  years between routine inspections, 1 to 6
 */
public record InspectionScheduleRequest(
    boolean closeProximityRequired,
    String closeProximityEquipmentCode,
    LocalDate nextCloseProximityDate,
    LocalDate nextRoutineDate,
    Integer routineFrequencyYears) {}
