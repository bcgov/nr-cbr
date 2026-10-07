package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.PierTypeCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.PIER_TYPE_CODE} — decodes the pier types on the Spans &amp; Piers tab. */
@Repository
public interface PierTypeCodeRepository extends JpaRepository<PierTypeCodeEntity, String> {}
