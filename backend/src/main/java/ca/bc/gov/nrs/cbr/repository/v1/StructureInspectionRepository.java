package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import java.time.LocalDate;
import java.util.Collection;
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
   * year the superstructure was installed — and those not dated yet: an In Progress inspection may
   * wait for its date, and is the newest work on the structure, so it is listed first. Legacy hid
   * them with the early ones. Joined inner to the current status, as legacy is, so an
   * inspection with no status history is not listed; the status and its code are fetched with the
   * page rather than one row at a time.
   */
  @Query(value = """
      SELECT inspection
        FROM StructureInspectionEntity inspection
        JOIN FETCH inspection.currentStatus status
        LEFT JOIN FETCH status.statusCode
       WHERE inspection.crossingStructureId = :structureId
         AND (:includeAll = TRUE OR inspection.inspectionDate IS NULL
              OR inspection.inspectionDate > :after)
       ORDER BY CASE WHEN inspection.inspectionDate IS NULL THEN 0 ELSE 1 END,
                inspection.inspectionDate DESC, inspection.inspectionId DESC
      """,
      countQuery = """
      SELECT COUNT(inspection)
        FROM StructureInspectionEntity inspection
        JOIN inspection.currentStatus status
       WHERE inspection.crossingStructureId = :structureId
         AND (:includeAll = TRUE OR inspection.inspectionDate IS NULL
              OR inspection.inspectionDate > :after)
      """)
  Page<StructureInspectionEntity> findPageByStructure(
      @Param("structureId") Long structureId,
      @Param("after") LocalDate after,
      @Param("includeAll") boolean includeAll,
      Pageable pageable);

  /**
   * How many of a structure's inspections {@link #findPageByStructure} leaves out without
   * {@code includeAll}: those dated on or before {@code after}. One not dated yet is listed.
   */
  @Query("""
      SELECT COUNT(inspection)
        FROM StructureInspectionEntity inspection
        JOIN inspection.currentStatus status
       WHERE inspection.crossingStructureId = :structureId
         AND inspection.inspectionDate <= :after
      """)
  long countBeforeInstall(
      @Param("structureId") Long structureId, @Param("after") LocalDate after);

  /**
   * Whether the structure already has an inspection on this date — legacy's same-date rule
   * (InspectionForm.java:276-299), offline ({@code OFL}) inspections aside. An inspection with no
   * status history counts.
   */
  @Query("""
      SELECT COUNT(inspection) > 0
        FROM StructureInspectionEntity inspection
        LEFT JOIN inspection.currentStatus status
       WHERE inspection.crossingStructureId = :structureId
         AND inspection.inspectionDate = :date
         AND (status IS NULL OR status.inspectionReportStatusCode <> 'OFL')
      """)
  boolean existsOnDate(@Param("structureId") Long structureId, @Param("date") LocalDate date);

  /**
   * As {@link #existsOnDate}, leaving out the inspection being edited — legacy's same-date rule on
   * its update, which skipped the inspection itself.
   */
  @Query("""
      SELECT COUNT(inspection) > 0
        FROM StructureInspectionEntity inspection
        LEFT JOIN inspection.currentStatus status
       WHERE inspection.crossingStructureId = :structureId
         AND inspection.inspectionDate = :date
         AND inspection.inspectionId <> :inspectionId
         AND (status IS NULL OR status.inspectionReportStatusCode <> 'OFL')
      """)
  boolean existsOnDateExcept(@Param("structureId") Long structureId,
      @Param("date") LocalDate date, @Param("inspectionId") Long inspectionId);

  /**
   * Each structure's last routine inspection date — the Structure Inventory report's "Last Inspctn
   * Date". Legacy's {@code STRCTRE_INVNTRY_SUMMRY_RPT}: the latest {@code ROUT} inspection that has
   * any status at all, whatever that status is.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.repository.v1.StructureValue(
               inspection.crossingStructureId, MAX(inspection.inspectionDate))
        FROM StructureInspectionEntity inspection
       WHERE inspection.crossingStructureId IN :structureIds
         AND inspection.strctreInspectionTypeCode = 'ROUT'
         AND EXISTS (SELECT 1 FROM InspectionReportStatusEntity status
                      WHERE status.inspectionId = inspection.inspectionId)
       GROUP BY inspection.crossingStructureId
      """)
  List<StructureValue<LocalDate>> findLastRoutineInspectionDates(
      @Param("structureIds") Collection<Long> structureIds);

  /**
   * Each structure's last reviewed routine inspection date — the Inspections Due reports' "Last
   * Inspection Date". Legacy's {@code INSPCTNS_DUE_SUMMARY}: the latest {@code ROUT} inspection
   * that has ever been Reviewed or Accepted.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.repository.v1.StructureValue(
               inspection.crossingStructureId, MAX(inspection.inspectionDate))
        FROM StructureInspectionEntity inspection
       WHERE inspection.crossingStructureId IN :structureIds
         AND inspection.strctreInspectionTypeCode = 'ROUT'
         AND EXISTS (SELECT 1 FROM InspectionReportStatusEntity status
                      WHERE status.inspectionId = inspection.inspectionId
                        AND status.inspectionReportStatusCode IN ('RVD', 'ACC'))
       GROUP BY inspection.crossingStructureId
      """)
  List<StructureValue<LocalDate>> findLastReviewedRoutineInspectionDates(
      @Param("structureIds") Collection<Long> structureIds);

  /**
   * Each structure's last routine inspection date, whatever the inspection's status — the
   * Structure Inventory – Detailed report's "Last Inspection Date". Legacy's
   * {@code STRCTRE_INVNTRY_DTL_RPT} asks for no status, where the Summary's asks for one.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.repository.v1.StructureValue(
               inspection.crossingStructureId, MAX(inspection.inspectionDate))
        FROM StructureInspectionEntity inspection
       WHERE inspection.crossingStructureId IN :structureIds
         AND inspection.strctreInspectionTypeCode = 'ROUT'
       GROUP BY inspection.crossingStructureId
      """)
  List<StructureValue<LocalDate>> findLastRoutineInspectionDatesAnyStatus(
      @Param("structureIds") Collection<Long> structureIds);

  /**
   * Each structure's newest routine status row — the report's "Inspection Status", whose code is
   * read by id afterwards. Legacy's rule: the highest {@code INSPECTION_REPORT_STATUS_ID} across
   * all the structure's {@code ROUT} inspections, which is not always the status of the inspection
   * with the latest date.
   *
   * <p>A plain grouped join rather than legacy's correlated subquery, which made Oracle look for
   * the newest status again for every status row it considered.
   */
  @Query("""
      SELECT new ca.bc.gov.nrs.cbr.repository.v1.StructureValue(
               inspection.crossingStructureId, MAX(status.inspectionReportStatusId))
        FROM InspectionReportStatusEntity status, StructureInspectionEntity inspection
       WHERE status.inspectionId = inspection.inspectionId
         AND inspection.crossingStructureId IN :structureIds
         AND inspection.strctreInspectionTypeCode = 'ROUT'
       GROUP BY inspection.crossingStructureId
      """)
  List<StructureValue<Long>> findLatestRoutineStatusIds(
      @Param("structureIds") Collection<Long> structureIds);
}
