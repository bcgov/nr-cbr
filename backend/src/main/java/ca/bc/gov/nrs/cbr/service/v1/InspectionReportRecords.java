package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;

import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionReviewerRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureLoadRatingRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureMonitorItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairRepository;
import ca.bc.gov.nrs.cbr.service.v1.RepairsSummaryNames.Names;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * What the Inspections Conducted – Detail reads of each inspection beside the inspection itself:
 * its repairs and monitoring items (legacy's {@code CBR_REPAIR_INSPCTN_RPT} and
 * {@code CBR_MONITOR_INSPCTN_RPT} subreports), its current report status and who set it, its last
 * accepted load rating, and its reviewer — a batch of inspections at a time.
 */
@Component
public class InspectionReportRecords {

  /** The statuses after which an inspection's load rating counts: Reviewed, Accepted. */
  private static final Set<String> RATED = Set.of("RVD", "ACC");

  private final StructureRepairRepository repairs;
  private final StructureMonitorItemRepository monitors;
  private final InspectionReportStatusRepository statuses;
  private final StructureLoadRatingRepository loadRatings;
  private final StructureInspectionReviewerRepository reviewers;
  private final RepairsSummaryNames repairNames;

  public InspectionReportRecords(
      StructureRepairRepository repairs,
      StructureMonitorItemRepository monitors,
      InspectionReportStatusRepository statuses,
      StructureLoadRatingRepository loadRatings,
      StructureInspectionReviewerRepository reviewers,
      RepairsSummaryNames repairNames) {
    this.repairs = repairs;
    this.monitors = monitors;
    this.statuses = statuses;
    this.loadRatings = loadRatings;
    this.reviewers = reviewers;
    this.repairNames = repairNames;
  }

  /** The records of these inspections. */
  Records of(List<Long> inspectionIds, Collection<Long> reviewerIds) {
    List<InspectionReportStatusEntity> statusRows =
        fetch(inspectionIds, statuses::findByInspectionIdIn);
    Map<Long, String> reviewerNames = new HashMap<>();
    reviewers.findAllById(reviewerIds.stream().filter(Objects::nonNull).distinct().toList())
        .forEach(reviewer -> reviewerNames.put(reviewer.getInspectionReviewerId(), name(reviewer)));
    return new Records(
        repairRows(fetch(inspectionIds, repairs::findByInspectionIdInOrderByRepairNumberAsc),
            repairNames.names()),
        monitorRows(fetch(inspectionIds, monitors::findByInspectionIdInOrderByMonitorNumberAsc)),
        current(statusRows),
        accepted(fetch(inspectionIds, loadRatings::findByInspectionIdIn), statusRows),
        reviewerNames);
  }

  private static Map<Long, List<Map<String, String>>> repairRows(
      List<StructureRepairEntity> found, Names names) {
    Map<Long, List<Map<String, String>>> rows = new HashMap<>();
    for (StructureRepairEntity repair : found) {
      Map<String, String> row = new HashMap<>();
      row.put("NUMBER", plain(repair.getRepairNumber()));
      row.put("STATUS", names.statuses().get(repair.getRepairStatusCode()));
      row.put("PRIORITY", names.priorities().get(repair.getRepairPriorityCode()));
      row.put("TYPE", names.types().get(repair.getStructureRepairTypeCode()));
      row.put("DESCRIPTION", repair.getDescription());
      rows.computeIfAbsent(repair.getInspectionId(), id -> new ArrayList<>()).add(row);
    }
    return rows;
  }

  private static Map<Long, List<Map<String, String>>> monitorRows(
      List<StructureMonitorItemEntity> found) {
    Map<Long, List<Map<String, String>>> rows = new HashMap<>();
    for (StructureMonitorItemEntity monitor : found) {
      Map<String, String> row = new HashMap<>();
      row.put("NUMBER", plain(monitor.getMonitorNumber()));
      row.put("DESCRIPTION", monitor.getDescription());
      rows.computeIfAbsent(monitor.getInspectionId(), id -> new ArrayList<>()).add(row);
    }
    return rows;
  }

  /** Each inspection's current status: its latest row. */
  private static Map<Long, InspectionReportStatusEntity> current(
      List<InspectionReportStatusEntity> found) {
    Map<Long, InspectionReportStatusEntity> current = new HashMap<>();
    for (InspectionReportStatusEntity status : found) {
      current.merge(status.getInspectionId(), status, (one, other) ->
          one.getInspectionReportStatusId() >= other.getInspectionReportStatusId() ? one : other);
    }
    return current;
  }

  /** Each inspection's latest load rating, once any of its statuses is Reviewed or Accepted. */
  private static Map<Long, BigDecimal> accepted(
      List<StructureLoadRatingEntity> found, List<InspectionReportStatusEntity> statusRows) {
    Set<Long> rated = new HashSet<>();
    for (InspectionReportStatusEntity status : statusRows) {
      if (RATED.contains(status.getInspectionReportStatusCode())) {
        rated.add(status.getInspectionId());
      }
    }
    Map<Long, StructureLoadRatingEntity> latest = new HashMap<>();
    for (StructureLoadRatingEntity rating : found) {
      if (rated.contains(rating.getInspectionId())) {
        latest.merge(rating.getInspectionId(), rating, (one, other) ->
            one.getStructureLoadRatingId() >= other.getStructureLoadRatingId() ? one : other);
      }
    }
    Map<Long, BigDecimal> accepted = new HashMap<>();
    latest.forEach((id, rating) -> accepted.put(id, rating.getLoadRating()));
    return accepted;
  }

  /** What a finder returns for these inspections, a batch at a time. */
  private static <T> List<T> fetch(List<Long> ids, Function<List<Long>, List<T>> finder) {
    List<T> found = new ArrayList<>();
    for (List<Long> batch : batches(ids)) {
      found.addAll(finder.apply(batch));
    }
    return found;
  }

  private static String name(StructureInspectionReviewerEntity reviewer) {
    return String.join(" ", Objects.toString(reviewer.getFirstName(), ""),
        Objects.toString(reviewer.getLastName(), "")).trim();
  }

  private static List<List<Long>> batches(List<Long> ids) {
    List<List<Long>> batches = new ArrayList<>();
    for (int from = 0; from < ids.size(); from += StructureInventoryReportSupport.BATCH) {
      batches.add(ids.subList(from, Math.min(ids.size(), from + StructureInventoryReportSupport.BATCH)));
    }
    return batches;
  }

  /**
   * An inspection's records, each by inspection id.
   *
   * @param loadRatings legacy's {@code FIND_LAST_ACCEPTED_LOAD_RATING}: the inspection's latest
   *                    rating, once any of its statuses has been Reviewed or Accepted
   */
  record Records(
      Map<Long, List<Map<String, String>>> repairs,
      Map<Long, List<Map<String, String>>> monitors,
      Map<Long, InspectionReportStatusEntity> currentStatuses,
      Map<Long, BigDecimal> loadRatings,
      Map<Long, String> reviewers) {

    /** The current status's row — its code, and who set it when. */
    InspectionReportStatusEntity status(Long inspectionId) {
      return currentStatuses.get(inspectionId);
    }
  }
}
