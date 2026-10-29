package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.InspectionLockedException;
import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.HazardsUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCommentRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionUpdateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionItemRepository;
import ca.bc.gov.nrs.cbr.struct.v1.ConditionUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest.Item;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inspection's own record, edited from its page — legacy's Inspection and Hydraulic Hazards
 * tabs on its update ({@code cbr-inspection-page.local.md} §2, §8), a card at a time. See
 * {@link #updateHazards} for the hazards; the Inspection tab's rules:
 *
 * <ul>
 *   <li>Date of Inspection, Inspector Name and Inspection Company Name are required past In
 *       Progress, or once given — legacy's {@code FRM.validate}: "X is required.";
 *   <li>the date is not in the future, and no other inspection of the structure is on it (offline
 *       ones aside);
 *   <li>the status moves between In Progress and Submitted here; Reviewed is an inspection
 *       reviewer's, through the review ({@link InspectionReviewService}); Rejected is an inspection
 *       reviewer's too, from In Progress or Submitted, here; out of Rejected only a P.Eng moves
 *       it, as legacy;
 *   <li>rejecting sets the structure's next planned routine inspection a year from today and its
 *       routine frequency to one year, as legacy's save at Rejected — once, on the change to
 *       Rejected, where legacy did it again at every save;
 *   <li>Professional Inspection Required is a Level 2 user's to change;
 *   <li>refused once Reviewed or Accepted, as legacy disables its page then — here on the server
 *       too.
 * </ul>
 *
 * <p>A status change adds a row to the status history, as legacy's {@code INSERT_INSPECTION_STATUS}
 * — only when it changes.
 */
@Service
public class InspectionEditService {

  private static final Logger log = LoggerFactory.getLogger(InspectionEditService.class);

  private static final String IN_PROGRESS = "PRO";
  private static final String SUBMITTED = "SUB";
  private static final String REJECTED = "REJ";
  private static final String STATUS_FIELD = "statusCode";
  private static final String DATE_FIELD = "inspectionDate";
  private static final String LOCKED = "it can no longer be changed from its page";
  private static final Set<String> EDITABLE_STATUSES = Set.of(IN_PROGRESS, SUBMITTED);
  private static final Map<String, String> LOCKING_STATUSES =
      Map.of("RVD", "Reviewed", "ACC", "Accepted");
  private static final int NAME_MAX = 255;
  private static final int COMMENT_MAX = 4000;
  private static final int HAZARD_COMMENT_MAX = 2000;
  private static final long COST_MAX = 999_999L;
  private static final String YES = "Y";
  /** Legacy's Rejected schedule: the next routine inspection a year out, then yearly. */
  private static final int REJECTED_FREQUENCY_YEARS = 1;

  private final StructureInspectionRepository inspections;
  private final InspectionReportStatusRepository statuses;
  private final StructureInspectionItemRepository items;
  private final CrossingStructureRepository structures;
  private final InspectionFormService forms;
  private final LoggedUserHelper loggedUser;
  private final InspectionAuditRecorder audit;

  public InspectionEditService(StructureInspectionRepository inspections,
      InspectionReportStatusRepository statuses, StructureInspectionItemRepository items,
      CrossingStructureRepository structures, InspectionFormService forms,
      LoggedUserHelper loggedUser, InspectionAuditRecorder audit) {
    this.inspections = inspections;
    this.statuses = statuses;
    this.items = items;
    this.structures = structures;
    this.forms = forms;
    this.loggedUser = loggedUser;
    this.audit = audit;
  }

  /**
   * Saves the Inspection card: status, date, inspector, company, cost and Professional
   * Inspection Required.
   *
   * @throws InspectionNotFoundException if there is no such inspection
   * @throws InspectionLockedException   once it is Reviewed or Accepted
   * @throws FieldValidationException    with a message for each field at fault
   */
  @Transactional
  public void update(long inspectionId, InspectionUpdateRequest request) {
    StructureInspectionEntity inspection = open(inspectionId);
    String current = statusOf(inspection);
    String status = upper(InspectionCreateValidator.trimmed(request.statusCode()));
    String name = InspectionCreateValidator.trimmed(request.inspectorName());
    String company = InspectionCreateValidator.trimmed(request.inspectorCompanyName());

    boolean professional = Boolean.TRUE.equals(request.professionalInspectionRequired());
    validate(inspection, current, new InspectionUpdateRequest(status, request.inspectionDate(),
        name, company, request.cost(), professional));

    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    inspections.save(inspection.toBuilder()
        .inspectionDate(request.inspectionDate())
        .inspectorName(name)
        .inspectorCompanyName(company)
        .inspectionCost(request.cost())
        .pengInspectionReqInd(yesNo(professional))
        .updateUserid(user)
        .updateTimestamp(now)
        .build());
    if (!Objects.equals(status, current)) {
      statuses.save(InspectionReportStatusEntity.builder()
          .inspectionId(inspectionId)
          .inspectionReportStatusCode(status)
          .entryUserid(user)
          .entryTimestamp(now)
          .build());
      if (REJECTED.equals(status)) {
        structures.updateFromRejection(inspection.getCrossingStructureId(),
            now.toLocalDate().plusYears(REJECTED_FREQUENCY_YEARS),
            String.valueOf(REJECTED_FREQUENCY_YEARS), user);
      }
    }
    log.info("Updated inspection {}, status {}", inspectionId, status);
    audit.afterCommit(inspectionId);
  }

  /** Every reason the card cannot be saved, refused together. */
  private void validate(StructureInspectionEntity inspection, String current,
      InspectionUpdateRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    String status = request.statusCode();
    checkStatus(errors, current, status);
    boolean pastInProgress = status != null && !IN_PROGRESS.equals(status);
    required(errors, DATE_FIELD, "Date of Inspection", request.inspectionDate(),
        pastInProgress || inspection.getInspectionDate() != null);
    required(errors, "inspectorName", "Inspector Name", request.inspectorName(),
        pastInProgress || inspection.getInspectorName() != null);
    required(errors, "inspectorCompanyName", "Inspection Company Name",
        request.inspectorCompanyName(),
        pastInProgress || inspection.getInspectorCompanyName() != null);
    checkDate(errors, inspection, request.inspectionDate());
    InspectionCreateValidator.tooLong(errors, "inspectorName", "Inspector Name",
        request.inspectorName(), NAME_MAX);
    InspectionCreateValidator.tooLong(errors, "inspectorCompanyName", "Inspection Company Name",
        request.inspectorCompanyName(), NAME_MAX);
    Long cost = request.cost();
    if (cost != null && (cost < 0 || cost > COST_MAX)) {
      errors.put("cost", "Cost of Inspection must be whole dollars from $0 to $999,999.");
    }
    boolean professional = Boolean.TRUE.equals(request.professionalInspectionRequired());
    boolean storedProfessional = YES.equals(inspection.getPengInspectionReqInd());
    if (professional != storedProfessional && !loggedUser.canDestroy()) {
      errors.put("professionalInspectionRequired",
          "Only Level 2 and up can change Professional Inspection Required.");
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Inspection cannot be saved", errors);
    }
  }

  /**
   * Saves the inspector's comments.
   *
   * @throws InspectionNotFoundException if there is no such inspection
   * @throws InspectionLockedException   once it is Reviewed or Accepted
   * @throws FieldValidationException    when they are longer than 4000
   */
  @Transactional
  public void updateComment(long inspectionId, InspectionCommentRequest request) {
    StructureInspectionEntity inspection = open(inspectionId);
    String comment = InspectionCreateValidator.trimmed(request.comment());
    Map<String, String> errors = new LinkedHashMap<>();
    InspectionCreateValidator.tooLong(errors, "inspectionComment", "Inspection Comments",
        comment, COMMENT_MAX);
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Inspection comments cannot be saved", errors);
    }
    inspections.save(inspection.toBuilder()
        .inspectionComment(comment)
        .updateUserid(loggedUser.getLoggedUserId())
        .updateTimestamp(LocalDateTime.now(ZoneId.systemDefault()))
        .build());
    log.info("Updated the comments of inspection {}", inspectionId);
    audit.afterCommit(inspectionId);
  }

  /**
   * Saves one Condition card — a question group's answers, at each span, pier and stringer — as
   * legacy's update does: an answer kept is updated, a new one added, one no longer sent (or sent
   * blank) taken out. The rules are Add Inspection's: each answer's, and legacy's stringer rules.
   *
   * @throws InspectionNotFoundException if there is no such inspection
   * @throws InspectionLockedException   once it is Reviewed or Accepted
   * @throws FieldValidationException    with a message for each answer at fault, keyed
   *     {@code items.DECK.Q1.span-12}, …
   */
  @Transactional
  public void updateCondition(long inspectionId, String subsectionCode,
      ConditionUpdateRequest request) {
    StructureInspectionEntity inspection = open(inspectionId);
    InspectionFormResponse form = forms.form(inspection.getCrossingStructureId());
    if (form.subsections().stream().noneMatch(group -> group.code().equals(subsectionCode))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "This structure is not asked the question group " + subsectionCode + ".");
    }
    List<Item> sent = (request.items() == null ? List.<Item>of() : request.items()).stream()
        .filter(item -> InspectionCreateValidator.trimmed(item.answer()) != null
            || InspectionCreateValidator.trimmed(item.comment()) != null)
        .toList();
    Map<String, String> errors = new LinkedHashMap<>();
    sent.stream()
        .filter(item -> !subsectionCode.equals(item.subsectionCode()))
        .forEach(item -> errors.put(InspectionCreateValidator.itemKey(item),
            "This answer is not of this question group."));
    errors.putAll(InspectionCreateValidator.itemErrors(sent, form));
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Condition cannot be saved", errors);
    }

    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    Map<String, StructureInspectionItemEntity> stored = new LinkedHashMap<>();
    for (StructureInspectionItemEntity item :
        items.findByInspectionIdAndSubsectionCode(inspectionId, subsectionCode)) {
      stored.put(InspectionCreateValidator.itemKey(itemOf(item)), item);
    }
    for (Item item : sent) {
      StructureInspectionItemEntity kept = stored.remove(InspectionCreateValidator.itemKey(item));
      StructureInspectionItemEntity.StructureInspectionItemEntityBuilder row = kept == null
          ? StructureInspectionItemEntity.builder()
              .inspectionId(inspectionId)
              .subsectionCode(subsectionCode)
              .questionCode(item.questionCode())
              .forestServiceBridgeSpanId(item.spanId())
              .forestServiceBridgePierId(item.pierId())
              .stringerNumber(item.stringerNumber())
              .entryUserid(user)
              .entryTimestamp(now)
          : kept.toBuilder();
      items.save(row
          .answerValue(InspectionCreateValidator.trimmed(item.answer()))
          .itemComment(InspectionCreateValidator.trimmed(item.comment()))
          .updateUserid(user)
          .updateTimestamp(now)
          .build());
    }
    items.deleteAll(stored.values());
    log.info("Updated the {} answers of inspection {}", subsectionCode, inspectionId);
    audit.afterCommit(inspectionId);
  }

  /** A stored answer as the request names one, for its key. */
  private static Item itemOf(StructureInspectionItemEntity item) {
    return new Item(item.getSubsectionCode(), item.getQuestionCode(),
        item.getForestServiceBridgeSpanId(), item.getForestServiceBridgePierId(),
        item.getStringerNumber(), item.getAnswerValue(), item.getItemComment());
  }

  /**
   * Saves the Hydraulic Hazards tab: the three water measurements — metres, to one decimal place,
   * from 0 to 99999.9, and required past In Progress or once given (legacy's "Required field.",
   * which its save ignored) — the five hazards, and their comments, at most 2000. Legacy's range
   * warnings stay warnings, on the page.
   *
   * @throws InspectionNotFoundException if there is no such inspection
   * @throws InspectionLockedException   once it is Reviewed or Accepted
   * @throws FieldValidationException    with a message for each field at fault
   */
  @Transactional
  public void updateHazards(long inspectionId, HazardsUpdateRequest request) {
    StructureInspectionEntity inspection = open(inspectionId);
    validateHazards(request, statusOf(inspection), inspection);
    inspections.save(inspection.toBuilder()
        .channelPwlDepth(request.presentWaterDepth())
        .channelPwlWidth(request.presentWaterWidth())
        .channelHwlClearance(request.highWaterClearance())
        .highWaterHazardInd(yesNo(request.highWater()))
        .debrisHazardInd(yesNo(request.debris()))
        .scourHazardInd(yesNo(request.scour()))
        .iceHazardInd(yesNo(request.ice()))
        .aggradationHazardInd(yesNo(request.aggradation()))
        .hazardComment(InspectionCreateValidator.trimmed(request.comment()))
        .updateUserid(loggedUser.getLoggedUserId())
        .updateTimestamp(LocalDateTime.now(ZoneId.systemDefault()))
        .build());
    log.info("Updated the hydraulic hazards of inspection {}", inspectionId);
    audit.afterCommit(inspectionId);
  }

  private static void validateHazards(HazardsUpdateRequest request, String status,
      StructureInspectionEntity stored) {
    Map<String, String> errors = new LinkedHashMap<>();
    boolean pastInProgress = status != null && !IN_PROGRESS.equals(status);
    measurement(errors, "presentWaterDepth", "Estimated Present Water Level Depth",
        request.presentWaterDepth(), pastInProgress || stored.getChannelPwlDepth() != null);
    measurement(errors, "presentWaterWidth", "Estimated Present Water Level Width",
        request.presentWaterWidth(), pastInProgress || stored.getChannelPwlWidth() != null);
    measurement(errors, "highWaterClearance", "Estimated High Water Level Clearance",
        request.highWaterClearance(),
        pastInProgress || stored.getChannelHwlClearance() != null);
    InspectionCreateValidator.tooLong(errors, "hazardComment", "Comments", request.comment(),
        HAZARD_COMMENT_MAX);
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Hydraulic hazards cannot be saved", errors);
    }
  }

  /** Required when legacy marks it so; otherwise metres, when given. */
  private static void measurement(Map<String, String> errors, String field, String label,
      BigDecimal value, boolean required) {
    if (value == null && required) {
      errors.put(field, label + " is required.");
    } else {
      InspectionCreateValidator.metres(errors, field, label, value);
    }
  }

  private static String yesNo(Boolean value) {
    return Boolean.TRUE.equals(value) ? YES : "N";
  }

  /** The inspection, once it is known to exist and not yet Reviewed or Accepted. */
  private StructureInspectionEntity open(long inspectionId) {
    StructureInspectionEntity inspection = inspections.findById(inspectionId)
        .orElseThrow(() -> new InspectionNotFoundException(inspectionId));
    String status = statusOf(inspection);
    if (status != null && LOCKING_STATUSES.containsKey(status)) {
      throw new InspectionLockedException(inspectionId, LOCKING_STATUSES.get(status), LOCKED);
    }
    return inspection;
  }

  /**
   * In Progress or Submitted, or the status it has; into Rejected an inspection reviewer's, from
   * In Progress or Submitted; out of Rejected a P.Eng's only — legacy's "You do not have
   * permission to change this status."
   */
  private void checkStatus(Map<String, String> errors, String current, String status) {
    if (status == null) {
      errors.put(STATUS_FIELD, "Inspection Report Status is required.");
    } else if (!status.equals(current)) {
      if (REJECTED.equals(status)) {
        if (!loggedUser.isInspectionReviewer()) {
          errors.put(STATUS_FIELD, "Only an inspection reviewer can reject an inspection.");
        } else if (!EDITABLE_STATUSES.contains(current)) {
          errors.put(STATUS_FIELD, "Only an In Progress or Submitted inspection can be rejected.");
        }
      } else if (!EDITABLE_STATUSES.contains(status)) {
        errors.put(STATUS_FIELD,
            "Reviewed is set by an inspection reviewer through the review, not saved here.");
      } else if (REJECTED.equals(current) && !loggedUser.isPeng()) {
        errors.put(STATUS_FIELD, "You do not have permission to change this status.");
      }
    }
  }

  /** Not in the future, and no other inspection of the structure on the day. */
  private void checkDate(Map<String, String> errors, StructureInspectionEntity inspection,
      LocalDate date) {
    if (date == null) {
      return;
    }
    if (date.isAfter(LocalDate.now(ZoneId.systemDefault()))) {
      errors.put(DATE_FIELD, "Date of Inspection must not be after today.");
    } else if (inspections.existsOnDateExcept(inspection.getCrossingStructureId(), date,
        inspection.getInspectionId())) {
      errors.put(DATE_FIELD,
          "Inspection with this date already exists. Please choose another Inspection date.");
    }
  }

  private static void required(Map<String, String> errors, String field, String label,
      Object value, boolean required) {
    if (value == null && required) {
      errors.put(field, label + " is required.");
    }
  }

  private static String statusOf(StructureInspectionEntity inspection) {
    InspectionReportStatusEntity status = inspection.getCurrentStatus();
    return status == null ? null : status.getInspectionReportStatusCode();
  }

  private static String upper(String value) {
    return value == null ? null : value.toUpperCase(Locale.ROOT);
  }
}
