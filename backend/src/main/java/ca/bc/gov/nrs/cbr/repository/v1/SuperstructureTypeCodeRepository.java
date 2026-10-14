package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.SuperstructureTypeCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.SUPERSTRUCTURE_TYPE_CODE}. */
@Repository
public interface SuperstructureTypeCodeRepository
    extends JpaRepository<SuperstructureTypeCodeEntity, String> {

  /**
   * Every code, ordered by description — what legacy's {@code CodeTableServiceManager} loads for
   * the Superstructure Type select on Structure Search, with no filter on the effective
   * dates.
   */
  List<SuperstructureTypeCodeEntity> findAllByOrderByDescriptionAsc();

  /** The current codes, by description — the Details tab's Bridge card select. */
  @Query("""
      SELECT code
        FROM SuperstructureTypeCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<SuperstructureTypeCodeEntity> findAllCurrent();
}
