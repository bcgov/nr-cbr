package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import java.time.LocalDate;

/**
 * Where the Repairs and Monitoring tabs stop listing items by default: 1 January of the year the
 * superstructure was installed. An item raised by an inspection dated on or before it is left out
 * until asked for — legacy's "Show Inspections before the Superstructure Install Date"
 * ({@code StructureAction.java:2758-2781, 3082-3100}).
 *
 * <p>With no install year recorded legacy lists everything ({@link #none()}), unlike its
 * inspection table, which falls back to 1900.
 *
 * @param installed the cut-off; a placeholder when {@link #none()}, never null
 * @param none      true when the structure has no install year, so nothing is left out
 */
record InstallCutoff(LocalDate installed, boolean none) {

  /** Bound in place of a cut-off when there is none; the filter is off, so it is never compared. */
  private static final LocalDate UNUSED = LocalDate.of(1900, 1, 1);

  static InstallCutoff of(CrossingStructureEntity structure) {
    Integer year = structure.getYearBuilt();
    return year == null
        ? new InstallCutoff(UNUSED, true)
        : new InstallCutoff(LocalDate.of(year, 1, 1), false);
  }
}
