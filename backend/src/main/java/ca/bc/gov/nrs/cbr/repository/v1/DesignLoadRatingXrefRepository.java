package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.DesignLoadRatingXrefEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.STRCTRE_DESGN_LOAD_RATING_XREF} — the design load rating each vehicle fixes. */
@Repository
public interface DesignLoadRatingXrefRepository
    extends JpaRepository<DesignLoadRatingXrefEntity, DesignLoadRatingXrefEntity.Key> {

  /** The vehicle's rows — legacy's {@code FIND_DVC_LOAD_RATING}. */
  List<DesignLoadRatingXrefEntity> findByDesignVehicleLoadCode(String designVehicleLoadCode);
}
