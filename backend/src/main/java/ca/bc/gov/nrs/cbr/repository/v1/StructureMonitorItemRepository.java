package ca.bc.gov.nrs.cbr.repository.v1;

import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
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
   * <p>In legacy's order: status descending, then newest inspection (an item with no inspection
   * first, as Oracle sorts it), then number descending.
   */
  @Query(value = """
      SELECT monitor
        FROM StructureMonitorItemEntity monitor
        LEFT JOIN FETCH monitor.inspection inspection
       WHERE monitor.crossingStructureId = :structureId
         AND (:outstandingOnly = FALSE OR monitor.monitoringStatusCode IN :outstandingStatuses)
       ORDER BY monitor.monitoringStatusCode DESC,
                inspection.inspectionDate DESC NULLS FIRST,
                monitor.monitorNumber DESC
      """,
      countQuery = """
      SELECT COUNT(monitor)
        FROM StructureMonitorItemEntity monitor
       WHERE monitor.crossingStructureId = :structureId
         AND (:outstandingOnly = FALSE OR monitor.monitoringStatusCode IN :outstandingStatuses)
      """)
  Page<StructureMonitorItemEntity> findPageByStructure(
      @Param("structureId") Long structureId,
      @Param("outstandingOnly") boolean outstandingOnly,
      @Param("outstandingStatuses") Collection<String> outstandingStatuses,
      Pageable pageable);
}
