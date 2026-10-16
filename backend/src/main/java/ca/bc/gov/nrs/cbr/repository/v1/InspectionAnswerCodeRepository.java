package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionAnswerCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Reads {@link InspectionAnswerCodeEntity} for Add Inspection's questions. */
@Repository
public interface InspectionAnswerCodeRepository extends JpaRepository<InspectionAnswerCodeEntity, String> {
}
