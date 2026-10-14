package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
   * Writes the structure's name and site — the Information tab's Edit — with who changed them and
   * when. Only these columns, as the schedule's update.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CrossingStructureEntity structure
         SET structure.crossingStructureName = :name,
             structure.crossingSiteId = :siteId,
             structure.updateUserid = :user,
             structure.updateTimestamp = LOCAL DATETIME
       WHERE structure.crossingStructureId = :id
      """)
  int updateInformation(
      @Param("id") Long id,
      @Param("name") String name,
      @Param("siteId") String siteId,
      @Param("user") String user);

  /** Writes Portable Structure, which the Details tab's Culvert card holds. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CrossingStructureEntity structure
         SET structure.portableStructureInd = :portableInd,
             structure.updateUserid = :user,
             structure.updateTimestamp = LOCAL DATETIME
       WHERE structure.crossingStructureId = :id
      """)
  int updatePortable(
      @Param("id") Long id,
      @Param("portableInd") String portableInd,
      @Param("user") String user);

  /** Writes the Details tab's Replacement Details, with who changed them and when. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CrossingStructureEntity structure
         SET structure.estimatedClosureYear = :closureYear,
             structure.lightVehicleReplacementYear = :replacementYear,
             structure.fullLogHaulReplacementYear = :loadRestrictionYear,
             structure.estimatedReplacementCost = :cost,
             structure.estimatedReplacementCostCmt = :costComment,
             structure.updateUserid = :user,
             structure.updateTimestamp = LOCAL DATETIME
       WHERE structure.crossingStructureId = :id
      """)
  int updateReplacementDetails(
      @Param("id") Long id,
      @Param("closureYear") Integer closureYear,
      @Param("replacementYear") Integer replacementYear,
      @Param("loadRestrictionYear") Integer loadRestrictionYear,
      @Param("cost") Long cost,
      @Param("costComment") String costComment,
      @Param("user") String user);

  /**
   * Writes the fields of the Details tab's Load Rating Details, with who changed them and when.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CrossingStructureEntity structure
         SET structure.loadPostingSignWrrntdCode = :postingCode,
             structure.loadRatingUnknownIndicator = :reviewInd,
             structure.designVehicleLoadCode = :vehicleCode,
             structure.designVehicleCmt = :vehicleComment,
             structure.designLoadRating = :designLoadRating,
             structure.updateUserid = :user,
             structure.updateTimestamp = LOCAL DATETIME
       WHERE structure.crossingStructureId = :id
      """)
  int updateLoadRatingDetails(
      @Param("id") Long id,
      @Param("postingCode") String postingCode,
      @Param("reviewInd") String reviewInd,
      @Param("vehicleCode") String vehicleCode,
      @Param("vehicleComment") String vehicleComment,
      @Param("designLoadRating") BigDecimal designLoadRating,
      @Param("user") String user);

  /**
   * Copies the structure's current load rating onto it — what legacy's rating procedures do after
   * each change — and, when given, sets Load Rating Review Required.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CrossingStructureEntity structure
         SET structure.currentLoadRating = :rating,
             structure.loadRatingUnknownIndicator =
               COALESCE(:reviewInd, structure.loadRatingUnknownIndicator)
       WHERE structure.crossingStructureId = :id
      """)
  int updateCurrentLoadRating(
      @Param("id") Long id,
      @Param("rating") BigDecimal rating,
      @Param("reviewInd") String reviewInd);

  /**
   * Writes the Type/Class and Portable Structure the Details tab's Bridge card holds, with who
   * changed them and when.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CrossingStructureEntity structure
         SET structure.structureTypeClassCode = :typeClassCode,
             structure.portableStructureInd = :portableInd,
             structure.updateUserid = :user,
             structure.updateTimestamp = LOCAL DATETIME
       WHERE structure.crossingStructureId = :id
      """)
  int updateBridgeIdentity(
      @Param("id") Long id,
      @Param("typeClassCode") String typeClassCode,
      @Param("portableInd") String portableInd,
      @Param("user") String user);

  /**
   * Writes the Details tab's Structure card, with who changed it and when. Only these columns, as
   * the schedule's update.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CrossingStructureEntity structure
         SET structure.builtByCode = :builtByCode,
             structure.yearFabricated = :yearFabricated,
             structure.yearBuilt = :yearBuilt,
             structure.inventoryAddedYear = :inventoryAddedYear,
             structure.structureSourceCode = :sourceCode,
             structure.originalReplacementYear = :endOfDesignLifeYear,
             structure.asBuiltInfoPresentInd = :asBuiltInd,
             structure.installationCost = :installationCost,
             structure.materialCost = :materialCost,
             structure.updateUserid = :user,
             structure.updateTimestamp = LOCAL DATETIME
       WHERE structure.crossingStructureId = :id
      """)
  @SuppressWarnings("java:S107") // One parameter a column, as the card has them.
  int updateStructureDetails(
      @Param("id") Long id,
      @Param("builtByCode") String builtByCode,
      @Param("yearFabricated") Integer yearFabricated,
      @Param("yearBuilt") Integer yearBuilt,
      @Param("inventoryAddedYear") Integer inventoryAddedYear,
      @Param("sourceCode") String sourceCode,
      @Param("endOfDesignLifeYear") Integer endOfDesignLifeYear,
      @Param("asBuiltInd") String asBuiltInd,
      @Param("installationCost") Long installationCost,
      @Param("materialCost") Long materialCost,
      @Param("user") String user);

  /**
   * Whether another structure, archived or not, has this name — the database's {@code CS_UK}.
   * Legacy checked active structures only, so a name an archived structure held passed its form and
   * then failed the constraint.
   */
  boolean existsByCrossingStructureNameAndCrossingStructureIdNot(String name, Long id);

  /**
   * The structure with this name, archived or not — the names are unique ({@code CS_UK}). How
   * legacy's replacement history found the other structure of a link.
   */
  Optional<CrossingStructureEntity> findByCrossingStructureName(String name);

  /** The first 20 structures, by name, whose Structure # starts with this — any case. */
  List<CrossingStructureEntity>
      findTop20ByCrossingStructureNameStartingWithIgnoreCaseOrderByCrossingStructureNameAsc(
          String prefix);

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
