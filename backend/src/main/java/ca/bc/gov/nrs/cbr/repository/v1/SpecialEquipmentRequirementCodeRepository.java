package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.SpecialEquipmentRequirementCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.SPECIAL_EQUIPMENT_RQMT_CODE}. */
@Repository
public interface SpecialEquipmentRequirementCodeRepository
    extends JpaRepository<SpecialEquipmentRequirementCodeEntity, String> {

  /**
   * Every code, ordered by description — what legacy's {@code CodeTableServiceManager} loads for
   * the Special Equipment Requirements select on Structure Search, with no filter on the effective
   * dates.
   */
  List<SpecialEquipmentRequirementCodeEntity> findAllByOrderByDescriptionAsc();
}
