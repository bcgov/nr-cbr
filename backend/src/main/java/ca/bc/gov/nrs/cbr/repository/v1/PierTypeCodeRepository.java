package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.PierTypeCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** {@code THE.PIER_TYPE_CODE} — the pier types on the Spans &amp; Piers tab. */
@Repository
public interface PierTypeCodeRepository extends JpaRepository<PierTypeCodeEntity, String> {

  /**
   * The current types, by description — legacy's {@code FIND_PIER_TYPES} order, with the expired
   * ones left out as its pier select leaves them out.
   */
  @Query("""
      SELECT code
        FROM PierTypeCodeEntity code
       WHERE CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
       ORDER BY code.description
      """)
  List<PierTypeCodeEntity> findAllCurrent();

  /** Whether this type is current — one a new pier may take. */
  @Query("""
      SELECT COUNT(code) > 0
        FROM PierTypeCodeEntity code
       WHERE code.pierTypeCode = :code
         AND CURRENT_TIMESTAMP BETWEEN code.effectiveDate AND code.expiryDate
      """)
  boolean isCurrent(@Param("code") String code);
}
