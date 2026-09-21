package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_MONITOR_ITEMS} — monitoring items raised by an inspection. */
@Repository
public interface StructureMonitorItemRepository
    extends JpaRepository<StructureMonitorItemEntity, Long> {

  void deleteByInspectionId(Long inspectionId);
}
