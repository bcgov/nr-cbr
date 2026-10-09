package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureRepairGroupCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_REPAIR_GROUP_CODE} — the Repair Item dialog's group checkboxes. */
@Repository
public interface StructureRepairGroupCodeRepository
    extends JpaRepository<StructureRepairGroupCodeEntity, String> {

  /**
   * The current groups, by code. Legacy's {@code FIND_REPAIR_GROUP_CODES} gives no order, so the
   * code is used, the order its type list sorts the groups in.
   */
  @Query("""
      SELECT code
        FROM StructureRepairGroupCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.structureRepairGroupCode
      """)
  List<StructureRepairGroupCodeEntity> findAllCurrent();
}
