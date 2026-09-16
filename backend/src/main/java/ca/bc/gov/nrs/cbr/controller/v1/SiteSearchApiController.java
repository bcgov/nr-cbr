package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.SiteSearchApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.SiteSearchService;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for Site Search. Mappings and authorization are declared on
 * {@link SiteSearchApiEndpoint}.
 */
@RestController
public class SiteSearchApiController implements SiteSearchApiEndpoint {

  private final SiteSearchService siteSearchService;

  public SiteSearchApiController(SiteSearchService siteSearchService) {
    this.siteSearchService = siteSearchService;
  }

  @Override
  public ResponseEntity<PagedResponse<SiteSearchResult>> searchSites(
      SiteSearchCriteria criteria, int pageNumber, int pageSize) {
    return ResponseEntity.ok(siteSearchService.search(criteria, pageNumber, pageSize));
  }
}
