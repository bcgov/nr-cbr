package ca.bc.gov.nrs.cbr.struct.v1;

import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import java.time.LocalDate;

/**
 * A page of the structure page's inspection table —
 * {@code GET /api/v1/structures/{structureId}/inspections}; legacy's table on
 * {@code inspectionTab.jsp}.
 *
 * @param page               the inspections on this page, newest first
 * @param beforeInstallCount inspections dated on or before 1 January of the year the superstructure
 *                           was installed — those the table leaves out until asked
 */
public record StructureInspectionsResponse(
    PagedResponse<Inspection> page, long beforeInstallCount) {

  /**
   * One inspection.
   *
   * @param siteId       the site the structure stood on when inspected; legacy showed the site it
   *                     stands on now
   * @param reviewedDate the P.Eng's review date
   * @param reviewedBy   the reviewer's name
   * @param viewable     false while the inspection is offline ({@code OFL}), when legacy offers no
   *                     View link
   */
  public record Inspection(
      String id,
      CodeValue type,
      LocalDate inspectionDate,
      String siteId,
      CodeValue status,
      LocalDate reviewedDate,
      String reviewedBy,
      String inspectorName,
      boolean viewable) {}
}
