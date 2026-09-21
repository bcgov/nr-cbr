package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for structure inspections. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.InspectionApiController}.
 *
 * <p>The mappings and the authorization live here rather than on the controller, following nr-frep
 * and {@link SiteApiEndpoint}: the interface is the contract, the controller is one way of
 * satisfying it. It also means the {@code @PreAuthorize} cannot be dropped by someone editing the
 * implementation, because removing it from the interface changes the published contract.
 *
 * <p><b>One operation today, and the next two are not like it.</b> Searching is READ; deleting an
 * offline inspection is {@code /deleteInspection}, one of the nine deletes the destructive
 * capability covers; and moving an inspection to {@code RVD} is {@code /approveInspection}, which is
 * a third gate again (cbr-inspection-reviewer.local.md §4). Whoever adds those should not assume the
 * gate on this one.
 */
@RequestMapping("/api/v1/inspections")
public interface InspectionApiEndpoint {

  /**
   * Server-side paginated inspection search — one page plus the true total.
   *
   * <p>Paginated on the server because the legacy screen is: {@code FIND_INSPECTIONS_BY_CRITERIA}
   * takes a page and a page size, and an unfiltered search would otherwise return every inspection
   * ever recorded.
   *
   * <p><b>A request with no criteria returns everything, a page at a time.</b> Legacy refuses it
   * outright, for a reason that no longer applies once the query is paged — see
   * {@link ca.bc.gov.nrs.cbr.service.v1.InspectionSearchService#search}. {@code /api/v1/sites/search}
   * behaves the same way.
   *
   * <p>{@code @Valid} is what turns a misformatted month into a 400 naming the field, rather than a
   * value that silently drops out of the {@code WHERE} clause and widens the search.
   *
   * <p>Gated on {@link CbrAuthorities#READ}, the legacy {@code /showInspectionSearch} privilege —
   * every role that can read holds it. This is an admission check, not a row filter: CBR has no
   * region, district or client scoping, so a caller who passes it can search every inspection in the
   * province. There is no narrower gate to apply (cbr-auth-and-roles.local.md §3.3).
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/search")
  ResponseEntity<PagedResponse<InspectionSearchResult>> searchInspections(
      @Valid @ModelAttribute InspectionSearchCriteria criteria,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize);
}
