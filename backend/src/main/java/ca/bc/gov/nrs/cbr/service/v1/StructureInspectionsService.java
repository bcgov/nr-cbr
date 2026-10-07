package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
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
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Comment;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionScheduleResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionScheduleResponse.CloseProximityInspection;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionsResponse.Inspection;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The structure page's Inspections tab — legacy's {@code inspectionTab.jsp}: its schedule and
 * comments above, then a page of the structure's inspections.
 *
 * <p>Read-only for now; legacy's Add Routine and Add Unplanned Inspection buttons, the
 * planned-inspection comment Add and the completed close proximity Add come with the page's
 * editing.
 */
@Service
public class StructureInspectionsService {

  private static final String YES = "Y";
  /** A planned-inspection comment, as against a general one. */
  private static final String PLANNED_INSPECTION_COMMENT = "Y";
  /** An inspection still out on the offline client — legacy offers no View link while it is. */
  private static final String OFFLINE = "OFL";
  /** Legacy's {@code NVL(YEAR_BUILT, 1900)}: with no year installed, what predates 1900 hides. */
  private static final int NO_YEAR_BUILT = 1900;
  private static final int MAX_PAGE_SIZE = 100;

  private final CrossingStructureRepository structures;
  private final StructureCommentRepository comments;
  private final CloseProximityInspectionRepository closeProximity;
  private final SpecialEquipmentRequirementCodeRepository equipment;
  private final StructureInspectionRepository inspections;
  private final StrctreInspectionTypeCodeRepository inspectionTypes;
  private final StructureInspectionReviewerRepository reviewers;

  public StructureInspectionsService(
      CrossingStructureRepository structures,
      StructureCommentRepository comments,
      CloseProximityInspectionRepository closeProximity,
      SpecialEquipmentRequirementCodeRepository equipment,
      StructureInspectionRepository inspections,
      StrctreInspectionTypeCodeRepository inspectionTypes,
      StructureInspectionReviewerRepository reviewers) {
    this.structures = structures;
    this.comments = comments;
    this.closeProximity = closeProximity;
    this.equipment = equipment;
    this.inspections = inspections;
    this.inspectionTypes = inspectionTypes;
    this.reviewers = reviewers;
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
        completed);
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
