package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
}
