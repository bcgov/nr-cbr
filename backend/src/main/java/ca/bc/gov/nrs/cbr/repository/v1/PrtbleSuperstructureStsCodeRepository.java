package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.PrtbleSuperstructureStsCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.PRTBLE_SUPERSTRUCTURE_STS_CODE}. */
@Repository
public interface PrtbleSuperstructureStsCodeRepository
    extends JpaRepository<PrtbleSuperstructureStsCodeEntity, String> {

  /**
   * Every code, ordered by description — what legacy's {@code CodeTableServiceManager} loads for
   * the Portable Superstructure Status select on the Structure Inventory reports, with no filter on
   * the effective dates.
   */
  List<PrtbleSuperstructureStsCodeEntity> findAllByOrderByDescriptionAsc();
}
