package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for Site Search. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.SiteSearchApiController}.
 *
 * <p>The mappings and the authorization live here rather than on the controller, following nr-frep:
 * the interface is the contract, the controller is one way of satisfying it. It also means the
 * {@code @PreAuthorize} cannot be dropped by someone editing the implementation, because removing
 * it from the interface changes the published contract.
 */
@RequestMapping("/api/v1/sites")
public interface SiteSearchApiEndpoint {

  /**
   * Server-side paginated site search — one page plus the true total.
   *
   * <p>Paginated on the server because the legacy screen is: {@code FIND_SITES_BY_CRITERIA} has a
   * paged overload taking page and page size, and {@code COUNT_SITES_BY_CRITERIA} supplies the
   * total separately. Returning everything and paging in the browser would work on a district and
   * not on the province.
   *
   * <p>{@code @Valid} is what turns a non-numeric kilometre bound into a 400 naming the field,
   * rather than a value that reaches the {@code WHERE} clause and matches nothing.
   *
   * <p>Gated on {@link CbrAuthorities#READ}, the legacy {@code /showSiteSearch} privilege — every
   * role that can read holds it, and `CBR_ADMIN` now does too. This is an admission check, not a
   * row filter: CBR has no region, district or client scoping, so a caller who passes it sees every
   * site in the province. There is no narrower gate to apply.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/search")
  ResponseEntity<PagedResponse<SiteSearchResult>> searchSites(
      @Valid @ModelAttribute SiteSearchCriteria criteria,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize);
}
