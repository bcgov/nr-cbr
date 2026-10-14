package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.OpnBtomClvrtSubstrctreCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.OPN_BTOM_CLVRT_SUBSTRCTRE_CODE} — the Open Bottom Substructure select on the Details tab's Culvert card. */
@Repository
public interface OpnBtomClvrtSubstrctreCodeRepository extends JpaRepository<OpnBtomClvrtSubstrctreCodeEntity, String> {

  /** The current codes, by description; expired ones are left out, as legacy's select left them. */
  @Query("""
      SELECT code
        FROM OpnBtomClvrtSubstrctreCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<OpnBtomClvrtSubstrctreCodeEntity> findAllCurrent();
}
