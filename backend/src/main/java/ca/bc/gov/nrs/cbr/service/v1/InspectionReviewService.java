package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.InspectionLockedException;
import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.LoadRatingReasonXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusRepository;
import ca.bc.gov.nrs.cbr.repository.v1.LoadRatingReasonXrefRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StrctreLoadRatingRsnCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionReviewerRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureLoadRatingRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureMonitorItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCommentRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionReviewRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * A P.Eng's part of an inspection: the review — legacy's Accept dialog, which marks it Reviewed —
 * and the PEng Comments.
 *
 * <p><b>The review</b> writes what legacy's save does on reaching {@code RVD} ({@code
 * InspectionAction} save, {@code CBR.UPDATE_INSPECTION}, {@code INSERT_INSPECTION_STATUS}):
 *
 * <ul>
 *   <li>the inspection: Reviewed in its status history, bound to the reviewer and dated now;
 *   <li>its load rating — a {@code STRUCTURE_LOAD_RATING} row of the inspection, added or changed —
 *       and the structure's current rating copied onto it again;
 *   <li>the structure: its routine frequency, Load Rating Review Required as the dialog leaves it,
 *       and for a routine inspection the next planned routine inspection;
 *   <li>every suggested repair and monitoring item of the structure made required.
 * </ul>
 *
 * <p>Where legacy checked only in the browser — a rating, a reason that fits, Other's words — it is
 * checked here too. Legacy's report to TRIM on review is not sent.
 *
 * <p>The reviewer is the P.Eng saving it: their active {@code STRUCTURE_INSPECTION_REVIEWER} row,
 * without which the review is refused ({@code cbr-inspection-reviewer.local.md} §4).
 */
@Service
public class InspectionReviewService {

  private static final Logger log = LoggerFactory.getLogger(InspectionReviewService.class);

  static final String REVIEWED = "RVD";
  private static final String ROUTINE = "ROUT";
  private static final String IN_PROGRESS = "PRO";
  private static final Map<String, String> LOCKING_STATUSES =
      Map.of(REVIEWED, "Reviewed", "ACC", "Accepted");
  private static final String LOCKED = "it can no longer be changed from its page";
  private static final String OTHER_REASON = "OTHR";
  /** Legacy's historical reason, never offered. */
  private static final String HISTORICAL_REASON = "HIST";
  private static final String ANY_CHANGE = "UNDEF";
  private static final long RATING_MAX = 999_999L;
  private static final int TEXT_MAX = 4000;
  private static final String YES = "Y";

  private static final String RATING = "loadRating";
  private static final String REASON = "reasonCode";
  private static final String REASON_COMMENT = "reasonComment";

  private final StructureInspectionRepository inspections;
  private final InspectionReportStatusRepository statuses;
  private final StructureInspectionReviewerRepository reviewers;
  private final StructureLoadRatingRepository loadRatings;
  private final StrctreLoadRatingRsnCodeRepository reasons;
  private final LoadRatingReasonXrefRepository reasonDeltas;
  private final CrossingStructureRepository structures;
  private final StructureRepairRepository repairs;
  private final StructureMonitorItemRepository monitors;
  private final LoadRatingService current;
  private final LoggedUserHelper loggedUser;

  @SuppressWarnings("java:S107") // One repository per table the review reads or writes.
  public InspectionReviewService(
      StructureInspectionRepository inspections,
      InspectionReportStatusRepository statuses,
      StructureInspectionReviewerRepository reviewers,
      StructureLoadRatingRepository loadRatings,
      StrctreLoadRatingRsnCodeRepository reasons,
      LoadRatingReasonXrefRepository reasonDeltas,
      CrossingStructureRepository structures,
      StructureRepairRepository repairs,
      StructureMonitorItemRepository monitors,
      LoadRatingService current,
      LoggedUserHelper loggedUser) {
    this.inspections = inspections;
    this.statuses = statuses;
    this.reviewers = reviewers;
    this.loadRatings = loadRatings;
    this.reasons = reasons;
    this.reasonDeltas = reasonDeltas;
    this.structures = structures;
    this.repairs = repairs;
    this.monitors = monitors;
    this.current = current;
    this.loggedUser = loggedUser;
  }

