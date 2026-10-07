package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.FOREST_SERVICE_BRIDGE_SPAN} — counted for the structure page's Number of Spans, and
 * listed on its Spans &amp; Piers tab.
 */
@Repository
public interface ForestServiceBridgeSpanRepository
    extends JpaRepository<ForestServiceBridgeSpanEntity, Long> {

  long countByForestServiceBridgeId(Long forestServiceBridgeId);

  /** A bridge's spans by span number — legacy's {@code FIND_SPANS_BY_BRIDGE_ID}. */
  List<ForestServiceBridgeSpanEntity> findByForestServiceBridgeIdOrderBySpanNumberAsc(
      Long forestServiceBridgeId);
}
