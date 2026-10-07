package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.FOREST_SERVICE_BRIDGE_PIER} — listed on the structure page's Spans &amp; Piers. */
@Repository
public interface ForestServiceBridgePierRepository
    extends JpaRepository<ForestServiceBridgePierEntity, Long> {

  /** A bridge's piers by pier number — legacy's {@code FIND_PIERS_BY_BRIDGE_ID}. */
  List<ForestServiceBridgePierEntity> findByForestServiceBridgeIdOrderByPierNumberAsc(
      Long forestServiceBridgeId);
}
