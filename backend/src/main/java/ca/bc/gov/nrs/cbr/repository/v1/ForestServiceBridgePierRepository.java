package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.FOREST_SERVICE_BRIDGE_PIER} — the structure page's Spans &amp; Piers tab. */
@Repository
public interface ForestServiceBridgePierRepository
    extends JpaRepository<ForestServiceBridgePierEntity, Long> {

  /** A bridge's piers by pier number — legacy's {@code FIND_PIERS_BY_BRIDGE_ID}. */
  List<ForestServiceBridgePierEntity> findByForestServiceBridgeIdOrderByPierNumberAsc(
      Long forestServiceBridgeId);

  long countByForestServiceBridgeId(Long forestServiceBridgeId);

  /** A bridge's pier of this number, if any — legacy's {@code FIND_PIER_BY_BRDGE_ID_NMBR}. */
  boolean existsByForestServiceBridgeIdAndPierNumber(Long forestServiceBridgeId, Long pierNumber);

  /** These bridges' piers, each bridge's in number order — a report's. */
  List<ForestServiceBridgePierEntity> findByForestServiceBridgeIdInOrderByPierNumberAsc(
      Collection<Long> bridgeIds);
}
