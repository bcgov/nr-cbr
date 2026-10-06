package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_COMMENT} — read by the structure page. */
@Repository
public interface StructureCommentRepository extends JpaRepository<StructureCommentEntity, Long> {

  /**
   * One kind of comment on a structure, newest first: general comments ({@code 'N'}) or
   * planned-inspection comments ({@code 'Y'}). Legacy's {@code FIND_STRUCTURE_COMM_BY_STRC_ID}.
   */
  @Query("""
      SELECT comment
        FROM StructureCommentEntity comment
       WHERE comment.crossingStructureId = :structureId
         AND comment.plannedInspectionCmtInd = :plannedInspection
       ORDER BY comment.updateTimestamp DESC, comment.structureCommentId DESC
      """)
  List<StructureCommentEntity> findByKind(
      @Param("structureId") Long structureId,
      @Param("plannedInspection") String plannedInspection);
}
