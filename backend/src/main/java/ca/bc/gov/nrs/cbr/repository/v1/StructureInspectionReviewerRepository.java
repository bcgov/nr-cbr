package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_INSPECTION_REVIEWER} — whose IDIR ID a reviewed load rating shows. */
@Repository
public interface StructureInspectionReviewerRepository
    extends JpaRepository<StructureInspectionReviewerEntity, Long> {}
