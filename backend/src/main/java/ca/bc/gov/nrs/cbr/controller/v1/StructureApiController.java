package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.StructureApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.StructureBridgeService;
import ca.bc.gov.nrs.cbr.service.v1.StructureCommentsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureCulvertService;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailService;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureDocumentsService.DocumentFile;
import ca.bc.gov.nrs.cbr.service.v1.StructureDocumentsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInformationService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInspectionsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureLoadRatingsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureMonitorsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureRepairsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureSearchService;
import ca.bc.gov.nrs.cbr.service.v1.StructureCreateService;
import ca.bc.gov.nrs.cbr.service.v1.StructureReplacementsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureService;
import ca.bc.gov.nrs.cbr.service.v1.StructureSpansAndPiersService;
import ca.bc.gov.nrs.cbr.struct.v1.BridgeDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.CloseProximityInspectionRequest;
import ca.bc.gov.nrs.cbr.struct.v1.CommentRequest;
import ca.bc.gov.nrs.cbr.struct.v1.CreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.CulvertDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.DocumentDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.DocumentUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionScheduleRequest;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.PierRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairTypeOption;
import ca.bc.gov.nrs.cbr.struct.v1.RepairUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.SitePreviewResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SpanRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureNameSuggestion;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDocumentsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInformationRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionScheduleResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
  private final StructureCommentsService commentsService;
  private final StructureInformationService informationService;
  private final StructureDetailsService detailsService;
  private final StructureBridgeService bridgeService;
  private final StructureLoadRatingsService loadRatingsService;
  private final StructureCulvertService culvertService;
  private final StructureReplacementsService replacementsService;
  private final StructureCreateService createService;

  public StructureApiController(
      StructureSearchService structureSearchService,
      StructureService structureService,
      StructureDetailService structureDetailService,
      StructureSpansAndPiersService spansAndPiersService,
      StructureDocumentsService documentsService,
      StructureInspectionsService inspectionsService,
      StructureRepairsService repairsService,
      StructureMonitorsService monitorsService,
      StructureCommentsService commentsService,
      StructureInformationService informationService,
      StructureDetailsService detailsService,
      StructureBridgeService bridgeService,
      StructureLoadRatingsService loadRatingsService,
      StructureCulvertService culvertService,
      StructureReplacementsService replacementsService,
      StructureCreateService createService) {
    this.structureSearchService = structureSearchService;
    this.structureService = structureService;
    this.structureDetailService = structureDetailService;
    this.spansAndPiersService = spansAndPiersService;
    this.documentsService = documentsService;
    this.inspectionsService = inspectionsService;
    this.repairsService = repairsService;
    this.monitorsService = monitorsService;
    this.commentsService = commentsService;
    this.informationService = informationService;
    this.detailsService = detailsService;
    this.bridgeService = bridgeService;
    this.loadRatingsService = loadRatingsService;
    this.culvertService = culvertService;
    this.replacementsService = replacementsService;
    this.createService = createService;
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
  public ResponseEntity<CreatedResponse> createStructure(StructureCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(createService.create(request));
  }

  @Override
  public SitePreviewResponse previewSite(String siteId) {
    return informationService.preview(siteId);
  }


  @Override
  public ResponseEntity<Void> updateInformation(
      long structureId, StructureInformationRequest request) {
    informationService.update(structureId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> updateStructureDetails(
      long structureId, StructureDetailsRequest request) {
    detailsService.updateStructure(structureId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> updateBridgeDetails(long structureId, BridgeDetailsRequest request) {
    bridgeService.update(structureId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> updateLoadRatingDetails(
      long structureId, LoadRatingDetailsRequest request) {
    loadRatingsService.updateDetails(structureId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<CreatedResponse> createLoadRating(
      long structureId, LoadRatingRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new CreatedResponse(loadRatingsService.add(structureId, request)));
  }

  @Override
  public ResponseEntity<Void> updateLoadRating(
      long structureId, long loadRatingId, LoadRatingRequest request) {
    loadRatingsService.update(structureId, loadRatingId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteLoadRating(long structureId, long loadRatingId) {
    loadRatingsService.delete(structureId, loadRatingId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public List<StructureNameSuggestion> suggestNames(String term) {
    return replacementsService.suggestNames(term);
  }

  @Override
  public ResponseEntity<Void> createReplacement(long structureId, ReplacementRequest request) {
    replacementsService.add(structureId, request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @Override
  public ResponseEntity<Void> deleteReplacement(long structureId, long replacedId) {
    replacementsService.delete(structureId, replacedId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> updateReplacementDetails(
      long structureId, ReplacementDetailsRequest request) {
    detailsService.updateReplacement(structureId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> updateCulvertDetails(long structureId, CulvertDetailsRequest request) {
    culvertService.update(structureId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<CreatedResponse> createSpan(long structureId, SpanRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new CreatedResponse(spansAndPiersService.createSpan(structureId, request)));
  }

  @Override
  public ResponseEntity<Void> updateSpan(long structureId, long spanId, SpanRequest request) {
    spansAndPiersService.updateSpan(structureId, spanId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteSpan(long structureId, long spanId) {
    spansAndPiersService.deleteSpan(structureId, spanId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<CreatedResponse> createPier(long structureId, PierRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new CreatedResponse(spansAndPiersService.createPier(structureId, request)));
  }

  @Override
  public ResponseEntity<Void> updatePier(long structureId, long pierId, PierRequest request) {
    spansAndPiersService.updatePier(structureId, pierId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deletePier(long structureId, long pierId) {
    spansAndPiersService.deletePier(structureId, pierId);
    return ResponseEntity.noContent().build();
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
  public ResponseEntity<CreatedResponse> addDocument(
      long structureId, MultipartFile file, DocumentDetailsRequest details) {
    return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedResponse(
        documentsService.upload(structureId, file, details)));
  }

  @Override
  public ResponseEntity<Void> updateDocument(
      long structureId, long fileId, DocumentUpdateRequest request) {
    documentsService.update(structureId, fileId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteDocument(long structureId, long fileId) {
    documentsService.delete(structureId, fileId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<CreatedResponse> addCloseProximityInspection(
      long structureId, CloseProximityInspectionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedResponse(
        inspectionsService.addCloseProximityInspection(structureId, request)));
  }

  @Override
  public ResponseEntity<CreatedResponse> addComment(long structureId, CommentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(commentsService.addComment(structureId, request));
  }

  @Override
  public ResponseEntity<CreatedResponse> addPlannedInspectionComment(
      long structureId, CommentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(commentsService.addPlannedInspectionComment(structureId, request));
  }

  @Override
  public ResponseEntity<Void> updateComment(
      long structureId, long commentId, CommentRequest request) {
    commentsService.update(structureId, commentId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> updateInspectionSchedule(
      long structureId, InspectionScheduleRequest request) {
    inspectionsService.updateSchedule(structureId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public StructureInspectionsResponse getInspections(
      long structureId, int pageNumber, int pageSize, boolean includeBeforeInstall) {
    return inspectionsService.inspections(
        structureId, pageNumber, pageSize, includeBeforeInstall);
  }

  @Override
  public StructureRepairsResponse.Listing getRepairs(long structureId,
      StructureRepairsResponse.View view, Long inspectionId, int pageNumber, int pageSize,
      boolean includeBeforeInstall) {
    return repairsService.repairs(
        structureId, view, inspectionId, pageNumber, pageSize, includeBeforeInstall);
  }

  @Override
  public List<RepairTypeOption> getRepairTypes(long structureId) {
    return repairsService.repairTypes(structureId);
  }

  @Override
  public ResponseEntity<RepairCreatedResponse> createRepair(
      long structureId, RepairCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(repairsService.create(structureId, request));
  }

  @Override
  public ResponseEntity<Void> updateRepair(
      long structureId, long repairId, RepairUpdateRequest request) {
    repairsService.update(structureId, repairId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteRepair(long structureId, long repairId) {
    repairsService.delete(structureId, repairId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public StructureMonitorsResponse.Listing getMonitors(long structureId,
      StructureMonitorsResponse.View view, Long inspectionId, int pageNumber, int pageSize,
      boolean includeBeforeInstall) {
    return monitorsService.monitors(
        structureId, view, inspectionId, pageNumber, pageSize, includeBeforeInstall);
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
