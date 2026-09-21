package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.STRUCTURE_LOAD_RATING}.
 *
 * <p>The two reads here are the halves of {@code CBR.FIND_CRNT_LD_RATING_ID}: the latest manually
 * entered rating, and the rating from a reviewed inspection on a given date. The rule for choosing
 * between them lives in {@link ca.bc.gov.nrs.cbr.service.v1.LoadRatingService}, where it can be read
 * as a rule rather than as SQL.
 */
@Repository
public interface StructureLoadRatingRepository
    extends JpaRepository<StructureLoadRatingEntity, Long> {

  /**
   * The most recent manually entered rating for a structure.
   *
   * <p>"Manual" is {@code INSPECTION_ID IS NULL} — typed in rather than produced by an inspection —
   * and it counts from the moment it was entered, which is why this orders by
   * {@code ENTRY_TIMESTAMP} rather than by any inspection date.
   */
  Optional<StructureLoadRatingEntity>
      findFirstByCrossingStructureIdAndInspectionIdIsNullOrderByEntryTimestampDesc(
          Long crossingStructureId);

  /**
   * The ratings produced by a structure's reviewed inspections on a given date.
   *
   * <p>"Reviewed" is the inspection's <em>current</em> status being one of the terminal codes — the
   * newest row of its status history, the same definition
   * {@code StructureInspectionEntity.currentStatus} maps and {@code CBR.GET_LAST_STATUS_ID}
   * returns.
   *
   * <p><b>A list rather than one row, and that is a deliberate difference from legacy.</b> Two
   * inspections of one structure on the same date are possible in historical data, and the
   * procedure's {@code SELECT … INTO} raises {@code TOO_MANY_ROWS} when it meets them — failing the
   * whole delete. The caller takes the highest id instead.
   */
  @Query("""
      SELECT rating
        FROM StructureLoadRatingEntity rating
        JOIN StructureInspectionEntity inspection
          ON inspection.inspectionId = rating.inspectionId
       WHERE rating.crossingStructureId = :structureId
         AND inspection.inspectionDate = :inspectionDate
         AND inspection.currentStatus.inspectionReportStatusCode IN :terminalStatuses
       ORDER BY rating.structureLoadRatingId DESC
      """)
  List<StructureLoadRatingEntity> findReviewedRatingsOn(
      @Param("structureId") Long structureId,
      @Param("inspectionDate") LocalDate inspectionDate,
      @Param("terminalStatuses") List<String> terminalStatuses);

  /** Deletes every rating an inspection produced. */
  void deleteByInspectionId(Long inspectionId);
}
