package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.StructureApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.StructureSearchService;
import ca.bc.gov.nrs.cbr.service.v1.StructureService;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Structures. Mappings and authorization are declared on {@link StructureApiEndpoint}. */
@RestController
public class StructureApiController implements StructureApiEndpoint {

  private final StructureSearchService structureSearchService;
  private final StructureService structureService;

  public StructureApiController(
      StructureSearchService structureSearchService, StructureService structureService) {
    this.structureSearchService = structureSearchService;
    this.structureService = structureService;
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
}
