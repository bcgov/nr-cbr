package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.REPAIR_STATUS_CODE} — read by the structure page's Repairs tab and its Repair Item
 * dialog.
 */
@Repository
public interface RepairStatusCodeRepository extends JpaRepository<RepairStatusCodeEntity, String> {

  /** The current statuses, by description — legacy's {@code FIND_REPAIR_STATUS_CODES}. */
  @Query("""
      SELECT code
        FROM RepairStatusCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<RepairStatusCodeEntity> findAllCurrent();
}
