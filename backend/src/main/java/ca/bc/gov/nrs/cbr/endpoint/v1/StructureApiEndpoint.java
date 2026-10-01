package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for structures. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.StructureApiController}.
 */
@RequestMapping("/api/v1/structures")
public interface StructureApiEndpoint {

  /**
   * Server-side paginated structure search — one page plus the true total.
   *
   * <p>Gated on {@link CbrAuthorities#READ} — legacy's {@code /showStructureSearch}, which sits at
   * the GENERAL floor with the rest of the {@code /show*} privileges. An admission check, not a row
   * filter: CBR has no scoping, so a caller who passes it sees every structure in the province.
   *
   * <p>{@code @Valid} turns a kilometre or a year that is not one into a 400 naming the field.
   *
   * <p><b>Sorted by a results column</b> when {@code sortBy} names one
   * ({@link StructureSortColumn}), {@code sortDirection} {@code ASC} or {@code DESC}; legacy's fixed
   * order otherwise, which also breaks a chosen column's ties.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/search")
  ResponseEntity<PagedResponse<StructureSearchResult>> searchStructures(
      @Valid @ModelAttribute StructureSearchCriteria criteria,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "sortBy", required = false) StructureSortColumn sortBy,
      @RequestParam(name = "sortDirection", defaultValue = "ASC") Sort.Direction sortDirection);
}
