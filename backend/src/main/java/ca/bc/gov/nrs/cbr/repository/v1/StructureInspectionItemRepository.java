package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_INSPECTION_ITEM} — the answered questions of an inspection form. */
@Repository
public interface StructureInspectionItemRepository
    extends JpaRepository<StructureInspectionItemEntity, Long> {

  void deleteByInspectionId(Long inspectionId);
}
