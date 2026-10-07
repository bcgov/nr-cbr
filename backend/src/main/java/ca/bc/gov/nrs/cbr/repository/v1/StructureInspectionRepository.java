package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * {@code THE.STRUCTURE_INSPECTION}.
 *
 * <p>{@link JpaSpecificationExecutor} for the same reason
 * {@link CrossingSiteRepository} has it: nineteen optional criteria cannot be expressed as a method
 * name, and the alternative — the stored procedure's caller-built {@code WHERE} clause with a
 * parallel bind array — is the thing this replaces. See
 * {@link ca.bc.gov.nrs.cbr.specification.v1.InspectionSearchSpecifications}.
 *
 * <p>Its {@code findAll(Specification, Pageable)} runs the page and the count as two queries, the
 * same split legacy makes with {@code FIND_INSPECTIONS_BY_CRITERIA} and
 * {@code COUNT_INSPECTIONS_BY_CRITERIA} — except that here both are generated from one
 * specification and cannot drift apart. Legacy's count is also not a count: it runs the full
 * {@code SELECT} and calls {@code results.size()} in Java.
 */
@Repository
public interface StructureInspectionRepository
    extends JpaRepository<StructureInspectionEntity, Long>,
    JpaSpecificationExecutor<StructureInspectionEntity> {

  /**
   * The date of the structure's most recent <em>reviewed</em> inspection.
   *
   * <p>One half of {@code CBR.FIND_CRNT_LD_RATING_ID}: an inspection's load rating only counts once
   * the inspection has reached a terminal status, so this is the date the winning inspection rating
   * would carry.
   *
   * <p>"Reviewed" is the inspection's <em>current</em> status — the newest row of its history — being
   * one of the terminal codes. Legacy computes that with a window function over
   * {@code INSPECTION_REPORT_STATUS}; here it reads through
   * {@link StructureInspectionEntity#getCurrentStatus()}, which is the same definition expressed
   * once on the entity.
   *
   * @param terminalStatuses {@code RVD} and {@code ACC} — see {@code LoadRatingService}
   */
  @Query("""
      SELECT MAX(inspection.inspectionDate)
        FROM StructureInspectionEntity inspection
       WHERE inspection.crossingStructureId = :structureId
         AND inspection.currentStatus.inspectionReportStatusCode IN :terminalStatuses
      """)
  Optional<LocalDate> findLatestReviewedInspectionDate(
      @Param("structureId") Long structureId,
      @Param("terminalStatuses") List<String> terminalStatuses);

  /**
   * A structure's inspections whose report was reviewed — current status {@code RVD} or
   * {@code ACC}, with a reviewer recorded. Each is a row of the structure page's load rating
   * history, with or without a rating, as legacy's {@code FIND_LOAD_RATINGS_BY_STRC_ID} lists them.
   */
  @Query("""
      SELECT inspection
        FROM StructureInspectionEntity inspection
        JOIN FETCH inspection.currentStatus status
       WHERE inspection.crossingStructureId = :structureId
         AND status.inspectionReportStatusCode IN :reviewedStatuses
         AND inspection.inspectionReviewerId IS NOT NULL
      """)
  List<StructureInspectionEntity> findReviewedByStructure(
      @Param("structureId") Long structureId,
      @Param("reviewedStatuses") List<String> reviewedStatuses);

  /**
   * A page of a structure's inspections, newest first — legacy's
   * {@code FIND_NEW_INSPECTIONS_BY_STRUCT}, or with {@code includeAll}
   * {@code FIND_INSPECTIONS_BY_STRUCTURE}.
   *
   * <p>Without {@code includeAll}, only inspections dated after {@code after} — 1 January of the
   * year the superstructure was installed. Joined inner to the current status, as legacy is, so an
   * inspection with no status history is not listed; the status and its code are fetched with the
   * page rather than one row at a time.
   */
  @Query(value = """
      SELECT inspection
        FROM StructureInspectionEntity inspection
        JOIN FETCH inspection.currentStatus status
        LEFT JOIN FETCH status.statusCode
       WHERE inspection.crossingStructureId = :structureId
         AND (:includeAll = TRUE OR inspection.inspectionDate > :after)
       ORDER BY inspection.inspectionDate DESC, inspection.inspectionId DESC
      """,
      countQuery = """
      SELECT COUNT(inspection)
        FROM StructureInspectionEntity inspection
        JOIN inspection.currentStatus status
       WHERE inspection.crossingStructureId = :structureId
         AND (:includeAll = TRUE OR inspection.inspectionDate > :after)
      """)
  Page<StructureInspectionEntity> findPageByStructure(
      @Param("structureId") Long structureId,
      @Param("after") LocalDate after,
      @Param("includeAll") boolean includeAll,
      Pageable pageable);

  /**
   * How many of a structure's inspections {@link #findPageByStructure} leaves out without
   * {@code includeAll}: those dated on or before {@code after}, or not dated at all.
   */
  @Query("""
      SELECT COUNT(inspection)
        FROM StructureInspectionEntity inspection
        JOIN inspection.currentStatus status
       WHERE inspection.crossingStructureId = :structureId
         AND (inspection.inspectionDate IS NULL OR inspection.inspectionDate <= :after)
      """)
  long countBeforeInstall(
      @Param("structureId") Long structureId, @Param("after") LocalDate after);
}
