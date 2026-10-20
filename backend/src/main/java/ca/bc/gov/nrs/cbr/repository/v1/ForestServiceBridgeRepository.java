package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** {@code THE.FOREST_SERVICE_BRIDGE} — read by the structure page. */
@Repository
public interface ForestServiceBridgeRepository
    extends JpaRepository<ForestServiceBridgeEntity, Long> {

  /** A structure's bridge record. One at most in practice; the first if there are more. */
  Optional<ForestServiceBridgeEntity> findFirstByCrossingStructureIdOrderByForestServiceBridgeId(
      Long crossingStructureId);

  /**
   * The total length of each of these structures' bridges, oldest bridge record first — the
   * Structure Inventory report's "Total Length" for a bridge.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.repository.v1.StructureValue(
               bridge.crossingStructureId, bridge.totalBridgeLength)
        FROM ForestServiceBridgeEntity bridge
       WHERE bridge.crossingStructureId IN :structureIds
       ORDER BY bridge.forestServiceBridgeId
      """)
  List<StructureValue<BigDecimal>> findLengths(
      @Param("structureIds") Collection<Long> structureIds);
}
