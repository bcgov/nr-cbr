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
   * The current priorities, most pressing first, by {@code REPAIR_PRIORITY_CODE_ORDER}; a priority
   * with no rank there is left out, as legacy's {@code FIND_REPAIR_PRIORITIES} leaves it.
   */
  @Query("""
      SELECT code
        FROM RepairPriorityCodeEntity code
        JOIN RepairPriorityCodeOrderEntity priorityOrder
          ON priorityOrder.repairPriorityCode = code.repairPriorityCode
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY priorityOrder.repairPriorityCodeOrder, code.repairPriorityCode
      """)
  List<RepairPriorityCodeEntity> findAllCurrent();
}
