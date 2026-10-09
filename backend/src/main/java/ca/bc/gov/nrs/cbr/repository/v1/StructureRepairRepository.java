package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import java.time.LocalDate;
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
   * The structure's highest repair number, inspection-raised repairs included, or 0 when it has
   * none — a new repair takes the next, as legacy's {@code FIND_NEXT_REPAIR_NUMBER} gives it.
   */
  @Query("""
      SELECT COALESCE(MAX(repair.repairNumber), 0)
        FROM StructureRepairEntity repair
       WHERE repair.crossingStructureId = :structureId
      """)
  long findHighestNumber(@Param("structureId") Long structureId);

  /**
   * A page of a structure's repairs — legacy's {@code FIND_REPAIRS_BY_STRUCTURE_ID}, or with
   * {@code outstandingOnly} its {@code FIND_OUTSTANDING_STRC_REPAIRS}: suggested, required or
   * carried-forward repairs that have not themselves been carried forward.
   *
   * <p>In legacy's order: newest inspection first (Oracle puts a repair with no inspection first in
   * a descending sort, and so does this), then priority, then status descending, then number.
   *
   * <p>Without {@code includeBeforeInstall}, a repair raised by an inspection dated on or before
   * {@code installed} — 1 January of the year the superstructure was installed — is left out, as
   * legacy's table leaves it until "Show Inspections before the Superstructure Install Date" is
   * ticked. A repair with no inspection, or an undated one, is always listed.
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
         AND (:includeBeforeInstall = TRUE
              OR inspection IS NULL
              OR inspection.inspectionDate IS NULL
              OR inspection.inspectionDate > :installed)
       ORDER BY inspection.inspectionDate DESC NULLS FIRST,
                repair.repairPriorityCode ASC,
                repair.repairStatusCode DESC,
                repair.repairNumber ASC
      """,
      countQuery = """
      SELECT COUNT(repair)
        FROM StructureRepairEntity repair
        LEFT JOIN repair.inspection inspection
       WHERE repair.crossingStructureId = :structureId
         AND (:outstandingOnly = FALSE
              OR (repair.repairStatusCode IN :outstandingStatuses
                  AND repair.carriedForwardInd = 'N'))
         AND (:includeBeforeInstall = TRUE
              OR inspection IS NULL
              OR inspection.inspectionDate IS NULL
              OR inspection.inspectionDate > :installed)
      """)
  Page<StructureRepairEntity> findPageByStructure(
      @Param("structureId") Long structureId,
      @Param("outstandingOnly") boolean outstandingOnly,
      @Param("outstandingStatuses") Collection<String> outstandingStatuses,
      @Param("includeBeforeInstall") boolean includeBeforeInstall,
      @Param("installed") LocalDate installed,
      Pageable pageable);

  /**
   * How many of the structure's repairs, in the view chosen, {@link #findPageByStructure} leaves
   * out without {@code includeBeforeInstall}: those raised by an inspection dated on or before
   * {@code installed}.
   */
  @Query("""
      SELECT COUNT(repair)
        FROM StructureRepairEntity repair
        JOIN repair.inspection inspection
       WHERE repair.crossingStructureId = :structureId
         AND (:outstandingOnly = FALSE
              OR (repair.repairStatusCode IN :outstandingStatuses
                  AND repair.carriedForwardInd = 'N'))
         AND inspection.inspectionDate <= :installed
      """)
  long countBeforeInstall(
      @Param("structureId") Long structureId,
      @Param("outstandingOnly") boolean outstandingOnly,
      @Param("outstandingStatuses") Collection<String> outstandingStatuses,
      @Param("installed") LocalDate installed);
}
