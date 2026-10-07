package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.MONITORING_STATUS_CODE} — read by the structure page's Monitoring tab. */
@Repository
public interface MonitoringStatusCodeRepository
    extends JpaRepository<MonitoringStatusCodeEntity, String> {}
