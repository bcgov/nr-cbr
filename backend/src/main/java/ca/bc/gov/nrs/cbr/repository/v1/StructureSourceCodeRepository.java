package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureSourceCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_SOURCE_CODE} — the Source select on the Details tab's Structure card. */
@Repository
public interface StructureSourceCodeRepository extends JpaRepository<StructureSourceCodeEntity, String> {

  /** The current codes, by description; expired ones are left out, as legacy's select left them. */
  @Query("""
      SELECT code
        FROM StructureSourceCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<StructureSourceCodeEntity> findAllCurrent();
}
