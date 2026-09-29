package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.RoadSectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CbrRoadSectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CbrRoadSegmentRepository;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionResponse;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The road section behind "Forest Service Road" on the site form.
 *
 * <p>Replaces the {@code showSite.do?actionMapping=road} call {@code site.jsp} makes whenever
 * Project File ID# or Br. changes. <b>Narrower than that call</b>, which also returns the road's
 * responsibility and the org unit it sits in — and overwrites the form's Forest District with the
 * latter before disabling it. Only the name is answered here; making the district derived is a
 * change to what the form means, not to what it displays.
 */
@Service
public class RoadSectionService {

  /**
   * Guards against a caller asking for the whole view in one page — the same bound Site Search
   * puts on its pages.
   *
   * <p>Legacy caps the dialog itself at two hundred roads ({@code ROWNUM <= 200}, and "Search
   * returned 200 or more records, 200 shown" when it fills). CBR pages instead, so every match is
   * reachable and the total is the true one; the bound here is on one page, not on the answer.
   */
  private static final int MAX_PAGE_SIZE = 200;

  /** The dialog's own page size, for a caller that does not say. */
  private static final int DEFAULT_PAGE_SIZE = 15;

  private static final String WILDCARD = "%";

  private static final Logger log = LoggerFactory.getLogger(RoadSectionService.class);

  /**
   * Whether {@code FOREST_FILE_CLIENT} can be read, once it is known.
   *
   * <p><b>CBR is not granted that table.</b> Thirty-odd other application roles are, and legacy's
   * own road search joins it — CBR never asked, because legacy runs as a different database user.
   * Until the grant lands, the join that names the tenure holder fails with {@code ORA-00942} and
   * took the whole dialog with it: a search that works on four of its six criteria was answering
   * none of them.
   *
   * <p>Discovered rather than configured, and remembered for the life of the process. A flag would
   * have to be set correctly in every environment and unset again once the grant arrives; this
   * corrects itself on the next restart after it does, in either direction.
   */
  private volatile Boolean tenureHolderReadable;

  private final CbrRoadSectionRepository roadSections;

  private final CbrRoadSegmentRepository roadSegments;

  public RoadSectionService(
      CbrRoadSectionRepository roadSections, CbrRoadSegmentRepository roadSegments) {
    this.roadSections = roadSections;
    this.roadSegments = roadSegments;
  }

  /**
   * Roads matching whatever the dialog was given.
   *
   * <p>Every criterion is an unanchored, case-insensitive contains — the {@code Search.LIKE}
   * treatment CBR gives text criteria everywhere. A blank one is dropped rather than matched
   * against the empty string, so all six blank returns the first page of everything, which is what
   * legacy's dialog does when submitted empty.
   *
   * <p><b>Deliberately not {@code @Transactional}</b>, unlike everything else in this package, and
   * the fallback below is why. A failed statement marks its transaction rollback-only, so
   * recovering inside one buys nothing: the second query runs, and then the commit fails with
   * {@code UnexpectedRollbackException} — a less legible error than the one being handled. Without
   * an outer transaction each repository call gets its own, the failed one rolls back alone, and
   * the fallback starts clean.
   *
   * @param pageNumber zero-based
   * @param pageSize   rows per page, capped at {@value #MAX_PAGE_SIZE}
   */
  public PagedResponse<RoadSearchResult> search(
      RoadSearchCriteria criteria, int pageNumber, int pageSize) {
    Pageable limit = PageRequest.of(Math.max(pageNumber, 0), boundedPageSize(pageSize));

    if (Boolean.FALSE.equals(tenureHolderReadable)) {
      return withoutTenureHolder(criteria, limit);
    }

    try {
      Page<RoadSearchResult> roads = roadSections.search(
          contains(criteria.forestServiceRoad()),
          contains(criteria.forestFileId()),
          contains(criteria.roadSectionId()),
          contains(criteria.tenureType()),
          contains(criteria.clientName()),
          contains(criteria.clientNumber()),
          limit);
      tenureHolderReadable = true;
      return paged(roads);
    } catch (InvalidDataAccessResourceUsageException missingGrant) {
      // The only thing in that query this application is not granted. Caught here rather than
      // guarded by a probe on every search: once the grant lands this branch is never taken again,
      // and until it does it is taken once per process.
      tenureHolderReadable = false;
      log.warn("Road search cannot read FOREST_FILE_CLIENT, so roads will be listed without their "
          + "tenure holder. Grant SELECT on THE.FOREST_FILE_CLIENT to the application role to "
          + "restore the client name, the client number and the two criteria that match on them.",
          missingGrant);
      return withoutTenureHolder(criteria, limit);
    }
  }

