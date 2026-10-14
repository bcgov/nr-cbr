package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.RunningSurfaceCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.RUNNING_SURFACE_CODE} — the Running Surface select on the Details tab's Bridge card. */
@Repository
public interface RunningSurfaceCodeRepository extends JpaRepository<RunningSurfaceCodeEntity, String> {

  /** The current codes, by description; expired ones are left out, as legacy's select left them. */
  @Query("""
      SELECT code
        FROM RunningSurfaceCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<RunningSurfaceCodeEntity> findAllCurrent();
}
