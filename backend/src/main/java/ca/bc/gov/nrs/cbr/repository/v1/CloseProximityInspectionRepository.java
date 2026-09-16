package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CloseProximityInspectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.CLOSE_PROXIMITY_INSPECTION}. */
@Repository
public interface CloseProximityInspectionRepository
    extends JpaRepository<CloseProximityInspectionEntity, Long> {

  long countByCrossingSiteId(String crossingSiteId);
}
