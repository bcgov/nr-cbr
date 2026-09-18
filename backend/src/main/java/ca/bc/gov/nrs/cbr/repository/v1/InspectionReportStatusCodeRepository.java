package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.INSPECTION_REPORT_STATUS_CODE}.
 */
@Repository
public interface InspectionReportStatusCodeRepository
    extends JpaRepository<InspectionReportStatusCodeEntity, String> {

  /**
   * Every inspection report status, by description.
   *
   * <p>This is {@code CBR.FIND_INSPCTN_RPT_STATUSES} — a plain {@code SELECT … ORDER BY
   * DESCRIPTION} over the one table, with no join and no date filter — asked for with the expiry
   * filter switched off, which is how the search form requests it.
   *
   * <p>Both halves of that matter more here than on the other code lists:
   *
   * <ul>
   *   <li><b>{@code ACC} is expired and still in use.</b> Saving an inspection that carries it
   *       silently rewrites it to {@code RVD}, so it survives only on rows nobody has re-saved —
   *       which is exactly the set a user would go looking for. Filtering the list would make them
   *       unfindable by status. Both codes are also still read as terminal by the outbound LRM
   *       views (cbr-workflows.local.md §1).</li>
   *   <li><b>{@code OFL} is included.</b> Legacy's expiry filter has a second job — when it runs it
   *       also drops {@code OFL}, to keep "offline" out of the status a user can set on a form.
   *       Searching is the opposite case: an offline inspection is one of the things this screen
   *       exists to find, and the results table treats those rows specially. Requesting the list
   *       unfiltered is what keeps it offered.</li>
   * </ul>
   *
   * <p>So a form that <em>sets</em> a status needs a different query from this one — filtered, and
   * without {@code OFL}. It should be added as its own method rather than by narrowing this.
   */
  List<InspectionReportStatusCodeEntity> findAllByOrderByDescriptionAsc();
}
