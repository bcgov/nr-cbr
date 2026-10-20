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

  /**
   * These structures' bridges as the Structure Inventory – Detailed report prints them, oldest
   * bridge record first. Legacy's {@code STRCTRE_INVNTRY_DTL_RPT} left-joins each code table, so a
   * code with no row there prints blank rather than dropping the bridge.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.repository.v1.StructureBridgeDetail(
               bridge.crossingStructureId, superstructure.description, deck.description,
               leftAbutment.description, rightAbutment.description, curb.description,
               bridge.totalBridgeLength, bridge.portableSuperstructureLocationDetail,
               (SELECT COUNT(span) FROM ForestServiceBridgeSpanEntity span
                 WHERE span.forestServiceBridgeId = bridge.forestServiceBridgeId),
               bridge.deckWidth)
        FROM ForestServiceBridgeEntity bridge
        LEFT JOIN SuperstructureTypeCodeEntity superstructure
               ON superstructure.superstructureTypeCode = bridge.superstructureTypeCode
        LEFT JOIN DeckTypeCodeEntity deck ON deck.deckTypeCode = bridge.deckTypeCode
        LEFT JOIN AbutmentCodeEntity leftAbutment
               ON leftAbutment.abutmentCode = bridge.leftAbutmentCode
        LEFT JOIN AbutmentCodeEntity rightAbutment
               ON rightAbutment.abutmentCode = bridge.rightAbutmentCode
        LEFT JOIN StructureCurbTypeCodeEntity curb
               ON curb.structureCurbTypeCode = bridge.structureCurbTypeCode
       WHERE bridge.crossingStructureId IN :structureIds
       ORDER BY bridge.forestServiceBridgeId
      """)
  List<StructureBridgeDetail> findDetails(@Param("structureIds") Collection<Long> structureIds);
}