  /**
   * Reviews the inspection — legacy's Accept.
   *
   * @throws InspectionNotFoundException if there is no such inspection
   * @throws InspectionLockedException   once it is Reviewed or Accepted
   * @throws ResponseStatusException     403 when the user is not an active inspection reviewer
   * @throws FieldValidationException    with a message for each field at fault
   */
  @Transactional
  public void review(long inspectionId, InspectionReviewRequest request) {
    StructureInspectionEntity inspection = open(inspectionId);
    String user = loggedUser.getLoggedUserId();
    StructureInspectionReviewerEntity reviewer = reviewers
        .findFirstByUseridIgnoreCaseAndActiveInd(user, YES)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
            "You are not listed as an inspection reviewer, so you cannot review inspections."));
    Long structureId = inspection.getCrossingStructureId();
    boolean routine = ROUTINE.equals(inspection.getStrctreInspectionTypeCode());
    validate(inspection, request, routine);

    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    String reason = trimmed(request.reasonCode());
    String reasonComment = OTHER_REASON.equals(reason) ? trimmed(request.reasonComment()) : null;
    StructureLoadRatingEntity rating = loadRatings
        .findByInspectionIdIn(List.of(inspectionId)).stream()
        .findFirst()
        .map(StructureLoadRatingEntity::toBuilder)
        .orElseGet(() -> StructureLoadRatingEntity.builder()
            .crossingStructureId(structureId)
            .inspectionId(inspectionId)
            .siteAtTimeOfLoadRating(inspection.getSiteAtTimeOfInspection())
            .entryUserid(user)
            .entryTimestamp(now))
        .loadRating(BigDecimal.valueOf(request.loadRating()))
        .reasonCode(reason)
        .reasonComment(reasonComment)
        .updateUserid(user)
        .updateTimestamp(now)
        .build();
    loadRatings.save(rating);

    inspections.save(inspection.toBuilder()
        .inspectionReviewerId(reviewer.getInspectionReviewerId())
        .pengReviewerDate(now)
        .updateUserid(user)
        .updateTimestamp(now)
        .build());
    statuses.save(InspectionReportStatusEntity.builder()
        .inspectionId(inspectionId)
        .inspectionReportStatusCode(REVIEWED)
        .entryUserid(user)
        .entryTimestamp(now)
        .build());
    inspections.flush();
    loadRatings.flush();

    // Now Reviewed, the inspection's rating may be the structure's current one.
    structures.updateCurrentLoadRating(structureId, current.currentLoadRating(structureId)
        .map(StructureLoadRatingEntity::getLoadRating)
        .orElse(null), null);
    structures.updateFromReview(structureId,
        routine ? request.nextRoutineDate() : null,
        String.valueOf(request.routineFrequencyYears()),
        request.loadRatingReviewRequired() ? YES : "N",
        user);
    repairs.requireSuggested(structureId, user);
    monitors.requireSuggested(structureId, user);
    log.info("Inspection {} reviewed by reviewer {}", inspectionId,
        reviewer.getInspectionReviewerId());
  }

  /**
   * Saves the PEng Comments.
   *
   * @throws InspectionNotFoundException if there is no such inspection
   * @throws InspectionLockedException   once it is Reviewed or Accepted
   * @throws FieldValidationException    when they are longer than 4000
   */
  @Transactional
  public void updatePengComment(long inspectionId, InspectionCommentRequest request) {
    StructureInspectionEntity inspection = open(inspectionId);
    String comment = trimmed(request.comment());
    Map<String, String> errors = new LinkedHashMap<>();
    InspectionCreateValidator.tooLong(errors, "pengComment", "PEng Comments", comment, TEXT_MAX);
    if (!errors.isEmpty()) {
      throw new FieldValidationException("PEng comments cannot be saved", errors);
    }
    inspections.save(inspection.toBuilder()
        .pengComment(comment)
        .updateUserid(loggedUser.getLoggedUserId())
        .updateTimestamp(LocalDateTime.now(ZoneId.systemDefault()))
        .build());
    log.info("Updated the PEng comments of inspection {}", inspectionId);
  }

  /**
   * Every reason the review cannot be saved, refused together: the inspection's own required
   * fields, as past In Progress; a rating; a reason fitting its change from the structure's current
   * rating, when any fits; Other's words; and the schedule.
   */
  private void validate(StructureInspectionEntity inspection, InspectionReviewRequest request,
      boolean routine) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (inspection.getInspectionDate() == null) {
      errors.put("inspectionDate", "Date of Inspection is required.");
    }
    if (inspection.getInspectorName() == null) {
      errors.put("inspectorName", "Inspector Name is required.");
    }
    if (inspection.getInspectorCompanyName() == null) {
      errors.put("inspectorCompanyName", "Inspection Company Name is required.");
    }

    Long rating = request.loadRating();
    if (rating == null) {
      errors.put(RATING, "Current Load Rating is required.");
    } else if (rating < 0 || rating > RATING_MAX) {
      errors.put(RATING, "Current Load Rating must be a whole number from 0 to " + RATING_MAX + ".");
    } else {
      validateReason(errors, request, inspection.getCrossingStructureId(), rating);
    }

    if (routine && request.nextRoutineDate() == null) {
      errors.put("nextRoutineDate", "Next Planned Inspection Date is required.");
    }
    Integer frequency = request.routineFrequencyYears();
    if (frequency == null || frequency < 1 || frequency > 6) {
      errors.put("routineFrequencyYears",
          "Routine Inspection Frequency must be from 1 to 6 years.");
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Inspection cannot be reviewed", errors);
    }
  }

  /** A listed reason that fits the change, when any does — legacy's dialog's list — and Other's words. */
  private void validateReason(Map<String, String> errors, InspectionReviewRequest request,
      Long structureId, long rating) {
    Set<String> fitting = fittingReasons(deltaOf(rating, structureId));
    String reason = trimmed(request.reasonCode());
    if (reason == null) {
      if (!fitting.isEmpty()) {
        errors.put(REASON, "Load Rating Reason is required.");
      }
      return;
    }
    if (!fitting.contains(reason)) {
      errors.put(REASON, "Load Rating Reason does not fit this change of load rating.");
    } else if (OTHER_REASON.equals(reason)) {
      String comment = trimmed(request.reasonComment());
      if (comment == null) {
        errors.put(REASON_COMMENT, "Load Rating Reason Comment is required for Other.");
      } else {
        InspectionCreateValidator.tooLong(errors, REASON_COMMENT, "Load Rating Reason Comment",
            comment, TEXT_MAX);
      }
    }
  }

  /** Legacy's grouping of the rating against the structure's current one: INC, DEC or NOC. */
  private String deltaOf(long rating, Long structureId) {
    long now = current.currentLoadRating(structureId)
        .map(StructureLoadRatingEntity::getLoadRating)
        .map(BigDecimal::longValue)
        .orElse(0L);
    if (rating > now) {
      return "INC";
    }
    return rating < now ? "DEC" : "NOC";
  }

  /** The current reasons offered for the change, or for any — legacy's, less HIST. */
  private Set<String> fittingReasons(String delta) {
    Set<String> listed = reasons.findAllCurrent().stream()
        .map(code -> code.getStrctreLoadRatingRsnCode())
        .collect(Collectors.toSet());
    return reasonDeltas.findAll().stream()
        .filter(row -> delta.equals(row.getDeltaCode()) || ANY_CHANGE.equals(row.getDeltaCode()))
        .map(LoadRatingReasonXrefEntity::getReasonCode)
        .filter(listed::contains)
        .filter(code -> !HISTORICAL_REASON.equals(code))
        .collect(Collectors.toSet());
  }

  /** The inspection, once it is known to exist and not yet Reviewed or Accepted. */
  private StructureInspectionEntity open(long inspectionId) {
    StructureInspectionEntity inspection = inspections.findById(inspectionId)
        .orElseThrow(() -> new InspectionNotFoundException(inspectionId));
    InspectionReportStatusEntity status = inspection.getCurrentStatus();
    String code = status == null ? IN_PROGRESS : status.getInspectionReportStatusCode();
    if (LOCKING_STATUSES.containsKey(code)) {
      throw new InspectionLockedException(inspectionId, LOCKING_STATUSES.get(code), LOCKED);
    }
    return inspection;
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
