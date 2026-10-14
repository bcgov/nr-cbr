package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StrctreLoadRatingRsnCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.STRCTRE_LOAD_RATING_RSN_CODE} — why a load rating was set. */
@Repository
public interface StrctreLoadRatingRsnCodeRepository extends JpaRepository<StrctreLoadRatingRsnCodeEntity, String> {

  /** The current codes, by description; expired ones are left out, as legacy's lists left them. */
  @Query("""
      SELECT code
        FROM StrctreLoadRatingRsnCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<StrctreLoadRatingRsnCodeEntity> findAllCurrent();
}
