package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.CROSSING_SITE_STATUS_CODE}.
 *
 * <p>The first Spring Data repository in CBR. It sits alongside
 * {@link ca.bc.gov.nrs.cbr.repository.AbstractCbrRepository}, not in place of it: the stored
 * procedures stay the route for anything with business logic behind it, and the two live in
 * different packages so which one a class uses is visible from its imports. A code table has no
 * logic behind it — {@code CBR.FIND_SITE_STATUSES} is a bare {@code SELECT} — so it is a plain
 * read, and Hibernate does it with less ceremony than a REF CURSOR.
 */
@Repository
public interface CrossingSiteStatusCodeRepository
    extends JpaRepository<CrossingSiteStatusCodeEntity, String> {

  /**
   * Every site status, in the order the dropdown should show them.
   *
   * <p>This is {@code CBR.FIND_SITE_STATUSES} as JPQL, and it keeps two of that procedure's
   * decisions that are easy to mistake for oversights:
   *
   * <ul>
   *   <li><b>An inner join to the xref, so a status with no display-order row does not appear.</b>
   *       That is the legacy behaviour — the xref is how a code is published to the UI at all,
   *       which makes "add the code, then add the xref row" the two-step the DBA migrations
   *       follow. A left join here would put a half-configured code in front of users.</li>
   *   <li><b>No filter on {@code EFFECTIVE_DATE}/{@code EXPIRY_DATE}.</b> Everywhere else in CBR an
   *       expired code is hidden, and this looks like the one place that forgot. It is not: this
   *       list populates a <em>search</em> filter, and sites recorded years ago still carry
   *       statuses that have since expired. Filtering would make those sites unfindable by status.
   *       A data-entry form is the opposite case and needs the filtered query — which does not
   *       exist yet, and should be added as its own method rather than by narrowing this one.</li>
   * </ul>
   *
   * <p>Written as JPQL rather than a derived name because the join is between two entities with no
   * association mapped between them, and because the legacy SQL is sitting right next to it in
   * {@code nr-mof-db}: keeping the shapes comparable is worth more here than a method name that
   * reads as a sentence.
   */
  @Query("""
      SELECT code
        FROM CrossingSiteStatusCodeEntity code
        JOIN CrossingSiteStatusXrefEntity xref
          ON xref.crossingSiteStatusCode = code.crossingSiteStatusCode
       ORDER BY xref.displayOrder
      """)
  List<CrossingSiteStatusCodeEntity> findAllInDisplayOrder();
}
