package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureRepairClassCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_REPAIR_CLASS_CODE} — the Repairs Summary report's Structural Safety. */
@Repository
public interface StructureRepairClassCodeRepository
    extends JpaRepository<StructureRepairClassCodeEntity, String> {

  /** The current classes, by description — legacy's code table read, which left expired ones out. */
  @Query("""
      SELECT code
        FROM StructureRepairClassCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<StructureRepairClassCodeEntity> findAllCurrent();
}
