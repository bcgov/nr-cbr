package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileEntity;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.CROSSING_STRUCTURE_FILE} — the bytes of an attachment. */
@Repository
public interface CrossingStructureFileRepository
    extends JpaRepository<CrossingStructureFileEntity, Long> {

  void deleteByFileIdIn(Collection<Long> fileIds);
}
