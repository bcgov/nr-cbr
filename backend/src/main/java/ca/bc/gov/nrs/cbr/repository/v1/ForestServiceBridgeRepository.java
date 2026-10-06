package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.FOREST_SERVICE_BRIDGE} — read by the structure page. */
@Repository
public interface ForestServiceBridgeRepository
    extends JpaRepository<ForestServiceBridgeEntity, Long> {

  /** A structure's bridge record. One at most in practice; the first if there are more. */
  Optional<ForestServiceBridgeEntity> findFirstByCrossingStructureIdOrderByForestServiceBridgeId(
      Long crossingStructureId);
}
