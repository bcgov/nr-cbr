package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.LoadRatingReasonXrefEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code THE.STRCTRE_LOAD_RATING_RSN_XREF} — which reasons fit which change of rating. */
@Repository
public interface LoadRatingReasonXrefRepository
    extends JpaRepository<LoadRatingReasonXrefEntity, LoadRatingReasonXrefEntity.Key> {}
