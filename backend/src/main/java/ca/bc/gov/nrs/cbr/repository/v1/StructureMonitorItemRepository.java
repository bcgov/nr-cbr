package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** {@code THE.STRUCTURE_MONITOR_ITEMS} — monitoring items raised by an inspection. */
@Repository
public interface StructureMonitorItemRepository
    extends JpaRepository<StructureMonitorItemEntity, Long> {

  void deleteByInspectionId(Long inspectionId);

  /**
   * A page of a structure's monitoring items — legacy's {@code FIND_MONITORS_BY_STRUCTURE_ID}, or
   * with {@code outstandingOnly} its {@code FIND_OUTSTANDING_STRC_MONITORS}: suggested or required.
   *
   * <p>Without {@code includeBeforeInstall}, an item raised by an inspection dated on or before
   * {@code installed} — 1 January of the year the superstructure was installed — is left out, as
   * legacy's table leaves it until "Show Inspections before the Superstructure Install Date" is
   * ticked. An item with no inspection, or an undated one, is always listed.
   *
   * <p>In legacy's order: status descending, then newest inspection (an item with no inspection
   * first, as Oracle sorts it), then number descending.
   */
  @Query(value = """
      SELECT monitor
        FROM StructureMonitorItemEntity monitor
        LEFT JOIN FETCH monitor.inspection inspection
       WHERE monitor.crossingStructureId = :structureId
         AND (:outstandingOnly = FALSE OR monitor.monitoringStatusCode IN :outstandingStatuses)
         AND (:includeBeforeInstall = TRUE
              OR inspection IS NULL
              OR inspection.inspectionDate IS NULL
              OR inspection.inspectionDate > :installed)
       ORDER BY monitor.monitoringStatusCode DESC,
                inspection.inspectionDate DESC NULLS FIRST,
                monitor.monitorNumber DESC
      """,
      countQuery = """
      SELECT COUNT(monitor)
        FROM StructureMonitorItemEntity monitor
        LEFT JOIN monitor.inspection inspection
       WHERE monitor.crossingStructureId = :structureId
         AND (:outstandingOnly = FALSE OR monitor.monitoringStatusCode IN :outstandingStatuses)
         AND (:includeBeforeInstall = TRUE
              OR inspection IS NULL
              OR inspection.inspectionDate IS NULL
              OR inspection.inspectionDate > :installed)
      """)
  Page<StructureMonitorItemEntity> findPageByStructure(
      @Param("structureId") Long structureId,
      @Param("outstandingOnly") boolean outstandingOnly,
      @Param("outstandingStatuses") Collection<String> outstandingStatuses,
      @Param("includeBeforeInstall") boolean includeBeforeInstall,
      @Param("installed") LocalDate installed,
      Pageable pageable);

  /**
   * How many of the structure's items, in the view chosen, {@link #findPageByStructure} leaves out
   * without {@code includeBeforeInstall}: those raised by an inspection dated on or before
   * {@code installed}.
   */
  @Query("""
      SELECT COUNT(monitor)
        FROM StructureMonitorItemEntity monitor
        JOIN monitor.inspection inspection
       WHERE monitor.crossingStructureId = :structureId
         AND (:outstandingOnly = FALSE OR monitor.monitoringStatusCode IN :outstandingStatuses)
         AND inspection.inspectionDate <= :installed
      """)
  long countBeforeInstall(
      @Param("structureId") Long structureId,
      @Param("outstandingOnly") boolean outstandingOnly,
      @Param("outstandingStatuses") Collection<String> outstandingStatuses,
      @Param("installed") LocalDate installed);
}
