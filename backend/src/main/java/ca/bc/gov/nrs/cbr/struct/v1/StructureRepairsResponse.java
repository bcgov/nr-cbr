package ca.bc.gov.nrs.cbr.struct.v1;

import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import java.time.LocalDate;

/**
 * The structure page's Repairs tab — {@code GET /api/v1/structures/{structureId}/repairs}, a page
 * of {@link Repair}s; legacy's {@code repairTab.jsp}.
 *
 * <p>The frontend's {@code repairsResponse.ts} is the other half of this contract.
 */
public final class StructureRepairsResponse {

  private StructureRepairsResponse() {}

  /** Which repairs the tab lists — legacy's "Choose Viewing Option". */
  public enum View {
    /** Suggested, required or carried forward, and not carried forward again. Legacy's default. */
    OUTSTANDING,
    ALL
  }

  /**
   * One repair.
   *
   * @param suggested     who suggested it and when, or null
   * @param required      who required it and when, or null
   * @param completed     who recorded it complete and when, or null
   * @param unit          what the quantity is counted in, from the repair type
   * @param estimate      whole dollars
   * @param actualCost    whole dollars
   */
  public record Repair(
      String id,
      Long number,
      CodeValue status,
      CodeValue type,
      UserAudit suggested,
      UserAudit required,
      UserAudit completed,
      String inspectionId,
      LocalDate inspectionDate,
      CodeValue priority,
      LocalDate completedDate,
      Long estimate,
      Long actualCost,
      Long quantity,
      String unit,
      String description) {}
}
