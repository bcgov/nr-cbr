package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureCreateValidator.trimmed;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgePierRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeSpanRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCommentRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureLoadRatingRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.BridgeDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.CreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.CulvertDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionScheduleRequest;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.PierRequest;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.SpanRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailsRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Add Structure — legacy's {@code INSERT_BRIDGE_STRUCTURE} / {@code INSERT_CULVERT_STRUCTURE} and
 * what its save wrote after them, in one transaction where legacy committed each step on its own.
 *
 * <p>Written, in order: the structure ({@code ACTIVE_IND = 'Y'}, the name in capitals, entered and
 * last updated by the user, now); its bridge or culvert; a bridge's spans and piers — all of them,
 * where legacy kept only the first of each; the first comment, if any; and legacy's 0-tonne "NS -
 * New Structure" manual rating, which as the current rating sets {@code CURRENT_LOAD_RATING} to 0
 * and Load Rating Review Required to N, as {@code INSERT_LOAD_RATING} did.
 */
@Service
public class StructureCreateService {

  private static final Logger log = LoggerFactory.getLogger(StructureCreateService.class);

  private static final String YES = "Y";
  private static final String NO = "N";
  private static final String OTHER = "OTH";
  private static final String OTHER_VEHICLE = "OTHER";
  private static final String WOOD_LOG_TYPE = "BOX";
  private static final String NEW_STRUCTURE = "NS";

  private final StructureCreateValidator validator;
  private final CrossingStructureRepository structures;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final ForestServiceBridgeSpanRepository spans;
  private final ForestServiceBridgePierRepository piers;
  private final StructureLoadRatingRepository loadRatings;
  private final StructureCommentRepository comments;
  private final LoggedUserHelper loggedUser;

  @SuppressWarnings("java:S107") // One repository per table a new structure writes.
  public StructureCreateService(
      StructureCreateValidator validator,
      CrossingStructureRepository structures,
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      ForestServiceBridgeSpanRepository spans,
      ForestServiceBridgePierRepository piers,
      StructureLoadRatingRepository loadRatings,
      StructureCommentRepository comments,
      LoggedUserHelper loggedUser) {
    this.validator = validator;
    this.structures = structures;
    this.bridges = bridges;
    this.culverts = culverts;
    this.spans = spans;
    this.piers = piers;
    this.loadRatings = loadRatings;
    this.comments = comments;
    this.loggedUser = loggedUser;
  }

  /**
   * Adds a structure to a site.
   *
   * @return the new structure's id
   * @throws FieldValidationException with a message for each field at fault, keyed as
   *     {@link StructureCreateValidator} describes
   */
  @Transactional
  public CreatedResponse create(StructureCreateRequest given) {
    StructureCreateRequest request = StructureCreateValidator.complete(given);
    StructureCreateValidator.Result checked = validator.validate(request);
    if (!checked.errors().isEmpty()) {
      throw new FieldValidationException("The structure cannot be added", checked.errors());
    }

    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    String typeClass = trimmed(request.typeClassCode());
    boolean bridge = StructureCreateValidator.BRIDGE_TYPES.contains(typeClass);
    CrossingStructureEntity structure = structures.save(
        structureOf(request, typeClass, bridge, checked.designLoadRating(), user, now));
    Long structureId = structure.getCrossingStructureId();

    if (bridge) {
      Long bridgeId = bridges.save(bridgeOf(structureId, request.bridge()))
          .getForestServiceBridgeId();
      for (SpanRequest span : request.spans()) {
        spans.save(ForestServiceBridgeSpanEntity.builder()
            .forestServiceBridgeId(bridgeId)
            .spanNumber(span.number().intValue())
            .spanLength(span.lengthMetres())
            .entryUserid(user).entryTimestamp(now).updateUserid(user).updateTimestamp(now)
            .build());
      }
      for (PierRequest pier : request.piers()) {
        piers.save(ForestServiceBridgePierEntity.builder()
            .forestServiceBridgeId(bridgeId)
            .pierNumber(pier.number())
            .pierTypeCode(pier.typeCode().trim())
            .entryUserid(user).entryTimestamp(now).updateUserid(user).updateTimestamp(now)
            .build());
      }
    } else {
      culverts.save(culvertOf(structureId, request.culvert(),
          StructureCreateValidator.WOOD_LOG.equals(typeClass)));
    }

    String comment = trimmed(request.comment());
    if (comment != null) {
      comments.save(StructureCommentEntity.builder()
          .crossingStructureId(structureId)
          .structureComment(comment)
          .plannedInspectionCmtInd(NO)
          .entryUserid(user).entryTimestamp(now).updateUserid(user).updateTimestamp(now)
          .build());
    }
    loadRatings.save(StructureLoadRatingEntity.builder()
        .crossingStructureId(structureId)
        .loadRating(BigDecimal.ZERO)
        .siteAtTimeOfLoadRating(structure.getCrossingSiteId())
        .reasonCode(NEW_STRUCTURE)
        .entryUserid(user).entryTimestamp(now).updateUserid(user).updateTimestamp(now)
        .build());

    log.info("Added structure {} to site {}", structureId, structure.getCrossingSiteId());
    return new CreatedResponse(String.valueOf(structureId));
  }

