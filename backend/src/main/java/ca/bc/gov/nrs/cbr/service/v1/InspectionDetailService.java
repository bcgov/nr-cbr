package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreInspectionTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreLoadRatingRsnCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StrctreInspectionTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StrctreLoadRatingRsnCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionReviewerRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureLoadRatingRepository;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionDetailResponse.Hazards;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionDetailResponse.Item;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionDetailResponse.Review;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionDetailResponse.StatusChange;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.UserAudit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One inspection, read — what its page shows (legacy's {@code showInspection.do} in UPDATE mode):
 * the inspection, its status history, its review and load rating, its hazards and its answers.
 */
@Service
public class InspectionDetailService {

  private static final String YES = "Y";
  private static final String OFFLINE = "OFL";

  private final StructureInspectionRepository inspections;
  private final StructureInspectionItemRepository items;
  private final InspectionReportStatusRepository statuses;
  private final StrctreInspectionTypeCodeRepository types;
  private final StructureInspectionReviewerRepository reviewers;
  private final StructureLoadRatingRepository loadRatings;
  private final StrctreLoadRatingRsnCodeRepository reasons;

  public InspectionDetailService(StructureInspectionRepository inspections,
      StructureInspectionItemRepository items, InspectionReportStatusRepository statuses,
      StrctreInspectionTypeCodeRepository types, StructureInspectionReviewerRepository reviewers,
      StructureLoadRatingRepository loadRatings, StrctreLoadRatingRsnCodeRepository reasons) {
    this.inspections = inspections;
    this.items = items;
    this.statuses = statuses;
    this.types = types;
    this.reviewers = reviewers;
    this.loadRatings = loadRatings;
    this.reasons = reasons;
  }

  /**
   * The inspection with this id.
   *
   * @throws InspectionNotFoundException if there is none
   */
  @Transactional(readOnly = true)
  public InspectionDetailResponse findById(long inspectionId) {
    StructureInspectionEntity inspection = inspections.findById(inspectionId)
        .orElseThrow(() -> new InspectionNotFoundException(inspectionId));
    List<StatusChange> history =
        statuses.findByInspectionIdOrderByInspectionReportStatusIdDesc(inspectionId).stream()
            .map(row -> new StatusChange(status(row), row.getEntryUserid(),
                row.getEntryTimestamp()))
            .toList();
    CodeValue current = history.isEmpty() ? CodeValue.NONE : history.getFirst().status();
    return new InspectionDetailResponse(
        String.valueOf(inspection.getInspectionId()),
        string(inspection.getCrossingStructureId()),
        inspection.getStructure() == null ? null : inspection.getStructure().getCrossingStructureName(),
        inspection.getSiteAtTimeOfInspection(),
        type(inspection.getStrctreInspectionTypeCode()),
        current,
        history,
        inspection.getInspectionDate(),
        inspection.getInspectorName(),
        inspection.getInspectorCompanyName(),
        inspection.getInspectionCost(),
        YES.equals(inspection.getPengInspectionReqInd()),
        inspection.getInspectionComment(),
        review(inspection),
        hazards(inspection),
        answers(inspectionId),
        UserAudit.of(inspection.getEntryUserid(), inspection.getEntryTimestamp()),
        UserAudit.of(inspection.getUpdateUserid(), inspection.getUpdateTimestamp()),
        OFFLINE.equals(current.code()));
  }

  private Review review(StructureInspectionEntity inspection) {
    String reviewer = Optional.ofNullable(inspection.getInspectionReviewerId())
        .flatMap(reviewers::findById)
        .map(InspectionDetailService::name)
        .orElse(null);
    // Legacy keeps one load rating per inspection; the latest, should there be more.
    Optional<StructureLoadRatingEntity> rating =
        loadRatings.findByInspectionIdIn(List.of(inspection.getInspectionId())).stream()
            .max(Comparator.comparing(StructureLoadRatingEntity::getStructureLoadRatingId));
    return new Review(
        reviewer,
        inspection.getPengReviewerDate() == null
            ? null
            : inspection.getPengReviewerDate().toLocalDate(),
        rating.map(StructureLoadRatingEntity::getLoadRating).orElse(null),
        rating.map(StructureLoadRatingEntity::getReasonCode).map(this::reason)
            .orElse(CodeValue.NONE),
        rating.map(StructureLoadRatingEntity::getReasonComment).orElse(null),
        inspection.getPengComment());
  }

  private static Hazards hazards(StructureInspectionEntity inspection) {
    return new Hazards(
        inspection.getChannelPwlDepth(),
        inspection.getChannelPwlWidth(),
        inspection.getChannelHwlClearance(),
        YES.equals(inspection.getHighWaterHazardInd()),
        YES.equals(inspection.getDebrisHazardInd()),
        YES.equals(inspection.getScourHazardInd()),
        YES.equals(inspection.getIceHazardInd()),
        YES.equals(inspection.getAggradationHazardInd()),
        inspection.getHazardComment());
  }

  private List<Item> answers(long inspectionId) {
    return items.findByInspectionId(inspectionId).stream()
        .sorted(Comparator.comparing(StructureInspectionItemEntity::getStructureInspectionItemId))
        .map(item -> new Item(
            item.getSubsectionCode(),
            item.getQuestionCode(),
            string(item.getForestServiceBridgeSpanId()),
            string(item.getForestServiceBridgePierId()),
            item.getStringerNumber(),
            item.getAnswerValue(),
            item.getItemComment()))
        .toList();
  }

  private CodeValue type(String code) {
    if (code == null) {
      return CodeValue.NONE;
    }
    return new CodeValue(code, types.findById(code)
        .map(StrctreInspectionTypeCodeEntity::getDescription).orElse(null));
  }

  private CodeValue reason(String code) {
    return new CodeValue(code, reasons.findById(code)
        .map(StrctreLoadRatingRsnCodeEntity::getDescription).orElse(null));
  }

  private static CodeValue status(InspectionReportStatusEntity row) {
    String description =
        row.getStatusCode() == null ? null : row.getStatusCode().getDescription();
    return new CodeValue(row.getInspectionReportStatusCode(), description);
  }

  private static String name(StructureInspectionReviewerEntity reviewer) {
    return String.join(" ", Objects.toString(reviewer.getFirstName(), ""),
        Objects.toString(reviewer.getLastName(), "")).trim();
  }

  private static String string(Long id) {
    return id == null ? null : String.valueOf(id);
  }
}
