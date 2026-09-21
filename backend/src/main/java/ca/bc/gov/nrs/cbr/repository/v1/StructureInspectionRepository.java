package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.STRUCTURE_INSPECTION}.
 *
 * <p>{@link JpaSpecificationExecutor} for the same reason
 * {@link CrossingSiteRepository} has it: nineteen optional criteria cannot be expressed as a method
 * name, and the alternative — the stored procedure's caller-built {@code WHERE} clause with a
 * parallel bind array — is the thing this replaces. See
 * {@link ca.bc.gov.nrs.cbr.specification.v1.InspectionSearchSpecifications}.
 *
 * <p>Its {@code findAll(Specification, Pageable)} runs the page and the count as two queries, the
 * same split legacy makes with {@code FIND_INSPECTIONS_BY_CRITERIA} and
 * {@code COUNT_INSPECTIONS_BY_CRITERIA} — except that here both are generated from one
 * specification and cannot drift apart. Legacy's count is also not a count: it runs the full
 * {@code SELECT} and calls {@code results.size()} in Java.
 */
@Repository
public interface StructureInspectionRepository
    extends JpaRepository<StructureInspectionEntity, Long>,
    JpaSpecificationExecutor<StructureInspectionEntity> {
}
