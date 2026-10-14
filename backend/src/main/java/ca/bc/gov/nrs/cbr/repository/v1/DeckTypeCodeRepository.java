package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.DeckTypeCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.DECK_TYPE_CODE} — the Deck Type select on the Details tab's Bridge card. */
@Repository
public interface DeckTypeCodeRepository extends JpaRepository<DeckTypeCodeEntity, String> {

  /** The current codes, by description; expired ones are left out, as legacy's select left them. */
  @Query("""
      SELECT code
        FROM DeckTypeCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<DeckTypeCodeEntity> findAllCurrent();
}
