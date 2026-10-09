package ca.bc.gov.nrs.cbr.struct.v1;

import java.time.LocalDate;

/**
 * A close proximity inspection that was done — the Inspections tab's Completed Close Proximity
 * Inspections, added by a P.Eng.
 *
 * @param completedDate when it was done; required, any date
 */
public record CloseProximityInspectionRequest(LocalDate completedDate) {}
