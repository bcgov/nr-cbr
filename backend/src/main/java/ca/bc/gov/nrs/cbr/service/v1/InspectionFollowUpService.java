package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.InspectionLockedException;
import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairUpdateRequest;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The repairs and monitoring items changed from an inspection's page — legacy's Repairs &
 * Monitoring tab ({@code cbr-inspection-page.local.md} §6).
 *
 * <p>As legacy: one added here belongs to the inspection; any of the structure's may be edited or
 * deleted from it, whichever inspection raised it. The rules are the structure page's — the same
 * services check them. Unlike legacy, which only disabled its page in the browser, a Reviewed,
 * Accepted or Rejected inspection is refused here (409).
 */
@Service
public class InspectionFollowUpService {

  /** The statuses after which an inspection is no longer changed from its page. */
  private static final Map<String, String> LOCKING_STATUSES =
      Map.of("RVD", "Reviewed", "ACC", "Accepted", "REJ", "Rejected");

  private final StructureInspectionRepository inspections;
  private final StructureRepairsService repairs;
  private final StructureMonitorsService monitors;

  public InspectionFollowUpService(StructureInspectionRepository inspections,
      StructureRepairsService repairs, StructureMonitorsService monitors) {
    this.inspections = inspections;
    this.repairs = repairs;
    this.monitors = monitors;
  }

  /** Adds a repair to the inspection's structure, raised by the inspection. */
  @Transactional
  public RepairCreatedResponse addRepair(long inspectionId, RepairCreateRequest request) {
    return repairs.create(open(inspectionId), inspectionId, request);
  }

  /** Saves an edit to one of the structure's repairs. */
  @Transactional
  public void updateRepair(long inspectionId, long repairId, RepairUpdateRequest request) {
    repairs.update(open(inspectionId), repairId, request);
  }

  /** Deletes one of the structure's repairs. */
  @Transactional
  public void deleteRepair(long inspectionId, long repairId) {
    repairs.delete(open(inspectionId), repairId);
  }

  /** Adds a monitoring item to the inspection's structure, raised by the inspection. */
  @Transactional
  public MonitorCreatedResponse addMonitor(long inspectionId, MonitorCreateRequest request) {
    return monitors.create(open(inspectionId), inspectionId, request);
  }

  /** Saves an edit to one of the structure's monitoring items. */
  @Transactional
  public void updateMonitor(long inspectionId, long monitorId, MonitorUpdateRequest request) {
    monitors.update(open(inspectionId), monitorId, request);
  }

  /** Deletes one of the structure's monitoring items. */
  @Transactional
  public void deleteMonitor(long inspectionId, long monitorId) {
    monitors.delete(open(inspectionId), monitorId);
  }

  /**
   * The inspection's structure, once the inspection is known to exist and still open to changes.
   *
   * @throws InspectionNotFoundException if there is no such inspection
   * @throws InspectionLockedException   if it has been reviewed or rejected
   */
  private long open(long inspectionId) {
    StructureInspectionEntity inspection = inspections.findById(inspectionId)
        .orElseThrow(() -> new InspectionNotFoundException(inspectionId));
    InspectionReportStatusEntity status = inspection.getCurrentStatus();
    String code = status == null ? null : status.getInspectionReportStatusCode();
    if (code != null && LOCKING_STATUSES.containsKey(code)) {
      throw new InspectionLockedException(inspectionId, LOCKING_STATUSES.get(code));
    }
    return inspection.getCrossingStructureId();
  }
}
