package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionAnswerOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Reads {@link InspectionAnswerOrderEntity} for Add Inspection's questions. */
@Repository
public interface InspectionAnswerOrderRepository extends JpaRepository<InspectionAnswerOrderEntity, String> {
}