  /**
   * The fallback on its own, so it can be exercised without provoking a real missing grant.
   *
   * <p>Package-private rather than public: it is the same answer {@link #search} gives once it has
   * learned the table cannot be read, and no caller should be choosing it.
   */
  PagedResponse<RoadSearchResult> searchWithoutTenureHolder(
      RoadSearchCriteria criteria, int pageNumber, int pageSize) {
    return withoutTenureHolder(
        criteria, PageRequest.of(Math.max(pageNumber, 0), boundedPageSize(pageSize)));
  }

  /**
   * The search with the client left out — and with the two client criteria left out too.
   *
   * <p>Dropping them rather than failing is the honest half of the trade: a criterion that cannot
   * be applied must not silently narrow nothing, so a user who typed a client name gets every road
   * rather than an empty list that reads as "no such road".
   */
  private PagedResponse<RoadSearchResult> withoutTenureHolder(
      RoadSearchCriteria criteria, Pageable limit) {
    return paged(roadSections.searchWithoutClient(
        contains(criteria.forestServiceRoad()),
        contains(criteria.forestFileId()),
        contains(criteria.roadSectionId()),
        contains(criteria.tenureType()),
        limit));
  }

  private static PagedResponse<RoadSearchResult> paged(Page<RoadSearchResult> page) {
    return new PagedResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }

  private static int boundedPageSize(int requested) {
    if (requested < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }

  /**
   * A criterion as the query wants it, or null when the user left it blank.
   *
   * <p>Null rather than {@code "%%"}: the query reads null as "no such criterion" and drops the
   * predicate entirely, where a wildcard pair would still exclude every row whose column is null —
   * a road with no name would vanish from a search that named no road.
   */
  private static String contains(String value) {
    return StringUtils.hasText(value)
        ? WILDCARD + value.trim().toUpperCase(Locale.ROOT) + WILDCARD
        : null;
  }

  /**
   * One road section, by the pair a site records — provided it has a road segment.
   *
   * <p><b>A section with no segment is no road here.</b> A site stores a segment, and the save
   * refuses a pair that resolves to none (see {@code SiteValidator.checkRoadResolves}). Legacy
   * behaves the same way before the save: it fills in the road name and the district only once
   * its hidden segment list has an entry ({@code site.jsp:341}), and otherwise blanks the name and
   * reports "Project File ID#/Br. is invalid." Answering with the section's name regardless showed a
   * road and a district for a pair the save would then refuse. Asked through the same method the
   * save uses, so the two cannot disagree about which pairs are roads.
   *
   * @throws RoadSectionNotFoundException if the view holds no such section, or the section has no
   *                                      segment — the ordinary answer while the user is still
   *                                      typing either half
   */
  @Transactional(readOnly = true)
  public RoadSectionResponse find(String forestFileId, String roadSectionId) {
    if (!StringUtils.hasText(forestFileId) || !StringUtils.hasText(roadSectionId)) {
      throw new RoadSectionNotFoundException(forestFileId, roadSectionId);
    }

    CbrRoadSectionEntity section = roadSections
        .findById(new CbrRoadSectionEntity.Key(forestFileId.trim(), roadSectionId.trim()))
        .orElseThrow(() -> new RoadSectionNotFoundException(forestFileId, roadSectionId));

    if (roadSegments
        .findFirstByForestFileIdAndRoadSectionIdOrderByRoadSegmentIdAsc(
            forestFileId.trim(), roadSectionId.trim())
        .isEmpty()) {
      throw new RoadSectionNotFoundException(forestFileId, roadSectionId);
    }

    return new RoadSectionResponse(
        section.getForestFileId(),
        section.getRoadSectionId(),
        section.getRoadSectName(),
        section.getForestRegion());
  }
}
