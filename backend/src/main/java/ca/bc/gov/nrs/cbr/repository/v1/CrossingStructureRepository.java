package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** {@code THE.CROSSING_STRUCTURE}. */
@Repository
public interface CrossingStructureRepository
    extends JpaRepository<CrossingStructureEntity, Long>,
        JpaSpecificationExecutor<CrossingStructureEntity> {

  /**
   * How many structures of one kind stand on a site.
   *
   * <p>Counted rather than fetched: the caller only needs to know whether any exist, and a site can
   * carry a good number of them.
   */
  long countByCrossingSiteIdAndActiveInd(String crossingSiteId, String activeInd);

  /**
   * The ids of the structures of one kind on a site, oldest first — what Site Detail's Display
   * Structures needs to open the one structure directly when there is only one.
   */
  @Query("""
      SELECT structure.crossingStructureId FROM CrossingStructureEntity structure
      WHERE structure.crossingSiteId = :crossingSiteId AND structure.activeInd = :activeInd
      ORDER BY structure.crossingStructureId""")
  List<Long> findIdsByCrossingSiteIdAndActiveInd(
      @Param("crossingSiteId") String crossingSiteId, @Param("activeInd") String activeInd);

  /**
   * Archives structures: {@code ACTIVE_IND = 'N'}, stamped with who and when.
   *
   * <p>Legacy's {@code CBR.ARCHIVE_STRUCTURE}, one statement for many ids. An already-archived
   * structure is archived again and re-stamped, as legacy does; an id with no row matches nothing.
   *
   * <p>Clears the persistence context afterwards, so a structure read later in the same transaction
   * is read afresh rather than from before the update.
   *
   * @return how many rows were updated
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CrossingStructureEntity structure
         SET structure.activeInd = 'N',
             structure.updateUserid = :user,
             structure.updateTimestamp = :now
       WHERE structure.crossingStructureId IN :ids
      """)
  int archive(
      @Param("ids") Collection<Long> ids,
      @Param("user") String user,
      @Param("now") LocalDateTime now);

  /**
   * Writes the structure's inspection schedule — the Inspections tab's Inspection Schedule card —
   * with who changed it and when (the database's clock). Only these columns: legacy's whole-row
   * save also blanked columns the page never showed (cbr-structure-edit.local.md §8).
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CrossingStructureEntity structure
         SET structure.closeProximityInd = :closeProximityInd,
             structure.specialEquipmentRqmtCode = :equipmentCode,
             structure.nextPlannedClsProxInspDt = :nextCloseProximityDate,
             structure.nextPlannedInspectionDate = :nextRoutineDate,
             structure.routineInspectionFrequency = :routineFrequency,
             structure.updateUserid = :user,
             structure.updateTimestamp = LOCAL DATETIME
       WHERE structure.crossingStructureId = :id
      """)
  int updateInspectionSchedule(
      @Param("id") Long id,
      @Param("closeProximityInd") String closeProximityInd,
      @Param("equipmentCode") String equipmentCode,
      @Param("nextCloseProximityDate") LocalDate nextCloseProximityDate,
      @Param("nextRoutineDate") LocalDate nextRoutineDate,
      @Param("routineFrequency") String routineFrequency,
      @Param("user") String user);

  /**
   * Marks structures updated by a repair-responsibility change, as legacy's {@code structure.save}
   * did: stamped with who and when, and {@code ACTIVE_IND = 'Y'} — legacy's save sets every
   * structure it touches active, so an archived structure ticked for this comes back. Kept on
   * purpose; see {@code StructureService.updateRepairResponsibility}.
   *
   * @return how many rows were updated
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CrossingStructureEntity structure
         SET structure.activeInd = 'Y',
             structure.updateUserid = :user,
             structure.updateTimestamp = :now
       WHERE structure.crossingStructureId IN :ids
      """)
  int touchForRepairResponsibility(
      @Param("ids") Collection<Long> ids,
      @Param("user") String user,
      @Param("now") LocalDateTime now);

  /**
   * How many active structures of the given types stand on a site — the "of N" after a culvert's
   * number, as legacy's {@code FIND_CULVERTS_BY_SITE_ID} counts it (active {@code CUL} and
   * {@code WLC}).
   */
  long countByCrossingSiteIdAndActiveIndAndStructureTypeClassCodeIn(
      String crossingSiteId, String activeInd, Collection<String> structureTypeClassCodes);
}
