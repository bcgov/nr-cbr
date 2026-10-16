package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.InspectionCreateValidator.trimmed;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest.Item;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Add Inspection — legacy's {@code CBR.INSERT_INSPECTION} and what its save did around it
 * (InspectionAction.save, Inspection.save), in one transaction where legacy committed in steps.
 *
 * <p>Written, in order: the inspection (its structure's site at the time, entered and last updated
 * by the user, now); its status history's first row, In Progress ({@code PRO}) or Submitted
 * ({@code SUB}) as chosen; each answered
 * question; and, as legacy, every outstanding repair from the structure's earlier inspections,
 * carried forward onto this one — a copy with the next repair number and status {@code CF}, the
 * original marked carried forward.
 *
 * <p>Nothing of the structure changes: an inspection not yet reviewed has no load rating and sets
 * no next inspection — those come with its review. Legacy also emailed a Submitted inspection's
 * report to TRIM; that waits for the inspection report (TODO in cbr-inspection-add.local.md).
 */
@Service
public class InspectionCreateService {

  private static final Logger log = LoggerFactory.getLogger(InspectionCreateService.class);

  private static final String CARRIED_FORWARD = "CF";
  private static final String YES = "Y";
  private static final String NO = "N";

  private final InspectionFormService forms;
  private final InspectionCreateValidator validator;
  private final StructureDetailService structureDetails;
  private final CrossingStructureRepository structures;
  private final StructureInspectionRepository inspections;
  private final InspectionReportStatusRepository statuses;
  private final StructureInspectionItemRepository items;
  private final StructureRepairRepository repairs;
  private final LoggedUserHelper loggedUser;

  @SuppressWarnings("java:S107") // One repository per table a new inspection writes.
  public InspectionCreateService(
      InspectionFormService forms,
      InspectionCreateValidator validator,
      StructureDetailService structureDetails,
      CrossingStructureRepository structures,
      StructureInspectionRepository inspections,
      InspectionReportStatusRepository statuses,
      StructureInspectionItemRepository items,
      StructureRepairRepository repairs,
      LoggedUserHelper loggedUser) {
    this.forms = forms;
    this.validator = validator;
    this.structureDetails = structureDetails;
    this.structures = structures;
    this.inspections = inspections;
    this.statuses = statuses;
    this.items = items;
    this.repairs = repairs;
    this.loggedUser = loggedUser;
  }

  /**
   * Adds an inspection of a structure, In Progress.
   *
   * @return the new inspection's id
   * @throws FieldValidationException with a message for each field at fault — the structure among
   *     them when it is missing or incomplete, as legacy's "Structure # is invalid."
   */
  @Transactional
  public CreatedResponse create(InspectionCreateRequest request) {
    Long structureId = request.structureId();
    if (structureId == null) {
      throw refused(Map.of("structureId", "Structure # is required."));
    }
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    if (!structureDetails.findById(structureId).outstanding().isEmpty()) {
      throw refused(Map.of("structureId", "Structure " + structure.getCrossingStructureName()
          + " is incomplete. Complete it on its structure page before it can be inspected."));
    }
    InspectionFormResponse form = forms.form(structureId);
    Map<String, String> errors = validator.validate(request, form);
    if (!errors.isEmpty()) {
      throw refused(errors);
    }

    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    StructureInspectionEntity inspection = inspections.save(StructureInspectionEntity.builder()
        .crossingStructureId(structureId)
        .siteAtTimeOfInspection(structure.getCrossingSiteId())
        .strctreInspectionTypeCode(trimmed(request.typeCode()).toUpperCase(Locale.ROOT))
        .inspectionDate(request.inspectionDate())
        .inspectorName(trimmed(request.inspectorName()))
        .inspectorCompanyName(trimmed(request.inspectorCompanyName()))
        .inspectionCost(request.cost())
        .pengInspectionReqInd(yesNo(request.professionalInspectionRequired()))
        .inspectionComment(trimmed(request.inspectionComment()))
        .channelPwlDepth(request.presentWaterDepth())
        .channelPwlWidth(request.presentWaterWidth())
        .channelHwlClearance(request.highWaterClearance())
        .highWaterHazardInd(yesNo(request.highWater()))
        .debrisHazardInd(yesNo(request.debris()))
        .scourHazardInd(yesNo(request.scour()))
        .iceHazardInd(yesNo(request.ice()))
        .aggradationHazardInd(yesNo(request.aggradation()))
        .hazardComment(trimmed(request.hazardComment()))
        .entryUserid(user).entryTimestamp(now).updateUserid(user).updateTimestamp(now)
        .build());
    Long inspectionId = inspection.getInspectionId();

    statuses.save(InspectionReportStatusEntity.builder()
        .inspectionId(inspectionId)
        .inspectionReportStatusCode(InspectionCreateValidator.statusOf(request))
        .entryUserid(user)
        .entryTimestamp(now)
        .build());
    saveAnswers(inspectionId, request.items() == null ? List.of() : request.items(), user, now);
    int carried = carryForwardRepairs(structureId, inspectionId, user, now);

    log.info("Added inspection {} of structure {} ({} repairs carried forward)", inspectionId,
        structureId, carried);
    return new CreatedResponse(String.valueOf(inspectionId));
  }

  /** Each answered question; one left blank is not a row — the answer column is required. */
  private void saveAnswers(Long inspectionId, List<Item> answered, String user,
      LocalDateTime now) {
    for (Item item : answered) {
      String answer = trimmed(item.answer());
      if (answer == null) {
        continue;
      }
      items.save(StructureInspectionItemEntity.builder()
          .inspectionId(inspectionId)
          .subsectionCode(item.subsectionCode())
          .questionCode(item.questionCode())
          .forestServiceBridgeSpanId(item.spanId())
          .forestServiceBridgePierId(item.pierId())
          .stringerNumber(item.stringerNumber())
          .answerValue(answer)
          .itemComment(trimmed(item.comment()))
          .entryUserid(user).entryTimestamp(now).updateUserid(user).updateTimestamp(now)
          .build());
    }
  }

  /**
   * Legacy's carry forward: each outstanding repair is copied onto the new inspection with the
   * structure's next repair number and status Carried Forward, and the original marked carried
   * forward so it is not carried twice.
   *
   * @return how many were carried
   */
  private int carryForwardRepairs(Long structureId, Long inspectionId, String user,
      LocalDateTime now) {
    List<StructureRepairEntity> outstanding = repairs.findToCarryForward(structureId);
    long number = repairs.findHighestNumber(structureId);
    for (StructureRepairEntity original : outstanding) {
      number += 1;
      repairs.save(original.toBuilder()
          .repairId(null)
          .inspectionId(inspectionId)
          .inspection(null)
          .repairNumber(number)
          .repairStatusCode(CARRIED_FORWARD)
          .carriedForwardInd(NO)
          .entryUserid(user).entryTimestamp(now).updateUserid(user).updateTimestamp(now)
          .build());
      repairs.save(original.toBuilder()
          .carriedForwardInd(YES)
          .updateUserid(user).updateTimestamp(now)
          .build());
    }
    return outstanding.size();
  }

  private static FieldValidationException refused(Map<String, String> errors) {
    return new FieldValidationException("The inspection cannot be added", errors);
  }

  private static String yesNo(Boolean value) {
    return Boolean.TRUE.equals(value) ? YES : NO;
  }
}
