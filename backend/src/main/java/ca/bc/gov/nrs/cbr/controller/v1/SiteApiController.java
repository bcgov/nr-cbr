package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.SiteApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.SiteSearchService;
import ca.bc.gov.nrs.cbr.service.v1.SiteService;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Crossing sites. Mappings and authorization are declared on {@link SiteApiEndpoint}.
 *
 * <p>Two services behind one controller, and they stay separate: searching is a read with seventeen
 * optional criteria and a paging envelope, deleting is a guarded write. One class doing both would
 * be two unrelated halves sharing only a table.
 */
@RestController
public class SiteApiController implements SiteApiEndpoint {

  private final SiteSearchService siteSearchService;
  private final SiteService siteService;

  public SiteApiController(SiteSearchService siteSearchService, SiteService siteService) {
    this.siteSearchService = siteSearchService;
    this.siteService = siteService;
  }

  @Override
  public ResponseEntity<PagedResponse<SiteSearchResult>> searchSites(
      SiteSearchCriteria criteria, int pageNumber, int pageSize) {
    return ResponseEntity.ok(siteSearchService.search(criteria, pageNumber, pageSize));
  }

  @Override
  public ResponseEntity<Void> deleteSite(String siteId) {
    siteService.delete(siteId);
    return ResponseEntity.noContent().build();
  }
}
