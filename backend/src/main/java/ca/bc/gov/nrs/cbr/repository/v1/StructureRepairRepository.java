package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_REPAIR} — repairs raised by an inspection. */
@Repository
public interface StructureRepairRepository extends JpaRepository<StructureRepairEntity, Long> {

  void deleteByInspectionId(Long inspectionId);
}
