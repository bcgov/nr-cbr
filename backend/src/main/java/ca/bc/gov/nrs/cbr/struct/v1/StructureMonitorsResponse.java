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

  /**
   * A page of monitoring items, and how many the table leaves out because they predate the
   * superstructure.
   *
   * @param beforeInstallCount in the view chosen, those raised by an inspection dated on or before
   *                           1 January of the year the superstructure was installed — the ones
   *                           listed only when asked for; 0 when no install year is recorded
   */
  public record Listing(PagedResponse<Monitor> page, long beforeInstallCount) {}

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
