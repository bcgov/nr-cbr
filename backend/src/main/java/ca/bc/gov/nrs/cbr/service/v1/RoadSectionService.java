package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.client.LrmopsApiClient;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadPage;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadSection;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadSort;
import ca.bc.gov.nrs.cbr.exception.RoadLookupUnavailableException;
import ca.bc.gov.nrs.cbr.exception.RoadSectionNotFoundException;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.HeaderSort;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionResponse.RoadDistrict;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSortColumn;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

/**
 * Roads for the site form, from LRMOPS through the LRM Ops API ({@link LrmopsApiClient}).
 *
 * <p>Three callers: the Road Search dialog ({@link #search}), the Forest Service Road lookup as
 * Project File ID# and Br. are typed ({@link #find}), and the site save's road check
 * ({@link #check}). The tenure type dropdown reads {@link #tenureTypes}.
 *
 * <p><b>When the API cannot answer</b> — down, timing out, refusing CBR's token, or not configured
 * in this environment — the lookups throw {@link RoadLookupUnavailableException} (503), which the
 * form shows as "Road lookup is unavailable right now.", and {@link #check} answers
 * {@link RoadCheck#UNAVAILABLE}, which refuses the save. Never mistaken for "no such road".
 *
 * <p>No database access: CBR's own road copies ({@code CBR_ROAD_SECTION_VW},
 * {@code CBR_ROAD_SEGMENT_VW}) are no longer read here. The view still supplies the road name
 * shown on the search and report screens, through {@code CrossingSiteEntity}.
 */
@Service
public class RoadSectionService {

  private static final Logger log = LoggerFactory.getLogger(RoadSectionService.class);

  /** The one site type with no road. */
  private static final String RECREATION_SITE = "REC";

  /** How LRMOPS marks a retired road's tenure type, e.g. {@code RP-RETIRE}. */
  private static final String RETIRED_SUFFIX = "-RETIRE";

  /**
   * The Tenure Type dropdown's order (business, 2026-11-23): each type followed by its retired
   * variant. A code LRMOPS adds later goes after these, alphabetically.
   */
  static final List<String> TENURE_TYPE_ORDER = List.of(
      "FSR", "FSR-RETIRE", "RP", "RP-RETIRE", "SUP", "SUP-RETIRE", "NSR", "CP", "OTH");

  private final LrmopsApiClient lrmops;

  public RoadSectionService(LrmopsApiClient lrmops) {
    this.lrmops = lrmops;
  }

  /**
   * Roads matching whatever the dialog was given, sorted by the columns the user chose — road
   * name, Project File ID# and Br. breaking their ties (the API's own order).
   *
   * <p>Paging and the page size bound are the API's: zero-based, at most 200 a page.
   *
   * @throws RoadLookupUnavailableException if the API cannot answer
   */
  public PagedResponse<RoadSearchResult> search(RoadSearchCriteria criteria, int pageNumber,
      int pageSize, List<HeaderSort<RoadSortColumn>> sort) {
    RoadPage page = call(() -> lrmops.searchRoads(
        new LrmopsApiClient.RoadSearchCriteria(
            criteria.forestServiceRoad(),
            criteria.forestFileId(),
            criteria.roadSectionId(),
            criteria.tenureType(),
            criteria.clientName()),
        Math.max(pageNumber, 0),
        pageSize,
        sort.stream()
            .map(header -> new RoadSort(column(header.column()), header.direction().name()))
            .toList()));

    return new PagedResponse<>(
        page.content().stream()
            .map(road -> new RoadSearchResult(
                road.roadName(),
                road.roadId(),
                road.sectionId(),
                storedTenureType(road.tenureType(), road.retired()),
                road.clientName()))
            .toList(),
        page.totalElements(),
        page.totalPages(),
        page.pageNumber(),
        page.pageSize());
  }

  /**
   * The tenure type as LRMOPS stores it — {@code RP-RETIRE} for a retired road permit — so the
   * results column reads as the Tenure Type dropdown, which lists the retired variants to filter
   * on. The API returns the base type and a {@code retired} flag.
   */
  private static String storedTenureType(String tenureType, boolean retired) {
    return tenureType != null && retired ? tenureType + RETIRED_SUFFIX : tenureType;
  }

