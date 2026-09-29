package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.RecreationProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.RECREATION_PROJECT}, keyed by the file id the site form holds.
 *
 * <p>Replaces {@code CBR_GENERAL.FIND_PROJECT_NAME_BY_ID}, which is a single-column select on the
 * primary key — {@code findById} says the same thing without a query of its own.
 */
@Repository
public interface RecreationProjectRepository
    extends JpaRepository<RecreationProjectEntity, String> {}
