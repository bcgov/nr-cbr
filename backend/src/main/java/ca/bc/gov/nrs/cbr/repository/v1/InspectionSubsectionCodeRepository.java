package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionSubsectionCodeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Reads {@link InspectionSubsectionCodeEntity} for Add Inspection's questions. */
@Repository
public interface InspectionSubsectionCodeRepository extends JpaRepository<InspectionSubsectionCodeEntity, String> {

  /**
   * The question groups a Type/Class is inspected under, in legacy's order: effective date, then
   * code.
   */
  @Query("""
      SELECT subsection
        FROM InspectionSubsectionCodeEntity subsection,
             InspectionSubsectionTypeXrefEntity xref
       WHERE xref.subsectionCode = subsection.subsectionCode
         AND xref.typeClassCode = :typeClass
       ORDER BY subsection.effectiveDate, subsection.subsectionCode
      """)
  List<InspectionSubsectionCodeEntity> findForTypeClass(@Param("typeClass") String typeClass);
}
