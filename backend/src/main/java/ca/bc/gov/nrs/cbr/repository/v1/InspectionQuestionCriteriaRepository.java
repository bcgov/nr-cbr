package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionCriteriaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Reads {@link InspectionQuestionCriteriaEntity} for Add Inspection's questions. */
@Repository
public interface InspectionQuestionCriteriaRepository extends JpaRepository<InspectionQuestionCriteriaEntity, Long> {

  List<InspectionQuestionCriteriaEntity> findByQuestionCodeIn(Collection<String> questionCodes);
}
