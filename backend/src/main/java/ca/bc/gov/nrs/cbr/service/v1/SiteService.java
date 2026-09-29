package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.SiteInUseException;
import ca.bc.gov.nrs.cbr.exception.SiteNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSegmentEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CbrRoadSegmentRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CloseProximityInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult;
import ca.bc.gov.nrs.cbr.struct.v1.SiteCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.SiteCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SiteDetailResponse;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations on a crossing site itself, as opposed to searching for one.
 *
 * <p>Creating, reading and deleting. Editing arrives with the Site Detail save.
 */
@Service
public class SiteService {

  private static final Logger log = LoggerFactory.getLogger(SiteService.class);

  private static final String ACTIVE = "Y";

  /** The one site type with no road, so no segment to derive. */
  private static final String RECREATION_SITE = "REC";
  private static final String ARCHIVED = "N";

  private final CrossingSiteRepository crossingSiteRepository;
  private final CrossingStructureRepository crossingStructureRepository;
  private final CloseProximityInspectionRepository closeProximityInspectionRepository;
  private final ClientLocationRepository clientLocations;
  private final CbrRoadSegmentRepository roadSegments;
  private final SiteValidator validator;
  private final LoggedUserHelper loggedUser;

  public SiteService(
      CrossingSiteRepository crossingSiteRepository,
      CrossingStructureRepository crossingStructureRepository,
      CloseProximityInspectionRepository closeProximityInspectionRepository,
      ClientLocationRepository clientLocations,
      CbrRoadSegmentRepository roadSegments,
      SiteValidator validator,
      LoggedUserHelper loggedUser) {
    this.crossingSiteRepository = crossingSiteRepository;
    this.crossingStructureRepository = crossingStructureRepository;
    this.closeProximityInspectionRepository = closeProximityInspectionRepository;
    this.clientLocations = clientLocations;
    this.roadSegments = roadSegments;
    this.validator = validator;
    this.loggedUser = loggedUser;
  }

  /**
   * Creates a site, or refuses with a message for every field at fault.
   *
   * <p>Replaces {@code addSite.do?actionMapping=save} and the {@code CBR_REGIONAL_ENGINEER
   * .INSERT_SITE} procedure behind it, which is a bare {@code INSERT} with no logic of its own — the
   * rules all live in {@code SiteForm.validate}, which is what {@link SiteValidator} ports.
   *
   * <p><b>The number is upper-cased here, not only in the browser.</b> {@code INSERT_SITE} writes
   * {@code UPPER(P_SITE_ID)}, so legacy's key is upper-case however it was typed and whatever
   * client sent it. A row stored in mixed case would be a site the search could not find and the
   * uniqueness check would not catch.
   *
   * <p><b>The four audit columns are set here too.</b> All four are {@code NOT NULL} and nothing on
   * the table populates them; legacy passes all four from {@code Site.save}. Entry and update are
   * the same user and the same instant on a create, which is what legacy writes.
   *
   * @return the number the site was stored under — upper-cased, whatever case was sent. Nothing
   *         else: the caller holds what it sent, and the screen it opens next reads the site
   *         for itself
   * @throws ca.bc.gov.nrs.cbr.exception.SiteValidationException if anything is wrong with it
   */
  @Transactional
  public SiteCreatedResponse create(SiteCreateRequest request) {
    String siteId = request.siteId() == null ? "" : request.siteId().trim().toUpperCase(Locale.ROOT);
    Long roadSegmentId = resolveRoadSegment(request);
    validator.validate(request, crossingSiteRepository.existsById(siteId), roadSegmentId);

    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now();

    CrossingSiteEntity site = CrossingSiteEntity.builder()
        .crossingSiteId(siteId)
        .crossingName(blankToNull(request.crossingName()))
        .pointOfCommencementDistance(request.pointOfCommencementDistance())
        .userKm(request.userKm())
        .crossingSiteStatusCode(blankToNull(request.crossingSiteStatusCode()))
        .structureInspectionStatusCode(blankToNull(request.structureInspectionStatusCode()))
        .crossingSiteTypeCode(blankToNull(request.crossingSiteTypeCode()))
        .specialAccessRqmtCode(blankToNull(request.specialAccessRqmtCode()))
        .orgUnitNo(request.orgUnitNo())
        .managementOrgUnitNo(request.managementOrgUnitNo())
        .businessAreaOrgUnitNo(request.businessAreaOrgUnitNo())
        .forestFileId(blankToNull(request.forestFileId()))
        .roadSectionId(blankToNull(request.roadSectionId()))
        .roadSegmentId(roadSegmentId)
        .clientNumber(blankToNull(request.clientNumber()))
        .clientLocnCode(blankToNull(request.clientLocnCode()))
        .capitalRoadInd(request.capitalRoad() ? ACTIVE : ARCHIVED)
        .longitude(request.longitude())
        .latitude(request.latitude())
        .utmZone(request.utmZone())
        .utmEasting(request.utmEasting())
        .utmNorthing(request.utmNorthing())
        .pointOfAccessDesc(blankToNull(request.pointOfAccessDescription()))
        .entryUserid(user)
        .entryTimestamp(now)
        .updateUserid(user)
        .updateTimestamp(now)
        .build();

    crossingSiteRepository.saveAndFlush(site);
    log.info("Created site {}", siteId);

    return new SiteCreatedResponse(siteId);
  }

