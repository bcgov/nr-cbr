package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.CROSSING_STRUCTURE_FILE_DETAIL} — attachment metadata. */
@Repository
public interface CrossingStructureFileDetailRepository
    extends JpaRepository<CrossingStructureFileDetailEntity, Long> {

  /**
   * The attachments belonging to an inspection.
   *
   * <p>Read rather than deleted straight off, because each one's bytes live in a separate table
   * whose foreign key points at this row — so the ids are needed before these can go.
   */
  List<CrossingStructureFileDetailEntity> findByInspectionId(Long inspectionId);

  void deleteByInspectionId(Long inspectionId);
}
