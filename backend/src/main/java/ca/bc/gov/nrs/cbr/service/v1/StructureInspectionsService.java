package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CloseProximityInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.SpecialEquipmentRequirementCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreInspectionTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CloseProximityInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.SpecialEquipmentRequirementCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StrctreInspectionTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCommentRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionReviewerRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CloseProximityInspectionRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionScheduleRequest;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Comment;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionScheduleResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionScheduleResponse.CloseProximityInspection;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionsResponse.Inspection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The structure page's Inspections tab — legacy's {@code inspectionTab.jsp}: its schedule and
 * comments above, then a page of the structure's inspections.
 *
 * <p>The schedule is edited here, and completed close proximity inspections recorded; legacy's Add
 * Routine and Add Unplanned Inspection buttons come later.
 */
@Service
public class StructureInspectionsService {

  private static final Logger log = LoggerFactory.getLogger(StructureInspectionsService.class);
  private static final String YES = "Y";
  /** A planned-inspection comment, as against a general one. */
  private static final String PLANNED_INSPECTION_COMMENT = "Y";
  /** An inspection still out on the offline client — legacy offers no View link while it is. */
  private static final String OFFLINE = "OFL";
  /** Legacy's {@code NVL(YEAR_BUILT, 1900)}: with no year installed, what predates 1900 hides. */
  private static final int NO_YEAR_BUILT = 1900;
  private static final int MAX_PAGE_SIZE = 100;
  /** Reviewed, and its expired predecessor accepted — legacy's {@code getLatestInspection}. */
  private static final List<String> REVIEWED_STATUSES = List.of("RVD", "ACC");
  private static final int MIN_FREQUENCY = 1;
  private static final int MAX_FREQUENCY = 6;
  private static final String NO = "N";

  private final CrossingStructureRepository structures;
  private final StructureCommentRepository comments;
  private final CloseProximityInspectionRepository closeProximity;
  private final SpecialEquipmentRequirementCodeRepository equipment;
  private final StructureInspectionRepository inspections;
  private final StrctreInspectionTypeCodeRepository inspectionTypes;
  private final StructureInspectionReviewerRepository reviewers;
  private final LoggedUserHelper loggedUser;

  public StructureInspectionsService(
      CrossingStructureRepository structures,
      StructureCommentRepository comments,
      CloseProximityInspectionRepository closeProximity,
      SpecialEquipmentRequirementCodeRepository equipment,
      StructureInspectionRepository inspections,
      StrctreInspectionTypeCodeRepository inspectionTypes,
      StructureInspectionReviewerRepository reviewers,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.comments = comments;
    this.closeProximity = closeProximity;
    this.equipment = equipment;
    this.inspections = inspections;
    this.inspectionTypes = inspectionTypes;
    this.reviewers = reviewers;
    this.loggedUser = loggedUser;
  }