  /**
   * The road segment a site on this section belongs to — derived, never sent.
   *
   * <p><b>Legacy does not ask the user either.</b> Its Road Segment control is a {@code <select>}
   * inside two {@code visibility:hidden} cells; the server fills it with the section's segments and
   * the browser posts back whichever the ordering put first. Deriving it here reaches the same
   * stored value without a field nobody can see travelling to the browser and back — and without a
   * client being able to name a segment that belongs to a different road.
   *
   * <p>Null for a recreation site, whose Project File ID# names a recreation project rather than a
   * road file, and null when either half of the pair is missing. {@link SiteValidator} turns the
   * remaining case — a road named but not found — into a message on Project File ID#.
   */
  private Long resolveRoadSegment(SiteCreateRequest request) {
    if (RECREATION_SITE.equals(blankToNull(request.crossingSiteTypeCode()))) {
      return null;
    }
    String forestFileId = blankToNull(request.forestFileId());
    String roadSectionId = blankToNull(request.roadSectionId());
    if (forestFileId == null || roadSectionId == null) {
      return null;
    }
    return roadSegments
        .findFirstByForestFileIdAndRoadSectionIdOrderByRoadSegmentIdAsc(forestFileId, roadSectionId)
        .map(CbrRoadSegmentEntity::getRoadSegmentId)
        .orElse(null);
  }

  /**
   * An empty box as {@code NULL}, not as the empty string.
   *
   * <p>Oracle treats the two as the same thing for a {@code VARCHAR2}, but the distinction matters
   * on the way in: a column left empty reads as "not recorded" everywhere else in this application,
   * and storing {@code ''} would make a blank Crossing Name a different value from an absent one to
   * every non-Oracle reader of the row.
   */
  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /**
   * One site, for the detail screen.
   *
   * <p>Legacy assembles the same page from three requests: {@code showSite.do} for the row, a road
   * lookup for the Forest Service Road, and a {@code getClientDetails()} AJAX call on load for the
   * maintainer's name. This answers all three at once — the screen cannot usefully render without
   * any of them, so three round trips only stagger the moment it becomes readable.
   *
   * @param siteId the {@code CROSSING_SITE_ID}
   * @throws SiteNotFoundException if no such site exists
   */
  @Transactional(readOnly = true)
  public SiteDetailResponse findById(String siteId) {
    CrossingSiteEntity site = crossingSiteRepository.findById(siteId)
        .orElseThrow(() -> new SiteNotFoundException(siteId));

    return new SiteDetailResponse(
        site.getCrossingSiteId(),
        site.getCrossingName(),
        site.getPointOfCommencementDistance(),
        site.getUserKm(),
        site.getCrossingSiteStatusCode(),
        site.getStructureInspectionStatusCode(),
        site.getCrossingSiteTypeCode(),
        site.getSpecialAccessRqmtCode(),
        site.getOrgUnitNo(),
        site.getManagementOrgUnitNo(),
        site.getBusinessAreaOrgUnitNo(),
        site.getForestFileId(),
        site.getRoadSectionId(),
        // Null whenever the road-section snapshot does not carry this pair — a stale mview, not an
        // error. The mapping reads a dangling reference as "no road section"; so does legacy.
        site.getRoadSection() == null ? null : site.getRoadSection().getRoadSectName(),
        crossingStructureRepository.countByCrossingSiteIdAndActiveInd(
            site.getCrossingSiteId(), ACTIVE),
        site.getClientNumber(),
        site.getClientLocnCode(),
        maintainerLabel(site),
        ACTIVE.equals(site.getCapitalRoadInd()),
        site.getLongitude(),
        site.getLatitude(),
        site.getUtmZone(),
        site.getUtmEasting(),
        site.getUtmNorthing(),
        site.getPointOfAccessDesc());
  }

