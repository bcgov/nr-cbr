package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.STRUCTURE_TYPE_CLASS_CODE}.
 */
@Repository
public interface StructureTypeClassCodeRepository
    extends JpaRepository<StructureTypeClassCodeEntity, String> {

  /**
   * The structure types and classes a user may pick, in the order the dropdown should show them.
   *
   * <p>This is {@code CBR.FIND_STRUCTURE_TYPES} plus the filter its caller applies. Two things
   * about it are worth stating, because both look wrong beside the neighbouring lookups:
   *
   * <ul>
   *   <li><b>An inner join to the xref, so a code with no display-order row does not appear.</b>
   *       Same arrangement as
   *       {@link CrossingSiteStatusCodeRepository#findAllInDisplayOrder()}: the xref is how a code
   *       is published to the UI at all, and a left join would put a half-configured one in front
   *       of users.</li>
   *   <li><b>Expired codes <em>are</em> filtered out here</b>, where the site-status, inspection-
   *       status and inspection-report-status lists deliberately keep theirs. That is not an
   *       inconsistency introduced in the rebuild — it is legacy's. The filter lives in
   *       {@code CodeTableServiceManager.getByCodeTable}, which takes an {@code excludeExpired}
   *       flag, and {@code InspectionSearchAction} passes {@code false} for inspection types and
   *       report statuses while taking the default {@code true} for this one. So on a single form,
   *       one list hides retired codes and two do not. The consequence is real: a structure whose
   *       type has since been retired cannot be found by its type. It is kept because the whole
   *       point of this screen is to answer the same questions the old one did, and changing which
   *       codes are offered changes which searches are possible — but it is a defect to raise, not
   *       a rule to copy onto the next lookup.</li>
   * </ul>
   *
   * <p><b>"Current" is evaluated once per pod, not per request.</b> The result is cached for the
   * life of the process by {@link ca.bc.gov.nrs.cbr.service.v1.ConfigurationService}, so a code that
   * expires while the pod is up stays in the list until it restarts. Harmless for data a DBA
   * migration changes a few times a decade, and the same trade the cache makes everywhere else
   * here — but this is the one list where the cached value depends on the clock.
   *
   * <p>Written as JPQL rather than a derived name because the join is between two entities with no
   * association mapped between them, and because the legacy SQL sits right beside it in
   * {@code nr-mof-db}: keeping the two shapes comparable is worth more than a method name that
   * reads as a sentence.
   */
  @Query("""
      SELECT code
        FROM StructureTypeClassCodeEntity code
        JOIN StructureTypeClassXrefEntity xref
          ON xref.structureTypeClassCode = code.structureTypeClassCode
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY xref.displayOrder
      """)
  List<StructureTypeClassCodeEntity> findAllCurrentInDisplayOrder();
}
