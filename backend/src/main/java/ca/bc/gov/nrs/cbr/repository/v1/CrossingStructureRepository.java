package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.CROSSING_STRUCTURE}. */
@Repository
public interface CrossingStructureRepository extends JpaRepository<CrossingStructureEntity, Long> {

  /**
   * How many structures of one kind stand on a site.
   *
   * <p>Counted rather than fetched: the caller only needs to know whether any exist, and a site can
   * carry a good number of them.
   */
  long countByCrossingSiteIdAndActiveInd(String crossingSiteId, String activeInd);
}
