package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.REPAIR_STATUS_CODE} — read by the structure page's Repairs tab. */
@Repository
public interface RepairStatusCodeRepository extends JpaRepository<RepairStatusCodeEntity, String> {}
