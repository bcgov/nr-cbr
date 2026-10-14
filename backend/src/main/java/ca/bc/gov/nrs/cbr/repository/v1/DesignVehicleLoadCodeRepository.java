package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.DesignVehicleLoadCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** {@code THE.DESIGN_VEHICLE_LOAD_CODE} — the Design Vehicle Configuration select. */
@Repository
public interface DesignVehicleLoadCodeRepository extends JpaRepository<DesignVehicleLoadCodeEntity, String> {

  /** The current codes, by description; expired ones are left out, as legacy's lists left them. */
  @Query("""
      SELECT code
        FROM DesignVehicleLoadCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<DesignVehicleLoadCodeEntity> findAllCurrent();
}
