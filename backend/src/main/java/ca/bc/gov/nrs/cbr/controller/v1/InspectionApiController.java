package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.InspectionApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.InspectionSearchService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Structure inspections. Mappings and authorization are declared on {@link InspectionApiEndpoint};
 * the legacy equivalent is itemized in
 * {@link ca.bc.gov.nrs.cbr.specification.v1.InspectionSearchSpecifications}.
 */
@RestController
public class InspectionApiController implements InspectionApiEndpoint {

  private final InspectionSearchService inspectionSearchService;

  public InspectionApiController(InspectionSearchService inspectionSearchService) {
    this.inspectionSearchService = inspectionSearchService;
  }

  @Override
  public ResponseEntity<PagedResponse<InspectionSearchResult>> searchInspections(
      InspectionSearchCriteria criteria, int pageNumber, int pageSize) {
    return ResponseEntity.ok(inspectionSearchService.search(criteria, pageNumber, pageSize));
  }
}