  private CrossingStructureEntity structureOf(StructureCreateRequest request, String typeClass,
      boolean bridge, Long designLoadRating, String user, LocalDateTime now) {
    StructureDetailsRequest details = request.details();
    LoadRatingDetailsRequest loadRating = request.loadRating();
    ReplacementDetailsRequest replacement = request.replacement();
    InspectionScheduleRequest schedule = request.schedule();
    Boolean portable = bridge ? request.bridge().portable() : request.culvert().portable();
    String vehicle = trimmed(loadRating.designVehicleCode());
    // Replacement Details only from a P.Eng; the validator has refused them from anyone else.
    boolean peng = loggedUser.isPeng();
    boolean closeProximity = schedule.closeProximityRequired();
    return CrossingStructureEntity.builder()
        .crossingStructureName(trimmed(request.structureName()).toUpperCase(Locale.ROOT))
        .crossingSiteId(trimmed(request.siteId()))
        .structureTypeClassCode(typeClass)
        .activeInd(YES)
        .builtByCode(trimmed(details.builtByCode()))
        .yearFabricated(details.yearFabricated())
        .yearBuilt(details.yearBuilt())
        .inventoryAddedYear(details.inventoryAddedYear())
        .structureSourceCode(trimmed(details.sourceCode()))
        .originalReplacementYear(details.endOfDesignLifeYear())
        .asBuiltInfoPresentInd(yesNo(details.asBuiltInfoOnFile()))
        .installationCost(details.installationCost())
        .materialCost(details.materialCost())
        .portableStructureInd(yesNo(portable))
        .loadPostingSignWrrntdCode(trimmed(loadRating.loadPostingSignsCode()))
        .designVehicleLoadCode(vehicle)
        .designVehicleCmt(OTHER_VEHICLE.equals(vehicle)
            ? trimmed(loadRating.designVehicleComment())
            : null)
        .designLoadRating(designLoadRating == null ? null : BigDecimal.valueOf(designLoadRating))
        // The NS rating below is the current one, as INSERT_LOAD_RATING leaves a new structure.
        .currentLoadRating(BigDecimal.ZERO)
        .loadRatingUnknownIndicator(NO)
        .estimatedClosureYear(peng ? replacement.closureYear() : null)
        .lightVehicleReplacementYear(peng ? replacement.replacementYear() : null)
        .fullLogHaulReplacementYear(peng ? replacement.loadRestrictionYear() : null)
        .estimatedReplacementCost(peng ? replacement.cost() : null)
        .estimatedReplacementCostCmt(peng ? trimmed(replacement.costComment()) : null)
        .closeProximityInd(closeProximity ? YES : NO)
        .specialEquipmentRqmtCode(
            closeProximity ? trimmed(schedule.closeProximityEquipmentCode()) : null)
        .nextPlannedClsProxInspDt(closeProximity ? schedule.nextCloseProximityDate() : null)
        .nextPlannedInspectionDate(schedule.nextRoutineDate())
        .routineInspectionFrequency(String.valueOf(schedule.routineFrequencyYears()))
        .annualInspectionReqInd(NO)
        .entryUserid(user)
        .entryTimestamp(now)
        .updateUserid(user)
        .updateTimestamp(now)
        .build();
  }

  private static ForestServiceBridgeEntity bridgeOf(Long structureId,
      BridgeDetailsRequest bridge) {
    String superstructure = trimmed(bridge.superstructureTypeCode());
    String deckType = trimmed(bridge.deckTypeCode());
    String curb = trimmed(bridge.curbTypeCode());
    String left = trimmed(bridge.leftAbutmentCode());
    String right = trimmed(bridge.rightAbutmentCode());
    return ForestServiceBridgeEntity.builder()
        .crossingStructureId(structureId)
        .needleBeamInd(yesNo(bridge.needleBeams()))
        .totalBridgeLength(bridge.totalBridgeLength())
        .deckWidth(bridge.deckWidth())
        .superstructureTypeCode(superstructure)
        .superstructureComment(otherComment(superstructure, bridge.superstructureComment()))
        .deckTypeCode(deckType)
        .deckTypeComment(otherComment(deckType, bridge.deckTypeComment()))
        .runningSurfaceCode(trimmed(bridge.runningSurfaceCode()))
        .structureCurbTypeCode(curb)
        .structureCurbTypeCmt(otherComment(curb, bridge.curbTypeComment()))
        .leftAbutmentCode(left)
        .rightAbutmentCode(right)
        .abutmentComment(otherComment(OTHER.equals(left) || OTHER.equals(right) ? OTHER : null,
            bridge.abutmentComment()))
        .build();
  }

  private static ForestServiceCulvertEntity culvertOf(Long structureId,
      CulvertDetailsRequest culvert, boolean woodLog) {
    String material = trimmed(culvert.materialCode());
    return ForestServiceCulvertEntity.builder()
        .crossingStructureId(structureId)
        .culvertNumber(culvert.culvertNumber())
        .culvertLength(culvert.lengthMetres())
        .slope(culvert.gradient())
        // A Wood Log Culvert is saved as BOX with no open bottom, as legacy's page forced it.
        .engineeredCulvertTypeCode(woodLog ? WOOD_LOG_TYPE : trimmed(culvert.culvertTypeCode()))
        .opnBtomClvrtSubstrctreCode(woodLog ? null : trimmed(culvert.openBottomCode()))
        .engineeredCulvertMaterialCode(material)
        .culvertMaterialComment(otherComment(material, culvert.materialComment()))
        .inletCoverDepth(culvert.inletCoverDepthMm())
        .outletCoverDepth(culvert.outletCoverDepthMm())
        .openingHeight(culvert.openingHeightMm())
        .openingWidth(culvert.openingWidthMm())
        .headwallLocationCode(trimmed(culvert.headwallLocationCode()))
        .build();
  }

  /** A comment kept only with its Other. */
  private static String otherComment(String code, String comment) {
    return OTHER.equals(code) ? trimmed(comment) : null;
  }

  private static String yesNo(Boolean value) {
    return Boolean.TRUE.equals(value) ? YES : NO;
  }
}
