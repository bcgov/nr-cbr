package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CloseProximityInspectionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.CLOSE_PROXIMITY_INSPECTION}. */
@Repository
public interface CloseProximityInspectionRepository
    extends JpaRepository<CloseProximityInspectionEntity, Long> {

  long countByCrossingSiteId(String crossingSiteId);

  /**
   * A structure's completed close proximity inspections, newest first — legacy's
   * {@code GET_CLOSE_PROX_INSP_DATES}; the id breaks a tie.
   */
  List<CloseProximityInspectionEntity>
      findByCrossingStructureIdOrderByCompletionDateDescCloseProximityInspectionIdDesc(
          Long crossingStructureId);
}
