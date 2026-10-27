package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.STRUCTURE_INSPECTION_REVIEWER} — who reviewed an inspection, and whose IDIR ID a
 * reviewed load rating shows.
 */
@Repository
public interface StructureInspectionReviewerRepository
    extends JpaRepository<StructureInspectionReviewerEntity, Long> {

  /**
   * The reviewer row for a userid ({@code IDIR\JSMITH}), in any case — legacy stores it upper-cased
   * — active or not, as legacy binds a review. Legacy may hold two rows for one person; an active
   * one first, then the oldest.
   */
  Optional<StructureInspectionReviewerEntity>
      findFirstByUseridIgnoreCaseOrderByActiveIndDescInspectionReviewerIdAsc(String userid);
}
