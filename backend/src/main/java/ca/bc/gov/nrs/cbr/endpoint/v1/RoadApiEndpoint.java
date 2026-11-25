package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSortColumn;
import java.util.List;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the road-section lookup, answered from LRMOPS through the LRM Ops API.
 * Implemented by {@link ca.bc.gov.nrs.cbr.controller.v1.RoadApiController}.
 *
 * <p><b>503 when the API cannot answer</b> (down, timing out, refusing CBR's token, or not
 * configured): the form says "Road lookup is unavailable right now." beside the field, rather than
 * treating it as "no such road".
 *
 * <p>Separate from {@link ConfigurationApiEndpoint} for the same reason {@link ClientApiEndpoint}
 * is: everything there is a bounded code table fetched once and cached for a session, where this
 * is parameterised and asked again whenever the pair it is keyed on changes.
 */
@RequestMapping("/api/v1/roads")
public interface RoadApiEndpoint {

  /**
   * Roads matching the lookup dialog's criteria.
   *
   * <p>Gated on {@link CbrAuthorities#READ} — legacy's {@code /showRoadSearch}, which sits at the
   * GENERAL floor with the rest of the {@code /show*} privileges.
   *
   * <p>Always 200, with one page of the roads that matched and the true total. An empty page is an
   * answer: legacy's dialog prints "No roads found." rather than treating it as a failure.
   *
   * <p><b>Server-side paging, as Site Search and Inspection Search have.</b> Legacy caps the dialog
   * at two hundred roads and says so on screen when it fills; paging instead puts every match in
   * reach and lets the dialog say how many there are. Retired roads are listed like any other.
   *
   * <p><b>Sorted by a column</b> when {@code sortBy} names one ({@link RoadSortColumn}),
   * {@code sortDirection} {@code ASC} or {@code DESC}; road name, file, branch otherwise, which also
   * breaks ties. On the server, so it orders every match rather than the page on screen. An unknown
   * value is a 400.
   *
   * <p><b>By several columns</b> when {@code sortBy} repeats, each with its {@code sortDirection}
   * in the same order ({@code HeaderSort}): the first decides, and each next breaks the ties before
   * it.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/search")
  ResponseEntity<PagedResponse<RoadSearchResult>> searchRoads(
      @ModelAttribute RoadSearchCriteria criteria,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "15") int pageSize,
      @RequestParam(name = "sortBy", required = false) List<RoadSortColumn> sortBy,
      @RequestParam(name = "sortDirection", required = false)
      List<Sort.Direction> sortDirection);

  /**
   * The road section named by a Project File ID# and a Br.
   *
   * <p>Gated on {@link CbrAuthorities#READ}: it fills in a field on screens that are themselves
   * READ-gated, so a token holder with no CBR role has nothing to do with it.
   *
   * <p><b>Query parameters, not a path.</b> {@code ROAD_SECTION_ID} is thirty characters of free
   * text — a path segment would have to be encoded and decoded correctly by every caller for a
   * value that may contain a slash.
   *
   * <p>404 when LRMOPS has no such section — the ordinary answer while the user is still typing
   * either half. Add Site reports it on Project File ID# once both halves are in, as legacy does,
   * rather than as an error of the request.
   *
   * <p>{@code districts} lists every Forest District the section lies in: one, which the form fills
   * in; two or more, and the user picks one of them (business, 2026-11-23).
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping
  ResponseEntity<RoadSectionResponse> getRoadSection(
      @RequestParam(name = "forestFileId", defaultValue = "") String forestFileId,
      @RequestParam(name = "roadSectionId", defaultValue = "") String roadSectionId);

  /**
   * LRMOPS's road tenure types ({@code FSR}, {@code RP}, …) with their descriptions, for the Road
   * Search dialog's Tenure Type dropdown.
   *
   * <p>Gated on {@link CbrAuthorities#READ}, as the search it filters.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/tenure-types")
  ResponseEntity<List<CodeOptionResponse>> getTenureTypes();

  /**
   * Road names containing the term, those starting with it first, at most twenty: the Forest
   * Service Road pick list on Site, Structure and Inspection Search, which filter on the name
   * picked, exactly.
   *
   * <p>Gated on {@link CbrAuthorities#READ}, as the searches it serves. Empty for a term under three
   * characters; 503 when LRMOPS cannot be reached.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/names")
  ResponseEntity<List<String>> getRoadNames(
      @RequestParam(name = "search", defaultValue = "") String search);
}
