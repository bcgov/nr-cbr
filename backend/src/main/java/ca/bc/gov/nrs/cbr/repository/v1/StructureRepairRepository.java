package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_REPAIR} — repairs raised by an inspection. */
@Repository
public interface StructureRepairRepository extends JpaRepository<StructureRepairEntity, Long> {

  void deleteByInspectionId(Long inspectionId);

  /**
   * A page of a structure's repairs — legacy's {@code FIND_REPAIRS_BY_STRUCTURE_ID}, or with
   * {@code outstandingOnly} its {@code FIND_OUTSTANDING_STRC_REPAIRS}: suggested, required or
   * carried-forward repairs that have not themselves been carried forward.
   *
   * <p>In legacy's order: newest inspection first (Oracle puts a repair with no inspection first in
   * a descending sort, and so does this), then priority, then status descending, then number.
   *
   * <p>TODO: rank priority by {@code REPAIR_PRIORITY_CODE_ORDER.REPAIR_PRIORITY_CODE_ORDER}, as
   * legacy does, once {@code FSA_CBR_READ_WRITE_ROLE} holds SELECT on it — the grant is in
   * nr-mof-db's {@code V202609211100.5__FSA_CBR_READ_WRITE_ROLE.sql}. Until then priority sorts by
   * its code, which may not be the order the priorities rank in.
   */
  @Query(value = """
      SELECT repair
        FROM StructureRepairEntity repair
        LEFT JOIN FETCH repair.inspection inspection
       WHERE repair.crossingStructureId = :structureId
         AND (:outstandingOnly = FALSE
              OR (repair.repairStatusCode IN :outstandingStatuses
                  AND repair.carriedForwardInd = 'N'))
       ORDER BY inspection.inspectionDate DESC NULLS FIRST,
                repair.repairPriorityCode ASC,
                repair.repairStatusCode DESC,
                repair.repairNumber ASC
      """,
      countQuery = """
      SELECT COUNT(repair)
        FROM StructureRepairEntity repair
       WHERE repair.crossingStructureId = :structureId
         AND (:outstandingOnly = FALSE
              OR (repair.repairStatusCode IN :outstandingStatuses
                  AND repair.carriedForwardInd = 'N'))
      """)
  Page<StructureRepairEntity> findPageByStructure(
      @Param("structureId") Long structureId,
      @Param("outstandingOnly") boolean outstandingOnly,
      @Param("outstandingStatuses") Collection<String> outstandingStatuses,
      Pageable pageable);
}
