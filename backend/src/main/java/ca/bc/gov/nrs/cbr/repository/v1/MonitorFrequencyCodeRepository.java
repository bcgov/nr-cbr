package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.MonitorFrequencyCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.MONITOR_FREQUENCY_CODE} — read by the structure page's Monitoring tab. */
@Repository
public interface MonitorFrequencyCodeRepository
    extends JpaRepository<MonitorFrequencyCodeEntity, String> {

  /** Every code, by description — the dropdown on the Monitoring tab's edit dialog. */
  List<MonitorFrequencyCodeEntity> findAllByOrderByDescriptionAsc();
}
