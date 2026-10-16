package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionSubsectionTypeXrefEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Reads {@link InspectionSubsectionTypeXrefEntity} for Add Inspection's questions. */
@Repository
public interface InspectionSubsectionTypeXrefRepository extends JpaRepository<InspectionSubsectionTypeXrefEntity, InspectionSubsectionTypeXrefEntity.Key> {
}
