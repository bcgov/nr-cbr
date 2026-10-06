package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.AbutmentCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.BuiltByCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.DeckTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.DesignVehicleLoadCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredClvrtMaterialCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredCulvertTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.HeadwallLocationCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.LoadPostingSignWrrntdCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OpnBtomClvrtSubstrctreCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RecreationProjectEntity;
import ca.bc.gov.nrs.cbr.model.v1.RunningSurfaceCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreLoadRatingRsnCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCurbTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureReplacementXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureSourceCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.SuperstructureTypeCodeEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CbrOrgUnitRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeSpanRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.RecreationProjectRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCommentRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionReviewerRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureLoadRatingRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureReplacementXrefRepository;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Bridge;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Comment;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Common;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Culvert;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.LoadRating;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.LoadRatingEntry;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.LoadRatingStatus;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.OutstandingItem;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Replacement;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Section;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Site;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.StructureRef;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One structure, for its page — the header and the Details tab of legacy's structure page
 * ({@code cbr-structure-page.local.md} §1–2).
 *
 * <p>Legacy assembles that page from {@code FIND_STRUCTURE_BY_ID}, {@code FIND_SITE_BY_ID} and a
 * string of AJAX calls after load — comments, load ratings, the site, the client. This answers them
 * all at once: the page cannot usefully render without any of them.
 *
 * <p><b>Values are as stored.</b> Legacy's page invents some on load — a next inspection a year
 * out, "UNK" load posting signs, a culvert routine frequency of 3, a fake load rating on an unnamed
 * structure. None is reproduced: what is not stored comes back null.
 */
@Service
public class StructureDetailService {

  private static final String YES = "Y";
  private static final String ACTIVE = "Y";
  private static final String GENERAL_COMMENT = "N";
  private static final String RECREATION = "REC";
  /** A portable superstructure in storage, which has no abutments to record. */
  private static final String IN_STORAGE = "S";
  private static final List<String> BRIDGES = List.of("PB", "TB");
  private static final List<String> CULVERTS = List.of("CUL", "WLC");
  private static final List<String> REVIEWED_STATUSES = List.of("RVD", "ACC");

  private final CrossingStructureRepository structures;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final ForestServiceBridgeSpanRepository spans;
  private final StructureCommentRepository comments;
  private final StructureLoadRatingRepository loadRatings;
  private final StructureInspectionRepository inspections;
  private final StructureInspectionReviewerRepository reviewers;
  private final StructureReplacementXrefRepository replacements;
  private final CbrOrgUnitRepository orgUnits;
  private final RecreationProjectRepository recreationProjects;
  private final ClientLocationRepository clientLocations;
  private final LoadRatingService loadRatingService;
  private final EntityManager entityManager;

  @SuppressWarnings("java:S107") // One repository per table the page reads.
  public StructureDetailService(
      CrossingStructureRepository structures,
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      ForestServiceBridgeSpanRepository spans,
      StructureCommentRepository comments,
      StructureLoadRatingRepository loadRatings,
      StructureInspectionRepository inspections,
      StructureInspectionReviewerRepository reviewers,
      StructureReplacementXrefRepository replacements,
      CbrOrgUnitRepository orgUnits,
      RecreationProjectRepository recreationProjects,
      ClientLocationRepository clientLocations,
      LoadRatingService loadRatingService,
      EntityManager entityManager) {
    this.structures = structures;
    this.bridges = bridges;
    this.culverts = culverts;
    this.spans = spans;
    this.comments = comments;
    this.loadRatings = loadRatings;
    this.inspections = inspections;
    this.reviewers = reviewers;
    this.replacements = replacements;
    this.orgUnits = orgUnits;
    this.recreationProjects = recreationProjects;
    this.clientLocations = clientLocations;
    this.loadRatingService = loadRatingService;
    this.entityManager = entityManager;
  }

  /**
   * One structure, for its page.
   *
   * @throws StructureNotFoundException if there is no such structure — legacy showed a blank
   *                                    new-structure form instead
   */
  @Transactional(readOnly = true)
  public StructureDetailResponse findById(long structureId) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    CrossingSiteEntity site = structure.getSite();

    Optional<ForestServiceBridgeEntity> bridge =
        bridges.findFirstByCrossingStructureIdOrderByForestServiceBridgeId(structureId);
    Optional<ForestServiceCulvertEntity> culvert =
        culverts.findFirstByCrossingStructureIdOrderByForestServiceCulvertId(structureId);
    LoadRating loadRating = loadRating(structure);