  /**
   * What sits above the inspection table. The special equipment and next close proximity date are
   * sent whatever the indicator says; the page shows them only when one is required, as legacy
   * does.
   *
   * @throws StructureNotFoundException if there is no such structure
   */
  @Transactional(readOnly = true)
  public StructureInspectionScheduleResponse schedule(long structureId) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));

    List<Comment> planned = comments.findByKind(structureId, PLANNED_INSPECTION_COMMENT).stream()
        .map(comment -> new Comment(
            String.valueOf(comment.getStructureCommentId()),
            comment.getStructureComment(),
            comment.getUpdateUserid(),
            comment.getUpdateTimestamp()))
        .toList();
    List<CloseProximityInspection> completed = closeProximity
        .findByCrossingStructureIdOrderByCompletionDateDescCloseProximityInspectionIdDesc(
            structureId)
        .stream()
        .map(done -> new CloseProximityInspection(
            String.valueOf(done.getCloseProximityInspectionId()),
            done.getCompletionDate(),
            done.getEntryUserid()))
        .toList();

    return new StructureInspectionScheduleResponse(
        planned,
        YES.equals(structure.getCloseProximityInd()),
        equipment(structure.getSpecialEquipmentRqmtCode()),
        structure.getNextPlannedClsProxInspDt(),
        structure.getNextPlannedInspectionDate(),
        frequency(structure.getRoutineInspectionFrequency()),
        completed,
        inspections.findLatestReviewedInspectionDate(structureId, REVIEWED_STATUSES)
            .orElse(null));
  }

  /**
   * Saves the structure's inspection schedule — legacy's fields above the inspection table, saved
   * there with the whole page and here on their own.
   *
   * <p>As legacy, by role: Level 2 and up set close proximity, the next planned routine inspection
   * and the frequency; Level 1 sets the frequency alone, the rest kept as stored. A new frequency
   * moves a Level 1 user's next planned routine inspection to the latest reviewed or accepted
   * inspection plus that many years, as legacy's page did on changing it; with no such inspection
   * the date stays and the frequency is still saved (legacy lost it). A Level 2 user's date is
   * taken as sent — the page does the same arithmetic, and they may change its answer.
   *
   * <p>While no close proximity inspection is required, its equipment and date are kept as stored,
   * as legacy kept them in its hidden fields. The next planned routine inspection is required once
   * it has a value, as legacy's; the frequency always, from 1 to 6.
   *
   * @throws StructureNotFoundException if there is no such structure
   * @throws FieldValidationException with a message for each field at fault
   */
  @Transactional
  public void updateSchedule(long structureId, InspectionScheduleRequest request) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    Map<String, String> errors = new LinkedHashMap<>();
    Integer frequency = request.routineFrequencyYears();
    if (frequency == null) {
      errors.put("routineFrequencyYears", "Routine Inspection Frequency is required.");
    } else if (frequency < MIN_FREQUENCY || frequency > MAX_FREQUENCY) {
      errors.put("routineFrequencyYears", "Routine Inspection Frequency must be from 1 to 6.");
    }

    boolean required;
    String equipmentCode = structure.getSpecialEquipmentRqmtCode();
    LocalDate nextCloseProximity = structure.getNextPlannedClsProxInspDt();
    LocalDate nextRoutine;
    if (loggedUser.canDestroy()) {
      required = request.closeProximityRequired();
      if (required) {
        equipmentCode = trimmed(request.closeProximityEquipmentCode());
        nextCloseProximity = request.nextCloseProximityDate();
      }
      if (required && equipmentCode != null && !equipment.existsById(equipmentCode)) {
        errors.put("closeProximityEquipmentCode", "Close Proximity Special Equipment "
            + "Requirements is not one of the listed requirements.");
      }
      nextRoutine = request.nextRoutineDate();
      if (nextRoutine == null && structure.getNextPlannedInspectionDate() != null) {
        errors.put("nextRoutineDate", "Next Planned Routine Inspection is required.");
      }
    } else {
      required = YES.equals(structure.getCloseProximityInd());
      nextRoutine = recalculated(structure, frequency);
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Inspection schedule cannot be saved", errors);
    }

    structures.updateInspectionSchedule(structureId, required ? YES : NO, equipmentCode,
        nextCloseProximity, nextRoutine, String.valueOf(frequency),
        loggedUser.getLoggedUserId());
    log.info("Updated the inspection schedule of structure {}", structureId);
  }

  /**
   * Records a completed close proximity inspection — legacy's P.Eng add on the Inspections tab
   * ({@code addCloseProximityInspectionDate}, {@code CBR.INSERT_CLOSE_PROX_INSP}): the date, the
   * site the structure stands on now, and who recorded it and when; its id from
   * {@code CLOSE_PROXIMITY_INSPECTION_SEQ} through the entity's generator. Nothing else changes —
   * the next planned close proximity inspection is the schedule's, as legacy leaves it.
   *
   * <p>The date is required, where legacy saved a row without one when it could not read it.
   *
   * @return the new row's id
   * @throws StructureNotFoundException if there is no such structure
   * @throws FieldValidationException   when there is no date
   * @throws ResponseStatusException    409 when the structure stands on no site, which the row
   *     needs
   */
  @Transactional
  public String addCloseProximityInspection(long structureId,
      CloseProximityInspectionRequest request) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    if (request.completedDate() == null) {
      throw new FieldValidationException("Close proximity inspection cannot be saved",
          Map.of("completedDate", "Date is required."));
    }
    if (structure.getCrossingSiteId() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Structure " + structureId
          + " stands on no site, so a close proximity inspection cannot be recorded for it.");
    }
    CloseProximityInspectionEntity saved = closeProximity.save(
        CloseProximityInspectionEntity.builder()
            .crossingStructureId(structureId)
            .crossingSiteId(structure.getCrossingSiteId())
            .completionDate(request.completedDate())
            .entryUserid(loggedUser.getLoggedUserId())
            .entryTimestamp(LocalDateTime.now(ZoneId.systemDefault()))
            .build());
    log.info("Recorded close proximity inspection {} of structure {} on {}",
        saved.getCloseProximityInspectionId(), structureId, request.completedDate());
    return String.valueOf(saved.getCloseProximityInspectionId());
  }

  /**
   * The next planned routine inspection after a Level 1 user's frequency change: the latest
   * reviewed or accepted inspection plus the new frequency, or the stored date when the frequency
   * is unchanged or there is no such inspection.
   */
  private LocalDate recalculated(CrossingStructureEntity structure, Integer frequency) {
    LocalDate stored = structure.getNextPlannedInspectionDate();
    Integer storedFrequency = frequency(structure.getRoutineInspectionFrequency());
    if (frequency == null || frequency.equals(storedFrequency)) {
      return stored;
    }
    return inspections
        .findLatestReviewedInspectionDate(structure.getCrossingStructureId(), REVIEWED_STATUSES)
        .map(latest -> latest.plusYears(frequency))
        .orElse(stored);
  }

  /** The value without surrounding spaces, or null when nothing is left. */
  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /**
   * A page of the structure's inspections, newest first. Without {@code includeBeforeInstall},
   * only those dated after 1 January of the year the superstructure was installed — legacy's
   * default table; with it, every one.
   *
   * @param pageNumber zero-based
   * @param pageSize   held to between 1 and 100
   * @throws StructureNotFoundException if there is no such structure
   */
  @Transactional(readOnly = true)
  public StructureInspectionsResponse inspections(
      long structureId, int pageNumber, int pageSize, boolean includeBeforeInstall) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    LocalDate installed = LocalDate.of(
        Objects.requireNonNullElse(structure.getYearBuilt(), NO_YEAR_BUILT), 1, 1);
    int size = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);

    Page<StructureInspectionEntity> page = inspections.findPageByStructure(
        structureId, installed, includeBeforeInstall,
        PageRequest.of(Math.max(pageNumber, 0), size));
    Map<String, String> types = typeDescriptions(page.getContent());
    Map<Long, String> reviewerNames = reviewerNames(page.getContent());

    List<Inspection> rows = page.getContent().stream()
        .map(inspection -> inspection(inspection, types, reviewerNames))
        .toList();
    return new StructureInspectionsResponse(
        new PagedResponse<>(rows, page.getTotalElements(), page.getTotalPages(),
            page.getNumber(), page.getSize()),
        inspections.countBeforeInstall(structureId, installed));
  }

  private static Inspection inspection(StructureInspectionEntity inspection,
      Map<String, String> types, Map<Long, String> reviewerNames) {
    InspectionReportStatusEntity status = inspection.getCurrentStatus();
    String statusCode = status == null ? null : status.getInspectionReportStatusCode();
    String type = inspection.getStrctreInspectionTypeCode();
    return new Inspection(
        String.valueOf(inspection.getInspectionId()),
        type == null ? CodeValue.NONE : new CodeValue(type, types.get(type)),
        inspection.getInspectionDate(),
        inspection.getSiteAtTimeOfInspection(),
        status(status),
        inspection.getPengReviewerDate() == null
            ? null
            : inspection.getPengReviewerDate().toLocalDate(),
        reviewerNames.get(inspection.getInspectionReviewerId()),
        inspection.getInspectorName(),
        !OFFLINE.equals(statusCode));
  }

  /** The inspection's current status, decoded; none when it has no status history. */
  private static CodeValue status(InspectionReportStatusEntity status) {
    if (status == null || status.getInspectionReportStatusCode() == null) {
      return CodeValue.NONE;
    }
    String description =
        status.getStatusCode() == null ? null : status.getStatusCode().getDescription();
    return new CodeValue(status.getInspectionReportStatusCode(), description);
  }

  private Map<String, String> typeDescriptions(Collection<StructureInspectionEntity> page) {
    return inspectionTypes
        .findAllById(page.stream()
            .map(StructureInspectionEntity::getStrctreInspectionTypeCode)
            .filter(Objects::nonNull)
            .distinct()
            .toList())
        .stream()
        .collect(Collectors.toMap(
            StrctreInspectionTypeCodeEntity::getStrctreInspectionTypeCode,
            StrctreInspectionTypeCodeEntity::getDescription));
  }

  private Map<Long, String> reviewerNames(Collection<StructureInspectionEntity> page) {
    return reviewers
        .findAllById(page.stream()
            .map(StructureInspectionEntity::getInspectionReviewerId)
            .filter(Objects::nonNull)
            .distinct()
            .toList())
        .stream()
        .collect(Collectors.toMap(
            StructureInspectionReviewerEntity::getInspectionReviewerId,
            reviewer -> String.join(" ", Objects.toString(reviewer.getFirstName(), ""),
                Objects.toString(reviewer.getLastName(), "")).trim()));
  }

  private CodeValue equipment(String code) {
    if (code == null || code.isBlank()) {
      return CodeValue.NONE;
    }
    return new CodeValue(code, equipment.findById(code)
        .map(SpecialEquipmentRequirementCodeEntity::getDescription)
        .orElse(null));
  }

  /** The stored frequency as a number of years, or null when it is not one. */
  private static Integer frequency(String stored) {
    if (stored == null || stored.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(stored.trim());
    } catch (NumberFormatException notANumber) {
      return null;
    }
  }
}
