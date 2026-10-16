package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionSubsectionXrefEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Reads {@link InspectionQuestionSubsectionXrefEntity} for Add Inspection's questions. */
@Repository
public interface InspectionQuestionSubsectionXrefRepository extends JpaRepository<InspectionQuestionSubsectionXrefEntity, InspectionQuestionSubsectionXrefEntity.Key> {

  /** The questions the groups ask, each group's in order. */
  @Query("""
      SELECT link
        FROM InspectionQuestionSubsectionXrefEntity link
       WHERE link.subsectionCode IN :subsectionCodes
       ORDER BY link.orderBy
      """)
  List<InspectionQuestionSubsectionXrefEntity> findInOrder(
      @Param("subsectionCodes") Collection<String> subsectionCodes);
}
