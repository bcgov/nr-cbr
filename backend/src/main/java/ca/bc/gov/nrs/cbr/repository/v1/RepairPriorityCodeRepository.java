package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.REPAIR_PRIORITY_CODE} — read by the structure page's Repairs tab and its Repair Item
 * dialog.
 */
@Repository
public interface RepairPriorityCodeRepository
    extends JpaRepository<RepairPriorityCodeEntity, String> {

  /**
   * The current priorities, by code.
   *
   * <p>TODO: order by {@code REPAIR_PRIORITY_CODE_ORDER.REPAIR_PRIORITY_CODE_ORDER} and leave out a
   * priority with no row there, as legacy's {@code FIND_REPAIR_PRIORITY_CODES} does, once
   * {@code FSA_CBR_READ_WRITE_ROLE} holds SELECT on it — see the same TODO on
   * {@code StructureRepairRepository.findPageByStructure}.
   */
  @Query("""
      SELECT code
        FROM RepairPriorityCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.repairPriorityCode
      """)
  List<RepairPriorityCodeEntity> findAllCurrent();
}
