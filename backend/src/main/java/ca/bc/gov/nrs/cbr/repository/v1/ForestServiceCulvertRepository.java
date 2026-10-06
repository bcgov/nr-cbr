package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.FOREST_SERVICE_CULVERT} — read by the structure page. */
@Repository
public interface ForestServiceCulvertRepository
    extends JpaRepository<ForestServiceCulvertEntity, Long> {

  /** A structure's culvert record. One at most in practice; the first if there are more. */
  Optional<ForestServiceCulvertEntity> findFirstByCrossingStructureIdOrderByForestServiceCulvertId(
      Long crossingStructureId);
}
