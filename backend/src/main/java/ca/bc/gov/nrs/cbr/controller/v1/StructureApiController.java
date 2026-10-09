package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.StructureApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailService;
import ca.bc.gov.nrs.cbr.service.v1.StructureDocumentsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureDocumentsService.DocumentFile;
import ca.bc.gov.nrs.cbr.service.v1.StructureInspectionsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureMonitorsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureRepairsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureSearchService;
import ca.bc.gov.nrs.cbr.service.v1.StructureService;
import ca.bc.gov.nrs.cbr.service.v1.StructureSpansAndPiersService;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDocumentsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionScheduleResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import java.nio.charset.StandardCharsets;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Structures. Mappings and authorization are declared on {@link StructureApiEndpoint}. */
@RestController
public class StructureApiController implements StructureApiEndpoint {

  private final StructureSearchService structureSearchService;
  private final StructureService structureService;
  private final StructureDetailService structureDetailService;
  private final StructureSpansAndPiersService spansAndPiersService;
  private final StructureDocumentsService documentsService;
  private final StructureInspectionsService inspectionsService;
  private final StructureRepairsService repairsService;
  private final StructureMonitorsService monitorsService;

  public StructureApiController(
      StructureSearchService structureSearchService,
      StructureService structureService,
      StructureDetailService structureDetailService,
      StructureSpansAndPiersService spansAndPiersService,
      StructureDocumentsService documentsService,
      StructureInspectionsService inspectionsService,
      StructureRepairsService repairsService,
      StructureMonitorsService monitorsService) {
    this.structureSearchService = structureSearchService;
    this.structureService = structureService;
    this.structureDetailService = structureDetailService;
    this.spansAndPiersService = spansAndPiersService;
    this.documentsService = documentsService;
    this.inspectionsService = inspectionsService;
    this.repairsService = repairsService;
    this.monitorsService = monitorsService;
  }

  @Override
  public ResponseEntity<PagedResponse<StructureSearchResult>> searchStructures(
      StructureSearchCriteria criteria, int pageNumber, int pageSize,
      StructureSortColumn sortBy, Sort.Direction sortDirection) {
    return ResponseEntity.ok(
        structureSearchService.search(criteria, pageNumber, pageSize, sortBy, sortDirection));
  }

  @Override
  public StructureArchiveResponse archiveStructures(StructureArchiveRequest request) {
    return structureService.archive(request.structureIds());
  }

  @Override
  public ResponseEntity<Void> deleteStructure(long structureId) {
    structureService.delete(structureId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public StructureRepairResponsibilityResponse updateRepairResponsibility(
      StructureRepairResponsibilityRequest request) {
    return structureService.updateRepairResponsibility(request);
  }

  @Override
  public StructureDetailResponse getStructure(long structureId) {
    return structureDetailService.findById(structureId);
  }

  @Override
  public StructureSpansAndPiersResponse getSpansAndPiers(long structureId) {
    return spansAndPiersService.findByStructure(structureId);
  }

  @Override
  public StructureDocumentsResponse getDocuments(long structureId) {
    return documentsService.findByStructure(structureId);
  }

  @Override
  public ResponseEntity<byte[]> getDocumentFile(long structureId, long fileId) {
    DocumentFile file = documentsService.file(structureId, fileId);
    ContentDisposition disposition =
        (file.inline() ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(file.filename(), StandardCharsets.UTF_8)
            .build();
    return ResponseEntity.ok()
        .contentType(file.mediaType())
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        // The type is decided here, from the name; a browser must not second-guess it.
        .header("X-Content-Type-Options", "nosniff")
        .body(file.content());
  }

  @Override
  public StructureInspectionScheduleResponse getInspectionSchedule(long structureId) {
    return inspectionsService.schedule(structureId);
  }

  @Override
  public StructureInspectionsResponse getInspections(
      long structureId, int pageNumber, int pageSize, boolean includeBeforeInstall) {
    return inspectionsService.inspections(
        structureId, pageNumber, pageSize, includeBeforeInstall);
  }

  @Override
  public StructureRepairsResponse.Listing getRepairs(long structureId,
      StructureRepairsResponse.View view, int pageNumber, int pageSize,
      boolean includeBeforeInstall) {
    return repairsService.repairs(structureId, view, pageNumber, pageSize, includeBeforeInstall);
  }

  @Override
  public ResponseEntity<Void> deleteRepair(long structureId, long repairId) {
    repairsService.delete(structureId, repairId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public StructureMonitorsResponse.Listing getMonitors(long structureId,
      StructureMonitorsResponse.View view, int pageNumber, int pageSize,
      boolean includeBeforeInstall) {
    return monitorsService.monitors(
        structureId, view, pageNumber, pageSize, includeBeforeInstall);
  }

  @Override
  public ResponseEntity<MonitorCreatedResponse> createMonitor(
      long structureId, MonitorCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(monitorsService.create(structureId, request));
  }

  @Override
  public ResponseEntity<Void> updateMonitor(
      long structureId, long monitorId, MonitorUpdateRequest request) {
    monitorsService.update(structureId, monitorId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteMonitor(long structureId, long monitorId) {
    monitorsService.delete(structureId, monitorId);
    return ResponseEntity.noContent().build();
  }
}
