package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** {@code THE.FOREST_SERVICE_CULVERT} — read by the structure page. */
@Repository
public interface ForestServiceCulvertRepository
    extends JpaRepository<ForestServiceCulvertEntity, Long> {

  /** A structure's culvert record. One at most in practice; the first if there are more. */
  Optional<ForestServiceCulvertEntity> findFirstByCrossingStructureIdOrderByForestServiceCulvertId(
      Long crossingStructureId);

  /**
   * The length of each of these structures' culverts, oldest culvert record first — the Structure
   * Inventory report's "Total Length" for a structure with no bridge.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.repository.v1.StructureValue(
               culvert.crossingStructureId, culvert.culvertLength)
        FROM ForestServiceCulvertEntity culvert
       WHERE culvert.crossingStructureId IN :structureIds
       ORDER BY culvert.forestServiceCulvertId
      """)
  List<StructureValue<BigDecimal>> findLengths(
      @Param("structureIds") Collection<Long> structureIds);
}
