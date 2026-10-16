package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Reads {@link InspectionQuestionCodeEntity} for Add Inspection's questions. */
@Repository
public interface InspectionQuestionCodeRepository extends JpaRepository<InspectionQuestionCodeEntity, String> {
}
