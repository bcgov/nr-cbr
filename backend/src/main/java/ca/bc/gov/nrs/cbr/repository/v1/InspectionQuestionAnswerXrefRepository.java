package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionAnswerXrefEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Reads {@link InspectionQuestionAnswerXrefEntity} for Add Inspection's questions. */
@Repository
public interface InspectionQuestionAnswerXrefRepository extends JpaRepository<InspectionQuestionAnswerXrefEntity, InspectionQuestionAnswerXrefEntity.Key> {

  List<InspectionQuestionAnswerXrefEntity> findByQuestionCodeIn(Collection<String> questionCodes);
}
