package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.RoadApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.RoadSectionService;
import ca.bc.gov.nrs.cbr.struct.v1.HeaderSort;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSortColumn;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The road-section lookup behind "Forest Service Road". Mappings and authorization are declared on
 * {@link RoadApiEndpoint}.
 */
@RestController
public class RoadApiController implements RoadApiEndpoint {

  private final RoadSectionService roadSectionService;

  public RoadApiController(RoadSectionService roadSectionService) {
    this.roadSectionService = roadSectionService;
  }

  @Override
  public ResponseEntity<PagedResponse<RoadSearchResult>> searchRoads(RoadSearchCriteria criteria,
      int pageNumber, int pageSize, List<RoadSortColumn> sortBy,
      List<Sort.Direction> sortDirection) {
    return ResponseEntity.ok(
        roadSectionService.search(
            criteria, pageNumber, pageSize, HeaderSort.of(sortBy, sortDirection)));
  }

  @Override
  public ResponseEntity<RoadSectionResponse> getRoadSection(
      String forestFileId, String roadSectionId) {
    return ResponseEntity.ok(roadSectionService.find(forestFileId, roadSectionId));
  }
}