    return new StructureDetailResponse(
        String.valueOf(structureId),
        structure.getCrossingStructureName(),
        ACTIVE.equals(structure.getActiveInd()),
        decode(StructureTypeClassCodeEntity.class, structure.getStructureTypeClassCode(),
            StructureTypeClassCodeEntity::getDescription),
        site == null ? null : site(site),
        common(structure),
        bridge.map(this::bridge).orElse(null),
        culvert.map(found -> culvert(found, structure)).orElse(null),
        comments(structureId),
        loadRating,
        replaced(structureId),
        replacedBy(structureId),
        new Replacement(
            structure.getEstimatedClosureYear(),
            structure.getLightVehicleReplacementYear(),
            structure.getFullLogHaulReplacementYear(),
            structure.getEstimatedReplacementCost(),
            structure.getEstimatedReplacementCostCmt()),
        outstanding(structure, site, bridge, culvert, loadRating));
  }

  // ─── The header ──────────────────────────────────────────────────────

  private Site site(CrossingSiteEntity site) {
    boolean recreation = RECREATION.equals(site.getCrossingSiteTypeCode());
    return new Site(
        site.getCrossingSiteId(),
        site.getCrossingSiteTypeCode(),
        decode(CrossingSiteStatusCodeEntity.class, site.getCrossingSiteStatusCode(),
            CrossingSiteStatusCodeEntity::getDescription),
        // Legacy shows this one as a raw code.
        decode(StructureInspectionStatusCodeEntity.class, site.getStructureInspectionStatusCode(),
            StructureInspectionStatusCodeEntity::getDescription),
        orgUnitName(site.getOrgUnitNo()),
        recreation ? null : orgUnitName(site.getManagementOrgUnitNo()),
        site.getRoadSection() == null ? null : site.getRoadSection().getRoadSectName(),
        recreation && site.getForestFileId() != null
            ? recreationProjects.findById(site.getForestFileId())
                .map(RecreationProjectEntity::getProjectName).orElse(null)
            : null,
        site.getCrossingName(),
        site.getForestFileId(),
        site.getRoadSectionId(),
        site.getPointOfCommencementDistance() == null
            ? null
            : site.getPointOfCommencementDistance().toPlainString(),
        maintainerLabel(site));
  }

  /** A district or management area's name, from CBR's own org units — recreation districts too. */
  private String orgUnitName(Long orgUnitNo) {
    return orgUnitNo == null
        ? null
        : orgUnits.findById(orgUnitNo).map(CbrOrgUnitEntity::getOrgUnitName).orElse(null);
  }

  /** As Site Detail shows the maintainer — see {@code SiteService.describe}. */
  private String maintainerLabel(CrossingSiteEntity site) {
    if (site.getClientNumber() == null || site.getClientLocnCode() == null) {
      return null;
    }
    return clientLocations.findMaintainer(site.getClientNumber(), site.getClientLocnCode())
        .stream().findFirst().map(SiteService::describe).orElse(null);
  }

  // ─── The Details tab ─────────────────────────────────────────────────

  private Common common(CrossingStructureEntity structure) {
    return new Common(
        decode(BuiltByCodeEntity.class, structure.getBuiltByCode(),
            BuiltByCodeEntity::getDescription),
        structure.getYearFabricated(),
        structure.getYearBuilt(),
        structure.getInventoryAddedYear(),
        decode(StructureSourceCodeEntity.class, structure.getStructureSourceCode(),
            StructureSourceCodeEntity::getDescription),
        structure.getOriginalReplacementYear(),
        YES.equals(structure.getAsBuiltInfoPresentInd()),
        structure.getInstallationCost(),
        structure.getMaterialCost(),
        YES.equals(structure.getPortableStructureInd()));
  }

  private Bridge bridge(ForestServiceBridgeEntity bridge) {
    return new Bridge(
        spans.countByForestServiceBridgeId(bridge.getForestServiceBridgeId()),
        YES.equals(bridge.getNeedleBeamInd()),
        bridge.getTotalBridgeLength(),
        decode(SuperstructureTypeCodeEntity.class, bridge.getSuperstructureTypeCode(),
            SuperstructureTypeCodeEntity::getDescription),
        bridge.getSuperstructureComment(),
        decode(DeckTypeCodeEntity.class, bridge.getDeckTypeCode(),
            DeckTypeCodeEntity::getDescription),
        bridge.getDeckTypeComment(),
        bridge.getDeckWidth(),
        decode(RunningSurfaceCodeEntity.class, bridge.getRunningSurfaceCode(),
            RunningSurfaceCodeEntity::getDescription),
        decode(StructureCurbTypeCodeEntity.class, bridge.getStructureCurbTypeCode(),
            StructureCurbTypeCodeEntity::getDescription),
        bridge.getStructureCurbTypeCmt(),
        decode(AbutmentCodeEntity.class, bridge.getLeftAbutmentCode(),
            AbutmentCodeEntity::getDescription),
        decode(AbutmentCodeEntity.class, bridge.getRightAbutmentCode(),
            AbutmentCodeEntity::getDescription),
        bridge.getAbutmentComment());
  }

  private Culvert culvert(ForestServiceCulvertEntity culvert, CrossingStructureEntity structure) {
    long onSite = structure.getCrossingSiteId() == null
        ? 0
        : structures.countByCrossingSiteIdAndActiveIndAndStructureTypeClassCodeIn(
            structure.getCrossingSiteId(), ACTIVE, CULVERTS);
    return new Culvert(
        culvert.getCulvertNumber(),
        onSite,
        culvert.getCulvertLength(),
        culvert.getSlope(),
        decode(EngineeredCulvertTypeCodeEntity.class, culvert.getEngineeredCulvertTypeCode(),
            EngineeredCulvertTypeCodeEntity::getDescription),
        decode(EngineeredClvrtMaterialCodeEntity.class, culvert.getEngineeredCulvertMaterialCode(),
            EngineeredClvrtMaterialCodeEntity::getDescription),
        culvert.getCulvertMaterialComment(),
        culvert.getInletCoverDepth(),
        culvert.getOutletCoverDepth(),
        culvert.getOpeningHeight(),
        culvert.getOpeningWidth(),
        decode(HeadwallLocationCodeEntity.class, culvert.getHeadwallLocationCode(),
            HeadwallLocationCodeEntity::getDescription),
        decode(OpnBtomClvrtSubstrctreCodeEntity.class, culvert.getOpnBtomClvrtSubstrctreCode(),
            OpnBtomClvrtSubstrctreCodeEntity::getDescription));
  }

  private List<Comment> comments(long structureId) {
    return comments.findByKind(structureId, GENERAL_COMMENT).stream()
        .map(comment -> new Comment(
            String.valueOf(comment.getStructureCommentId()),
            comment.getStructureComment(),
            comment.getUpdateUserid(),
            comment.getUpdateTimestamp()))
        .toList();
  }

  // ─── Load ratings ────────────────────────────────────────────────────

  /**
   * Legacy's Load Rating Details — {@code FIND_LOAD_RATINGS_BY_STRC_ID}: every manual rating, and
   * every inspection whose report was reviewed, with the rating it recorded if it recorded one.
   * Newest first. The current rating is {@link LoadRatingService#currentLoadRating}'s, the rule
   * {@code CROSSING_STRUCTURE.CURRENT_LOAD_RATING} is kept by.
   *
   * <p>One departure: a reviewed rating's Reviewed Date is the inspection's
   * {@code PENG_REVIEWER_DATE}. Legacy shows the inspection's {@code UPDATE_TIMESTAMP}, which
   * moves whenever the inspection is touched.
   */
  private LoadRating loadRating(CrossingStructureEntity structure) {
    long structureId = structure.getCrossingStructureId();
    Optional<StructureLoadRatingEntity> current = loadRatingService.currentLoadRating(structureId);
    Long currentId = current.map(StructureLoadRatingEntity::getStructureLoadRatingId).orElse(null);

    List<Row> rows = new ArrayList<>();
    for (StructureLoadRatingEntity manual :
        loadRatings.findByCrossingStructureIdAndInspectionIdIsNull(structureId)) {
      rows.add(new Row(manual.getEntryTimestamp(), manual.getStructureLoadRatingId(),
          new LoadRatingEntry(
              String.valueOf(manual.getStructureLoadRatingId()),
              manual.getLoadRating(),
              reason(manual.getReasonCode()),
              manual.getReasonComment(),
              manual.getEntryTimestamp() == null ? null : manual.getEntryTimestamp().toLocalDate(),
              manual.getEntryUserid(),
              null,
              LoadRatingStatus.MANUAL,
              null,
              manual.getStructureLoadRatingId().equals(currentId))));
    }

    List<StructureInspectionEntity> reviewed =
        inspections.findReviewedByStructure(structureId, REVIEWED_STATUSES);
    if (!reviewed.isEmpty()) {
      List<Long> inspectionIds =
          reviewed.stream().map(StructureInspectionEntity::getInspectionId).toList();
      Map<Long, StructureLoadRatingEntity> ratingByInspection =
          loadRatings.findByInspectionIdIn(inspectionIds).stream().collect(Collectors.toMap(
              StructureLoadRatingEntity::getInspectionId, Function.identity(),
              (first, second) -> first));
      Map<Long, String> reviewerIds = reviewers.findAllById(reviewed.stream()
              .map(StructureInspectionEntity::getInspectionReviewerId)
              .filter(Objects::nonNull).distinct().toList())
          .stream().collect(Collectors.toMap(
              StructureInspectionReviewerEntity::getInspectionReviewerId,
              StructureInspectionReviewerEntity::getUserid));

      for (StructureInspectionEntity inspection : reviewed) {
        StructureLoadRatingEntity rating = ratingByInspection.get(inspection.getInspectionId());
        Long ratingId = rating == null ? null : rating.getStructureLoadRatingId();
        rows.add(new Row(
            inspection.getInspectionDate() == null
                ? null
                : inspection.getInspectionDate().atStartOfDay(),
            ratingId,
            new LoadRatingEntry(
                ratingId == null
                    ? "inspection-" + inspection.getInspectionId()
                    : String.valueOf(ratingId),
                rating == null ? null : rating.getLoadRating(),
                rating == null ? CodeValue.NONE : reason(rating.getReasonCode()),
                rating == null ? null : rating.getReasonComment(),
                inspection.getInspectionDate(),
                reviewerIds.get(inspection.getInspectionReviewerId()),
                inspection.getPengReviewerDate() == null
                    ? null
                    : inspection.getPengReviewerDate().toLocalDate(),
                LoadRatingStatus.REVIEWED,
                String.valueOf(inspection.getInspectionId()),
                ratingId != null && ratingId.equals(currentId))));
      }
    }

    // Newest first; legacy lists oldest first and reverses on the page.
    rows.sort(Comparator.comparing(Row::sortedOn, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(Row::ratingId, Comparator.nullsLast(Comparator.reverseOrder())));

    return new LoadRating(
        current.map(StructureLoadRatingEntity::getLoadRating).orElse(null),
        rows.stream().map(Row::entry).toList(),
        decode(LoadPostingSignWrrntdCodeEntity.class, structure.getLoadPostingSignWrrntdCode(),
            LoadPostingSignWrrntdCodeEntity::getDescription),
        YES.equals(structure.getLoadRatingUnknownIndicator()),
        decode(DesignVehicleLoadCodeEntity.class, structure.getDesignVehicleLoadCode(),
            DesignVehicleLoadCodeEntity::getDescription),
        structure.getDesignVehicleCmt(),
        structure.getDesignLoadRating());
  }

  /** A history row and what it sorts by. */
  private record Row(LocalDateTime sortedOn, Long ratingId, LoadRatingEntry entry) {}

  private CodeValue reason(String code) {
    return decode(StrctreLoadRatingRsnCodeEntity.class, code,
        StrctreLoadRatingRsnCodeEntity::getDescription);
  }

  // ─── Replacement history ─────────────────────────────────────────────

  private List<StructureRef> replaced(long structureId) {
    return refs(replacements.findByReplacesStructureNumber(structureId).stream()
        .map(StructureReplacementXrefEntity::getReplacedStructureNumber).toList());
  }

  private List<StructureRef> replacedBy(long structureId) {
    return refs(replacements.findByReplacedStructureNumber(structureId).stream()
        .map(StructureReplacementXrefEntity::getReplacesStructureNumber).toList());
  }

  /** Structures by id, as links, in id order. An id with no structure is left out. */
  private List<StructureRef> refs(List<Long> ids) {
    return ids.isEmpty()
        ? List.of()
        : structures.findAllById(ids).stream()
            .sorted(Comparator.comparing(CrossingStructureEntity::getCrossingStructureId))
            .map(found -> new StructureRef(
                String.valueOf(found.getCrossingStructureId()), found.getCrossingStructureName()))
            .toList();
  }

  // ─── What is missing ─────────────────────────────────────────────────

  /**
   * Why the structure is incomplete, item by item — legacy's structure page {@code isComplete()}
   * (Structure.java:813-852, Bridge.java:830-859, Culvert.java:609-631). The same rules as
   * Structure Search's "Incomplete Data?" filter, so the two always agree.
   */
  private static List<OutstandingItem> outstanding(
      CrossingStructureEntity structure,
      CrossingSiteEntity site,
      Optional<ForestServiceBridgeEntity> bridge,
      Optional<ForestServiceCulvertEntity> culvert,
      LoadRating loadRating) {
    List<OutstandingItem> items = new ArrayList<>();
    if (loadRating.history().isEmpty() && !loadRating.reviewRequired()) {
      items.add(new OutstandingItem(Section.DETAILS,
          "A load rating, or Load Rating Review Required"));
    }
    boolean recreation = site != null && RECREATION.equals(site.getCrossingSiteTypeCode());
    if (structure.getFullLogHaulReplacementYear() == null && !recreation) {
      items.add(new OutstandingItem(Section.DETAILS, "Estimated Load Restriction (year)"));
    }
    if (structure.getNextPlannedInspectionDate() == null) {
      items.add(new OutstandingItem(Section.INSPECTIONS, "Next Planned Routine Inspection"));
    }
    if (isBlank(structure.getStructureSourceCode())) {
      items.add(new OutstandingItem(Section.DETAILS, "Source"));
    }
    if (isBlank(structure.getCrossingSiteId())) {
      items.add(new OutstandingItem(Section.SITE, "Site #"));
    }
    String typeClass = structure.getStructureTypeClassCode();
    if (BRIDGES.contains(typeClass)) {
      bridgeOutstanding(bridge, items);
    }
    if (CULVERTS.contains(typeClass)) {
      culvertOutstanding(culvert, items);
    }
    return items;
  }

  private static void bridgeOutstanding(
      Optional<ForestServiceBridgeEntity> found, List<OutstandingItem> items) {
    if (found.isEmpty()) {
      items.add(new OutstandingItem(Section.DETAILS, "Bridge details"));
      return;
    }
    ForestServiceBridgeEntity bridge = found.get();
    missing(items, bridge.getTotalBridgeLength() == null, "Bridge Length (metres)");
    missing(items, bridge.getDeckWidth() == null, "Deck Width (metres)");
    missing(items, isBlank(bridge.getRunningSurfaceCode()), "Running Surface");
    missing(items, isBlank(bridge.getDeckTypeCode()), "Deck Type");
    missing(items, isBlank(bridge.getSuperstructureTypeCode()), "Superstructure");
    // Both abutments, unless the portable superstructure is in storage.
    if (!IN_STORAGE.equals(bridge.getPortableSuperstructureStatusCode())) {
      missing(items, isBlank(bridge.getLeftAbutmentCode()), "Left Abutment");
      missing(items, isBlank(bridge.getRightAbutmentCode()), "Right Abutment");
    }
  }

  private static void culvertOutstanding(
      Optional<ForestServiceCulvertEntity> found, List<OutstandingItem> items) {
    if (found.isEmpty()) {
      items.add(new OutstandingItem(Section.DETAILS, "Culvert details"));
      return;
    }
    ForestServiceCulvertEntity culvert = found.get();
    missing(items, culvert.getCulvertNumber() == null, "Culvert Number");
    missing(items, culvert.getCulvertLength() == null, "Culvert Length (metres)");
    missing(items, culvert.getOpeningHeight() == null, "Opening Height (mm)");
    missing(items, culvert.getOpeningWidth() == null, "Opening Width (mm)");
    missing(items, isBlank(culvert.getEngineeredCulvertMaterialCode()), "Culvert Material");
    missing(items, isBlank(culvert.getEngineeredCulvertTypeCode()), "Culvert Type");
  }

  private static void missing(List<OutstandingItem> items, boolean isMissing, String label) {
    if (isMissing) {
      items.add(new OutstandingItem(Section.DETAILS, label));
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  // ─── Codes ───────────────────────────────────────────────────────────

  /**
   * A stored code with its description from its code table. A code the table lacks still comes
   * back, with no description — the page then shows the bare code rather than nothing.
   */
  private <E> CodeValue decode(Class<E> table, String code, Function<E, String> description) {
    if (isBlank(code)) {
      return CodeValue.NONE;
    }
    E row = entityManager.find(table, code);
    return new CodeValue(code, row == null ? null : description.apply(row));
  }
}
