package ca.bc.gov.nrs.cbr.struct.v1;

import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import java.time.LocalDate;

/**
 * The structure page's Monitoring tab — {@code GET /api/v1/structures/{structureId}/monitors}, a
 * page of {@link Monitor}s; legacy's {@code monitorTab.jsp}.
 *
 * <p>The frontend's {@code monitorsResponse.ts} is the other half of this contract.
 */
public final class StructureMonitorsResponse {

  private StructureMonitorsResponse() {}

  /** Which items the tab lists — legacy's "Choose Viewing Option". */
  public enum View {
    /** Suggested or required. Legacy's default. */
    OUTSTANDING,
    ALL
  }

  /**
   * One monitoring item.
   *
   * @param frequencyComment free text alongside the frequency
   */
  public record Monitor(
      String id,
      Long number,
      CodeValue status,
      UserAudit suggested,
      UserAudit required,
      UserAudit completed,
      String inspectionId,
      LocalDate inspectionDate,
      String description,
      CodeValue frequency,
      String frequencyComment) {}
}
