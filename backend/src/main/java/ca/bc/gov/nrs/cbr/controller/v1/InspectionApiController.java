package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.InspectionApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.InspectionCreateService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionDetailService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionFollowUpService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionFormService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionSearchService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionService;
import ca.bc.gov.nrs.cbr.struct.v1.CreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSortColumn;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Structure inspections. Mappings and authorization are declared on {@link InspectionApiEndpoint};
 * the legacy equivalent is itemized in
 * {@link ca.bc.gov.nrs.cbr.specification.v1.InspectionSearchSpecifications}.
 *
 * <p>Two services behind one controller, and they stay separate — the same split
 * {@code SiteApiController} makes: searching is a read with nineteen optional criteria and a paging
 * envelope, deleting is a guarded write with a cascade behind it.
 */
@RestController
public class InspectionApiController implements InspectionApiEndpoint {

  private final InspectionSearchService inspectionSearchService;
  private final InspectionService inspectionService;
  private final InspectionFormService formService;
  private final InspectionCreateService createService;
  private final InspectionDetailService detailService;
  private final InspectionFollowUpService followUpService;

  public InspectionApiController(
      InspectionSearchService inspectionSearchService, InspectionService inspectionService,
      InspectionFormService formService, InspectionCreateService createService,
      InspectionDetailService detailService, InspectionFollowUpService followUpService) {
    this.inspectionSearchService = inspectionSearchService;
    this.inspectionService = inspectionService;
    this.formService = formService;
    this.createService = createService;
    this.detailService = detailService;
    this.followUpService = followUpService;
  }

  @Override
  public ResponseEntity<PagedResponse<InspectionSearchResult>> searchInspections(
      InspectionSearchCriteria criteria, int pageNumber, int pageSize,
      InspectionSortColumn sortBy, Sort.Direction sortDirection) {
    return ResponseEntity.ok(
        inspectionSearchService.search(criteria, pageNumber, pageSize, sortBy, sortDirection));
  }

  @Override
  public ResponseEntity<Void> deleteInspection(Long inspectionId) {
    inspectionService.delete(inspectionId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public InspectionFormResponse inspectionForm(long structureId) {
    return formService.form(structureId);
  }

  @Override
  public ResponseEntity<CreatedResponse> createInspection(InspectionCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(createService.create(request));
  }

  @Override
  public InspectionDetailResponse getInspection(long inspectionId) {
    return detailService.findById(inspectionId);
  }

  @Override
  public ResponseEntity<RepairCreatedResponse> addRepair(long inspectionId,
      RepairCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(followUpService.addRepair(inspectionId, request));
  }

  @Override
  public ResponseEntity<Void> updateRepair(long inspectionId, long repairId,
      RepairUpdateRequest request) {
    followUpService.updateRepair(inspectionId, repairId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteRepair(long inspectionId, long repairId) {
    followUpService.deleteRepair(inspectionId, repairId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<MonitorCreatedResponse> addMonitor(long inspectionId,
      MonitorCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(followUpService.addMonitor(inspectionId, request));
  }

  @Override
  public ResponseEntity<Void> updateMonitor(long inspectionId, long monitorId,
      MonitorUpdateRequest request) {
    followUpService.updateMonitor(inspectionId, monitorId, request);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> deleteMonitor(long inspectionId, long monitorId) {
    followUpService.deleteMonitor(inspectionId, monitorId);
    return ResponseEntity.noContent().build();
  }
}
