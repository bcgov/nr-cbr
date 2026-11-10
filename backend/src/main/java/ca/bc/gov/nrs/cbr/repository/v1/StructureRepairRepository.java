package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
   * <p>Unordered here: the order comes on the {@code Pageable}, built by
   * {@code StructureRepairsService} — the header the user sorted by, then legacy's order. The
   * aliases it may name are {@code repair}, {@code inspection}, {@code priorityOrder} (the
   * priority's rank), {@code statusCode} and {@code typeCode} (the descriptions shown); all are
   * left joins, so a repair with no inspection, rank or decodable code is still listed.
   *
   * <p>Without {@code includeBeforeInstall}, a repair raised by an inspection dated on or before
   * {@code installed} — 1 January of the year the superstructure was installed — is left out, as
   * legacy's table leaves it until "Show Inspections before the Superstructure Install Date" is
   * ticked. A repair with no inspection, or an undated one, is always listed.
   */
  @Query(value = """
      SELECT repair
        FROM StructureRepairEntity repair
        LEFT JOIN FETCH repair.inspection inspection
        LEFT JOIN RepairPriorityCodeOrderEntity priorityOrder
          ON priorityOrder.repairPriorityCode = repair.repairPriorityCode
        LEFT JOIN RepairStatusCodeEntity statusCode
          ON statusCode.repairStatusCode = repair.repairStatusCode
        LEFT JOIN StructureRepairTypeCodeEntity typeCode
          ON typeCode.structureRepairTypeCode = repair.structureRepairTypeCode
       WHERE repair.crossingStructureId = :structureId
         AND (:inspectionId IS NULL OR repair.inspectionId = :inspectionId)
         AND (:outstandingOnly = FALSE
              OR (repair.repairStatusCode IN :outstandingStatuses
                  AND repair.carriedForwardInd = 'N'))
         AND (:includeBeforeInstall = TRUE
              OR inspection IS NULL
              OR inspection.inspectionDate IS NULL
              OR inspection.inspectionDate > :installed)
      """,
      countQuery = """
      SELECT COUNT(repair)
        FROM StructureRepairEntity repair
        LEFT JOIN repair.inspection inspection
       WHERE repair.crossingStructureId = :structureId
         AND (:inspectionId IS NULL OR repair.inspectionId = :inspectionId)
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
      @Param("inspectionId") Long inspectionId,
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

  /**
   * The structure's repairs a new inspection carries forward — legacy's Inspection.save
   * (Inspection.java:281-330): not Completed or Not Required, and not already carried forward.
   */
  @Query("""
      SELECT repair
        FROM StructureRepairEntity repair
       WHERE repair.crossingStructureId = :structureId
         AND (repair.repairStatusCode IS NULL OR repair.repairStatusCode NOT IN ('COM', 'NRQ'))
         AND repair.carriedForwardInd = 'N'
       ORDER BY repair.repairNumber
      """)
  List<StructureRepairEntity> findToCarryForward(@Param("structureId") Long structureId);

  /**
   * Makes every suggested repairs of the structure required — what legacy's {@code UPDATE_INSPECTION}
   * does when an inspection is reviewed — stamped with who and when.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE StructureRepairEntity repair
         SET repair.repairStatusCode = 'REQ',
             repair.requiredByUserid = :user,
             repair.requiredByTimestamp = LOCAL DATETIME,
             repair.updateUserid = :user,
             repair.updateTimestamp = LOCAL DATETIME
       WHERE repair.crossingStructureId = :structureId
         AND repair.repairStatusCode = 'SUG'
      """)
  int requireSuggested(@Param("structureId") Long structureId, @Param("user") String user);

  /**
   * These structures' repairs in these statuses, each structure's by repair number — the Blank
   * Report's outstanding repairs (legacy's {@code BLANK_INSPECTION_REPAIRS_SR}: Required and
   * Suggested).
   */
  List<StructureRepairEntity>
      findByCrossingStructureIdInAndRepairStatusCodeInOrderByCrossingStructureIdAscRepairNumberAsc(
          Collection<Long> structureIds, Collection<String> statusCodes);

  /** The repairs recorded on these inspections, each's by repair number — a report's. */
  List<StructureRepairEntity> findByInspectionIdInOrderByRepairNumberAsc(
      Collection<Long> inspectionIds);
}
