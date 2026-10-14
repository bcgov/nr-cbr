package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.AbutmentCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.ABUTMENT_CODE} — the Left and Right Abutment selects on the Details tab's Bridge card. */
@Repository
public interface AbutmentCodeRepository extends JpaRepository<AbutmentCodeEntity, String> {

  /** The current codes, by description; expired ones are left out, as legacy's select left them. */
  @Query("""
      SELECT code
        FROM AbutmentCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<AbutmentCodeEntity> findAllCurrent();
}
