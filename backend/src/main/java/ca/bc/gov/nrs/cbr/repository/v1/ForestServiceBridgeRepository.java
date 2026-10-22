package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
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

  /** Every bridge record of a structure, oldest first — the Structure Detail Report prints each. */
  List<ForestServiceBridgeEntity> findByCrossingStructureIdOrderByForestServiceBridgeId(
      Long crossingStructureId);

  /**
   * The spans of every bridge record of a structure, by span number — legacy's
   * {@code STRCTRE_DTL_RPT} span cursor, from which the Structure Detail Report lists the lengths
   * and counts each bridge's spans.
   */
  @Query("""
      SELECT span
        FROM ForestServiceBridgeSpanEntity span, ForestServiceBridgeEntity bridge
       WHERE span.forestServiceBridgeId = bridge.forestServiceBridgeId
         AND bridge.crossingStructureId = :structureId
       ORDER BY span.spanNumber, span.forestServiceBridgeSpanId
      """)
  List<ForestServiceBridgeSpanEntity> findSpans(@Param("structureId") Long structureId);

  /**
   * The span lengths of these structures' bridges, each structure's by span number — the
   * Inspections Conducted – Detail report's "Span Length(s)", legacy's
   * {@code FIND_STRUCTURE_SPAN_LENGTHS} for many structures at once.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.repository.v1.StructureValue(
               bridge.crossingStructureId, span.spanLength)
        FROM ForestServiceBridgeSpanEntity span, ForestServiceBridgeEntity bridge
       WHERE span.forestServiceBridgeId = bridge.forestServiceBridgeId
         AND bridge.crossingStructureId IN :structureIds
       ORDER BY bridge.crossingStructureId, span.spanNumber, span.forestServiceBridgeSpanId
      """)
  List<StructureValue<BigDecimal>> findSpanLengths(
      @Param("structureIds") Collection<Long> structureIds);

  /**
   * Each of these structures' bridges — its total length and how many piers it has — oldest bridge
   * record first: the Inspections Due reports' Total Length and # Piers, a line per bridge record
   * as legacy's left join made it.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.repository.v1.StructureBridgeLength(
               bridge.crossingStructureId, bridge.totalBridgeLength,
               (SELECT COUNT(pier) FROM ForestServiceBridgePierEntity pier
                 WHERE pier.forestServiceBridgeId = bridge.forestServiceBridgeId))
        FROM ForestServiceBridgeEntity bridge
       WHERE bridge.crossingStructureId IN :structureIds
       ORDER BY bridge.forestServiceBridgeId
      """)
  List<StructureBridgeLength> findLengthsAndPiers(
      @Param("structureIds") Collection<Long> structureIds);

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
               bridge.deckWidth, surface.description)
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
        LEFT JOIN RunningSurfaceCodeEntity surface
               ON surface.runningSurfaceCode = bridge.runningSurfaceCode
       WHERE bridge.crossingStructureId IN :structureIds
       ORDER BY bridge.forestServiceBridgeId
      """)
  List<StructureBridgeDetail> findDetails(@Param("structureIds") Collection<Long> structureIds);

  /** These structures' bridges, oldest record first — a report's, each structure's first used. */
  List<ForestServiceBridgeEntity> findByCrossingStructureIdInOrderByForestServiceBridgeId(
      Collection<Long> structureIds);
}
