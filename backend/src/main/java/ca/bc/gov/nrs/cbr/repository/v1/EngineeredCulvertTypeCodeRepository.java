package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.EngineeredCulvertTypeCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.ENGINEERED_CULVERT_TYPE_CODE}. */
@Repository
public interface EngineeredCulvertTypeCodeRepository
    extends JpaRepository<EngineeredCulvertTypeCodeEntity, String> {

  /**
   * Every code, ordered by description — what legacy's {@code CodeTableServiceManager} loads for
   * the Culvert Type select on Structure Search, with no filter on the effective
   * dates.
   */
  List<EngineeredCulvertTypeCodeEntity> findAllByOrderByDescriptionAsc();
}
