package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.CROSSING_SITE}.
 *
 * <p>{@link JpaSpecificationExecutor} is what Site Search needs and the other repositories do not:
 * seventeen optional criteria cannot be expressed as a method name, and the alternative — the
 * stored procedure's caller-built {@code WHERE} clause with a parallel bind array — is the thing
 * this replaces. See {@link ca.bc.gov.nrs.cbr.specification.v1.SiteSearchSpecifications}.
 *
 * <p>Its {@code findAll(Specification, Pageable)} runs the page and the count as two queries, which
 * is the same split legacy makes with {@code FIND_SITES_BY_CRITERIA} and
 * {@code COUNT_SITES_BY_CRITERIA} — except that here both are generated from one specification and
 * cannot drift apart.
 */
@Repository
public interface CrossingSiteRepository
    extends JpaRepository<CrossingSiteEntity, String>, JpaSpecificationExecutor<CrossingSiteEntity> {
}
