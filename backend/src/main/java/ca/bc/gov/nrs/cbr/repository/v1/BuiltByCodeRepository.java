package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.BuiltByCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.BUILT_BY_CODE} — the Built For select on the Details tab's Structure card. */
@Repository
public interface BuiltByCodeRepository extends JpaRepository<BuiltByCodeEntity, String> {

  /** Every code, expired ones too, by description — the Built For filter on Structure Search. */
  List<BuiltByCodeEntity> findAllByOrderByDescriptionAsc();

  /** The current codes, by description; expired ones are left out, as legacy's select left them. */
  @Query("""
      SELECT code
        FROM BuiltByCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<BuiltByCodeEntity> findAllCurrent();
}
