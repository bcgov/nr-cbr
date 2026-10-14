package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.EngineeredClvrtMaterialCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.ENGINEERED_CLVRT_MATERIAL_CODE} — the Culvert Material select on the Details tab's Culvert card. */
@Repository
public interface EngineeredClvrtMaterialCodeRepository extends JpaRepository<EngineeredClvrtMaterialCodeEntity, String> {

  /** The current codes, by description; expired ones are left out, as legacy's select left them. */
  @Query("""
      SELECT code
        FROM EngineeredClvrtMaterialCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<EngineeredClvrtMaterialCodeEntity> findAllCurrent();
}
