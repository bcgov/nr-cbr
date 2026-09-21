package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.InspectionApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.InspectionSearchService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
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

  public InspectionApiController(
      InspectionSearchService inspectionSearchService, InspectionService inspectionService) {
    this.inspectionSearchService = inspectionSearchService;
    this.inspectionService = inspectionService;
  }

  @Override
  public ResponseEntity<PagedResponse<InspectionSearchResult>> searchInspections(
      InspectionSearchCriteria criteria, int pageNumber, int pageSize) {
    return ResponseEntity.ok(inspectionSearchService.search(criteria, pageNumber, pageSize));
  }

  @Override
  public ResponseEntity<Void> deleteInspection(Long inspectionId) {
    inspectionService.delete(inspectionId);
    return ResponseEntity.noContent().build();
  }
}
