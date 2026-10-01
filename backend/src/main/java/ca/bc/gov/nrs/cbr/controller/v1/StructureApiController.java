package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.StructureApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.StructureSearchService;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
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

  public StructureApiController(StructureSearchService structureSearchService) {
    this.structureSearchService = structureSearchService;
  }

  @Override
  public ResponseEntity<PagedResponse<StructureSearchResult>> searchStructures(
      StructureSearchCriteria criteria, int pageNumber, int pageSize,
      StructureSortColumn sortBy, Sort.Direction sortDirection) {
    return ResponseEntity.ok(
        structureSearchService.search(criteria, pageNumber, pageSize, sortBy, sortDirection));
  }
}