  /**
   * The Designated Maintainer as one line, or null when the site names none.
   *
   * <p>Composed here rather than on the screen only because this is the one place that has the
   * client row; the search's own lookup composes its label in the browser for the same reason in
   * reverse. Null rather than an empty string when the pair resolves to nothing — a site may name
   * a client location that has since been removed, and an empty label is not the same answer as
   * "no maintainer recorded".
   */
  private String maintainerLabel(CrossingSiteEntity site) {
    if (site.getClientNumber() == null || site.getClientLocnCode() == null) {
      return null;
    }
    return clientLocations
        .findMaintainer(site.getClientNumber(), site.getClientLocnCode())
        .stream()
        .findFirst()
        .map(SiteService::describe)
        .orElse(null);
  }

  /** "CANFOR CORPORATION · Northern Division · Prince George · 00001012-01". */
  private static String describe(ClientLookupResult client) {
    String pair = client.clientNumber() + "-" + client.clientLocnCode();
    return Stream.of(client.clientName(), client.clientLocnName(), client.city(), pair)
        .filter(part -> part != null && !part.isBlank())
        .collect(Collectors.joining(" \u00b7 "));
  }

  /**
   * Deletes a site, if nothing depends on it.
   *
   * <p><b>A hard delete, and it is not recoverable.</b> That is what legacy does —
   * {@code CBR.DELETE_SITE} is a bare {@code DELETE FROM CROSSING_SITE WHERE CROSSING_SITE_ID = :id}
   * — and CBR has no soft-delete column on the table and writes no history row for a site, so there
   * is nothing to reverse it with. Worth knowing before this is offered anywhere more reachable than
   * behind a confirmation dialog and the destructive capability.
   *
   * <h3>The guards, and why they are here rather than left to the database</h3>
   * Two tables key to {@code CROSSING_SITE} and neither cascades, so the database will refuse a
   * delete that would orphan a child — with {@code ORA-02292}, which says nothing a user can act on.
   * Checking first turns the same refusal into a sentence naming what is in the way.
   *
   * <ul>
   *   <li><b>Active structures</b> — legacy's {@code errors.site.delete}.</li>
   *   <li><b>Archived structures</b> — legacy's {@code errors.site.delete.inactive}. Reported
   *       separately because the user cannot see them from the search results and would otherwise
   *       be told a site with no visible structures cannot be deleted, with no way to find out
   *       why.</li>
   *   <li><b>Close-proximity inspections</b> — <b>not a legacy check</b>.
   *       {@code SiteSearchAction.delete} tests the structures and stops, so in legacy a site
   *       carrying one of these and no structures passes every guard and fails in the database. This
   *       closes that gap rather than reproducing it.</li>
   * </ul>
   *
   * <p>The checks are read-then-delete in one transaction, not a lock. Two people deleting the same
   * site is harmless — the second gets a 404 — and someone adding a structure between the count and
   * the delete is caught by the foreign key, which is the backstop this is layered on top of rather
   * than a replacement for.
   *
   * @param siteId the {@code CROSSING_SITE_ID}
   * @throws SiteNotFoundException if no such site exists
   * @throws SiteInUseException    if a structure or inspection still references it
   */
  @Transactional
  public void delete(String siteId) {
    if (!crossingSiteRepository.existsById(siteId)) {
      throw new SiteNotFoundException(siteId);
    }

    long active = crossingStructureRepository.countByCrossingSiteIdAndActiveInd(siteId, ACTIVE);
    if (active > 0) {
      throw new SiteInUseException(
          "Site " + siteId + " has " + active + " associated structure(s) and cannot be deleted.");
    }

    long archived = crossingStructureRepository.countByCrossingSiteIdAndActiveInd(siteId, ARCHIVED);
    if (archived > 0) {
      throw new SiteInUseException("Site " + siteId + " has " + archived
          + " associated archived structure(s) and cannot be deleted.");
    }

    long inspections = closeProximityInspectionRepository.countByCrossingSiteId(siteId);
    if (inspections > 0) {
      throw new SiteInUseException("Site " + siteId + " has " + inspections
          + " associated close proximity inspection(s) and cannot be deleted.");
    }

    crossingSiteRepository.deleteById(siteId);
    log.info("Deleted site {}", siteId);
  }
}
