package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.REPAIR_PRIORITY_CODE} — read by the structure page's Repairs tab. */
@Repository
public interface RepairPriorityCodeRepository
    extends JpaRepository<RepairPriorityCodeEntity, String> {}
