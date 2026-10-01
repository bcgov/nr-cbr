package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureCurbTypeCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_CURB_TYPE_CODE}. */
@Repository
public interface StructureCurbTypeCodeRepository
    extends JpaRepository<StructureCurbTypeCodeEntity, String> {

  /**
   * Every code, ordered by description — what legacy's {@code CodeTableServiceManager} loads for
   * the Curb Type select on Structure Search, with no filter on the effective
   * dates.
   */
  List<StructureCurbTypeCodeEntity> findAllByOrderByDescriptionAsc();
}
