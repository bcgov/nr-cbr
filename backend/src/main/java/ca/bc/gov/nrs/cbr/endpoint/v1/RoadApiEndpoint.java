package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionResponse;
import java.util.List;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the road-section lookup. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.RoadApiController}.
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
   * <p>Always 200, with however many roads matched and at most two hundred of them. An empty list
   * is an answer: legacy's dialog prints "No roads found." rather than treating it as a failure.
   *
   * <p><b>No paging.</b> This fills a dialog the user picks one row from and closes, not a table
   * they work through — legacy caps at the same two hundred and says so on screen when it fills.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/search")
  ResponseEntity<List<RoadSearchResult>> searchRoads(@ModelAttribute RoadSearchCriteria criteria);

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
   * <p>404 when the view holds no such section, which is the ordinary answer while the user is
   * still typing either half — and the only answer in an environment provisioned from nr-mof-db,
   * where the materialized view is stubbed. The caller shows nothing rather than reporting it.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping
  ResponseEntity<RoadSectionResponse> getRoadSection(
      @RequestParam(name = "forestFileId", defaultValue = "") String forestFileId,
      @RequestParam(name = "roadSectionId", defaultValue = "") String roadSectionId);
}