  /** The API's name for a dialog column. */
  private static String column(RoadSortColumn column) {
    return switch (column) {
      case FOREST_SERVICE_ROAD -> "ROAD_NAME";
      case PROJECT_FILE -> "ROAD_ID";
      case BRANCH -> "SECTION_ID";
      case TENURE_TYPE -> "TENURE_TYPE";
      case CLIENT_NAME -> "CLIENT_NAME";
    };
  }

  /**
   * One road section, by the pair a site records, with the districts it lies in.
   *
   * <p>Retired roads are answered like any other (business, 2026-11-23). A section with no road
   * segment is a road too: LRMOPS has no segments.
   *
   * @throws RoadSectionNotFoundException if either half is blank or LRMOPS has no such section —
   *     the ordinary answer while the user is still typing
   * @throws RoadLookupUnavailableException if the API cannot answer
   */
  public RoadSectionResponse find(String forestFileId, String roadSectionId) {
    if (!StringUtils.hasText(forestFileId) || !StringUtils.hasText(roadSectionId)) {
      throw new RoadSectionNotFoundException(forestFileId, roadSectionId);
    }
    RoadSection section = lookUp(forestFileId, roadSectionId)
        .orElseThrow(() -> new RoadSectionNotFoundException(forestFileId, roadSectionId));

    return new RoadSectionResponse(
        section.roadId(),
        section.sectionId(),
        section.roadName(),
        storedTenureType(section.tenureType(), section.retired()),
        section.clientName(),
        section.districts().stream()
            .map(district -> new RoadDistrict(
                district.orgUnitNo(), district.code(), district.lengthMetres()))
            .toList());
  }

  /**
   * The save's road check: whether LRMOPS has the section a site names.
   *
   * <p>{@link RoadCheck#NOT_APPLICABLE} for a recreation site, whose Project File ID# names a
   * recreation project, and when either half is blank (already reported as required). Never
   * throws for the API being unavailable: that is {@link RoadCheck#UNAVAILABLE}, and the
   * validator refuses the save with a message beside Project File ID#.
   */
  public RoadCheck check(String siteTypeCode, String forestFileId, String roadSectionId) {
    if (RECREATION_SITE.equals(siteTypeCode == null ? null : siteTypeCode.trim())
        || !StringUtils.hasText(forestFileId) || !StringUtils.hasText(roadSectionId)) {
      return RoadCheck.NOT_APPLICABLE;
    }
    try {
      return lookUp(forestFileId, roadSectionId).isPresent()
          ? RoadCheck.FOUND
          : RoadCheck.NOT_FOUND;
    } catch (RoadLookupUnavailableException unavailable) {
      return RoadCheck.UNAVAILABLE;
    }
  }

  /**
   * LRMOPS's road tenure types, retired variants included, for the Road Search dialog's Tenure
   * Type dropdown — in {@link #TENURE_TYPE_ORDER}. A code with no letter or digit (LRMOPS's
   * {@code !}, "Geometry only") is left out, as the API already does.
   *
   * @throws RoadLookupUnavailableException if the API cannot answer
   */
  public List<CodeOptionResponse> tenureTypes() {
    return call(lrmops::getRoadTenureTypes).stream()
        .filter(type -> type.code() != null
            && type.code().chars().anyMatch(Character::isLetterOrDigit))
        .map(type -> new CodeOptionResponse(type.code().trim(), type.description()))
        .sorted(Comparator
            .comparingInt((CodeOptionResponse type) -> rank(type.code()))
            .thenComparing(CodeOptionResponse::code))
        .toList();
  }

  /** A code's place in {@link #TENURE_TYPE_ORDER}; after all of them when it is not listed. */
  private static int rank(String code) {
    int index = TENURE_TYPE_ORDER.indexOf(code.toUpperCase(Locale.ROOT));
    return index < 0 ? TENURE_TYPE_ORDER.size() : index;
  }

  private Optional<RoadSection> lookUp(String forestFileId, String roadSectionId) {
    return call(() -> lrmops.getRoadSection(forestFileId, roadSectionId));
  }

  /**
   * One API call, with every way it can fail to answer turned into
   * {@link RoadLookupUnavailableException}. Not configured counts: the call would go out without a
   * token, or nowhere.
   */
  private <T> T call(Supplier<T> request) {
    if (!lrmops.isConfigured()) {
      throw new RoadLookupUnavailableException(null);
    }
    try {
      return request.get();
    } catch (RestClientException ex) {
      log.warn("LRM Ops API call failed: {}", ex.getMessage());
      throw new RoadLookupUnavailableException(ex);
    }
  }
}
