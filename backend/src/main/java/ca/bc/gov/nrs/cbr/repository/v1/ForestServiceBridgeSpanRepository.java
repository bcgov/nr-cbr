package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.FOREST_SERVICE_BRIDGE_SPAN} — counted for the structure page's Number of Spans. */
@Repository
public interface ForestServiceBridgeSpanRepository
    extends JpaRepository<ForestServiceBridgeSpanEntity, Long> {

  long countByForestServiceBridgeId(Long forestServiceBridgeId);
}
