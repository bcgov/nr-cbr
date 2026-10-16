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
import ca.bc.gov.nrs.cbr.struct.v1.DocumentDetailsRequest;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The repairs and monitoring items changed from an inspection's page — legacy's Repairs &
 * Monitoring tab ({@code cbr-inspection-page.local.md} §6).
 *
 * <p>As legacy: one added here belongs to the inspection; any of the structure's may be edited or
 * deleted from it, whichever inspection raised it. Documents & Photos the same, but with legacy's
 * narrower lock — see {@link #addDocument}. The rules are the structure page's — the same
 * services check them. Unlike legacy, which only disabled its page in the browser, a Reviewed,
 * Accepted or Rejected inspection is refused here (409).
 */
@Service
public class InspectionFollowUpService {

  /** The statuses after which its repairs and monitoring items are no longer changed from it. */
  private static final Map<String, String> LOCKING_STATUSES =
      Map.of("RVD", "Reviewed", "ACC", "Accepted", "REJ", "Rejected");
  /**
   * The statuses after which no document is added from it — legacy's: its page disables the Add
   * button once Reviewed or Accepted, not once Rejected, and never its delete icons.
   */
  private static final Map<String, String> DOCUMENT_LOCKING_STATUSES =
      Map.of("RVD", "Reviewed", "ACC", "Accepted");
  private static final String REPAIRS_AND_MONITORS =
      "its repairs and monitoring items can no longer be changed from it";

  private final StructureInspectionRepository inspections;
  private final StructureRepairsService repairs;
  private final StructureMonitorsService monitors;
  private final StructureDocumentsService documents;

  public InspectionFollowUpService(StructureInspectionRepository inspections,
      StructureRepairsService repairs, StructureMonitorsService monitors,
      StructureDocumentsService documents) {
    this.inspections = inspections;
    this.repairs = repairs;
    this.monitors = monitors;
    this.documents = documents;
  }

  /** Adds a repair to the inspection's structure, raised by the inspection. */
  @Transactional
  public RepairCreatedResponse addRepair(long inspectionId, RepairCreateRequest request) {
    return repairs.create(openToFollowUp(inspectionId), inspectionId, request);
  }

  /** Saves an edit to one of the structure's repairs. */
  @Transactional
  public void updateRepair(long inspectionId, long repairId, RepairUpdateRequest request) {
    repairs.update(openToFollowUp(inspectionId), repairId, request);
  }

  /** Deletes one of the structure's repairs. */
  @Transactional
  public void deleteRepair(long inspectionId, long repairId) {
    repairs.delete(openToFollowUp(inspectionId), repairId);
  }

  /** Adds a monitoring item to the inspection's structure, raised by the inspection. */
  @Transactional
  public MonitorCreatedResponse addMonitor(long inspectionId, MonitorCreateRequest request) {
    return monitors.create(openToFollowUp(inspectionId), inspectionId, request);
  }

  /** Saves an edit to one of the structure's monitoring items. */
  @Transactional
  public void updateMonitor(long inspectionId, long monitorId, MonitorUpdateRequest request) {
    monitors.update(openToFollowUp(inspectionId), monitorId, request);
  }

  /** Deletes one of the structure's monitoring items. */
  @Transactional
  public void deleteMonitor(long inspectionId, long monitorId) {
    monitors.delete(openToFollowUp(inspectionId), monitorId);
  }

  /**
   * Adds a document or photo to the inspection's structure, the inspection's — legacy's Add on its
   * Documents &amp; Photos tab. Refused once it is Reviewed or Accepted, as legacy disables the
   * button.
   *
   * @return the new document's id
   */
  @Transactional
  public String addDocument(long inspectionId, MultipartFile file, DocumentDetailsRequest details) {
    long structureId = open(inspectionId, DOCUMENT_LOCKING_STATUSES,
        "documents and photos can no longer be added to it");
    return documents.upload(structureId, inspectionId, file, details);
  }

  /**
   * Deletes one of the structure's documents, from the inspection's page — any of them, at any
   * status: legacy's delete icon, which its page never disables.
   */
  @Transactional
  public void deleteDocument(long inspectionId, long fileId) {
    documents.delete(open(inspectionId, Map.of(), ""), fileId);
  }

  /** The inspection's structure, for a change to its repairs or monitoring items. */
  private long openToFollowUp(long inspectionId) {
    return open(inspectionId, LOCKING_STATUSES, REPAIRS_AND_MONITORS);
  }

  /**
   * The inspection's structure, once the inspection is known to exist and still open to changes.
   *
   * @param locking the statuses after which this change is refused, with how each reads
   * @param what    what can no longer be changed, for the refusal
   * @throws InspectionNotFoundException if there is no such inspection
   * @throws InspectionLockedException   if it has a locking status
   */
  private long open(long inspectionId, Map<String, String> locking, String what) {
    StructureInspectionEntity inspection = inspections.findById(inspectionId)
        .orElseThrow(() -> new InspectionNotFoundException(inspectionId));
    InspectionReportStatusEntity status = inspection.getCurrentStatus();
    String code = status == null ? null : status.getInspectionReportStatusCode();
    if (code != null && locking.containsKey(code)) {
      throw new InspectionLockedException(inspectionId, locking.get(code), what);
    }
    return inspection.getCrossingStructureId();
  }
}
