package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.MonitorFrequencyCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.MonitorFrequencyCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.MonitoringStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureMonitorItemRepository;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse.Monitor;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse.View;
import ca.bc.gov.nrs.cbr.struct.v1.UserAudit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The structure page's Monitoring tab — legacy's {@code monitorTab.jsp}, a page at a time.
 *
 * <p>Read-only for now; legacy's Add, Edit (Level 1) and Delete come with the page's editing.
 * Legacy inner-joins the status code, so an item whose status has no row would vanish there; here
 * it stays, its code shown without a description.
 */
@Service
public class StructureMonitorsService {

  /** Suggested, required — legacy's outstanding statuses. */
  private static final List<String> OUTSTANDING_STATUSES = List.of("SUG", "REQ");
  private static final int MAX_PAGE_SIZE = 100;

  private final CrossingStructureRepository structures;
  private final StructureMonitorItemRepository monitors;
  private final MonitoringStatusCodeRepository statuses;
  private final MonitorFrequencyCodeRepository frequencies;

  public StructureMonitorsService(
      CrossingStructureRepository structures,
      StructureMonitorItemRepository monitors,
      MonitoringStatusCodeRepository statuses,
      MonitorFrequencyCodeRepository frequencies) {
    this.structures = structures;
    this.monitors = monitors;
    this.statuses = statuses;
    this.frequencies = frequencies;
  }

  /**
   * A page of the structure's monitoring items, in legacy's order.
   *
   * @param view       outstanding only, or all
   * @param pageNumber zero-based
   * @param pageSize   held to between 1 and 100
   * @throws StructureNotFoundException if there is no such structure
   */
  @Transactional(readOnly = true)
  public PagedResponse<Monitor> monitors(
      long structureId, View view, int pageNumber, int pageSize) {
    if (!structures.existsById(structureId)) {
      throw new StructureNotFoundException(structureId);
    }
    Page<StructureMonitorItemEntity> page = monitors.findPageByStructure(
        structureId, view == View.OUTSTANDING, OUTSTANDING_STATUSES,
        PageRequest.of(Math.max(pageNumber, 0), Math.clamp(pageSize, 1, MAX_PAGE_SIZE)));
    List<StructureMonitorItemEntity> rows = page.getContent();

    Map<String, String> statusNames = statuses
        .findAllById(codes(rows, StructureMonitorItemEntity::getMonitoringStatusCode)).stream()
        .collect(Collectors.toMap(MonitoringStatusCodeEntity::getMonitoringStatusCode,
            MonitoringStatusCodeEntity::getDescription));
    Map<String, String> frequencyNames = frequencies
        .findAllById(codes(rows, StructureMonitorItemEntity::getMonitorFrequencyCode)).stream()
        .collect(Collectors.toMap(MonitorFrequencyCodeEntity::getMonitorFrequencyCode,
            MonitorFrequencyCodeEntity::getDescription));

    List<Monitor> content = rows.stream()
        .map(monitor -> new Monitor(
            String.valueOf(monitor.getMonitorId()),
            monitor.getMonitorNumber(),
            code(monitor.getMonitoringStatusCode(), statusNames),
            UserAudit.of(monitor.getSuggestedByUserid(), monitor.getSuggestedByTimestamp()),
            UserAudit.of(monitor.getRequiredByUserid(), monitor.getRequiredByTimestamp()),
            UserAudit.of(monitor.getCompletedByUserid(), monitor.getCompletedByTimestamp()),
            monitor.getInspectionId() == null ? null : String.valueOf(monitor.getInspectionId()),
            monitor.getInspection() == null ? null : monitor.getInspection().getInspectionDate(),
            monitor.getDescription(),
            code(monitor.getMonitorFrequencyCode(), frequencyNames),
            monitor.getMonitorFrequencyCmt()))
        .toList();
    return new PagedResponse<>(content, page.getTotalElements(), page.getTotalPages(),
        page.getNumber(), page.getSize());
  }

  private static List<String> codes(List<StructureMonitorItemEntity> rows,
      Function<StructureMonitorItemEntity, String> codeOf) {
    return rows.stream().map(codeOf).filter(Objects::nonNull).distinct().toList();
  }

  private static CodeValue code(String code, Map<String, String> descriptions) {
    return code == null ? CodeValue.NONE : new CodeValue(code, descriptions.get(code));
  }
}
