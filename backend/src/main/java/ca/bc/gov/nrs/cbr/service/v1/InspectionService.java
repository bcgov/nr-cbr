package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.exception.InspectionNotOfflineException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileDetailRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureLoadRatingRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureMonitorItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations on an inspection itself, as opposed to searching for one.
 *
 * <p>Deleting is all there is so far; creating, editing and the status transitions arrive with the
 * inspection screens — and they are gated differently again, on
 * {@link ca.bc.gov.nrs.cbr.security.CbrAuthorities#INSPECTION_WRITE} and
 * {@link ca.bc.gov.nrs.cbr.security.CbrAuthorities#APPROVE}.
 */
@Service
public class InspectionService {

  private static final Logger log = LoggerFactory.getLogger(InspectionService.class);

  /** The status an inspection holds while it is checked out to a field device. */
  private static final String OFFLINE = "OFL";

  private final StructureInspectionRepository inspections;
  private final CrossingStructureRepository structures;
  private final CrossingStructureFileRepository attachmentBytes;
  private final CrossingStructureFileDetailRepository attachments;
  private final StructureMonitorItemRepository monitorItems;
  private final StructureRepairRepository repairs;
  private final StructureInspectionItemRepository inspectionItems;
  private final StructureLoadRatingRepository loadRatings;
  private final InspectionReportStatusRepository statusHistory;
  private final LoadRatingService loadRatingService;

  public InspectionService(
      StructureInspectionRepository inspections,
      CrossingStructureRepository structures,
      CrossingStructureFileRepository attachmentBytes,
      CrossingStructureFileDetailRepository attachments,
      StructureMonitorItemRepository monitorItems,
      StructureRepairRepository repairs,
      StructureInspectionItemRepository inspectionItems,
      StructureLoadRatingRepository loadRatings,
      InspectionReportStatusRepository statusHistory,
      LoadRatingService loadRatingService) {
    this.inspections = inspections;
    this.structures = structures;
    this.attachmentBytes = attachmentBytes;
    this.attachments = attachments;
    this.monitorItems = monitorItems;
    this.repairs = repairs;
    this.inspectionItems = inspectionItems;
    this.loadRatings = loadRatings;
    this.statusHistory = statusHistory;
    this.loadRatingService = loadRatingService;
  }

  /**
   * Deletes an offline inspection and everything hanging off it.
   *
   * <p><b>A hard, cascading delete, and it is not recoverable.</b> The inspection's own status
   * history goes with it, so afterwards there is no record it ever existed.
   *
   * <h3>Two guards, and only one of them is legacy's</h3>
   * <ul>
   *   <li><b>The inspection must exist</b> — a 404 otherwise. The delete is reached from a results
   *       table that may have been on screen for some time, so another user having dealt with the
   *       same offline inspection is an ordinary case.</li>
   *   <li><b>The inspection must be offline.</b> <b>Legacy does not check this at all.</b> The rule
   *       exists — {@code inspection_search.jsp:339} renders the delete control only on an
   *       {@code OFL} row — but it lives entirely in that JSP's {@code <c:if>}.
   *       {@code InspectionAction.delete()} validates that the id is a number and then deletes, so a
   *       caller holding the privilege could delete a reviewed inspection by typing the URL. See
   *       {@link InspectionNotOfflineException}.</li>
   * </ul>
   *
   * <h3>This is a port of {@code CBR.DELETE_INSPECTION}, not a call to it</h3>
   * The procedure is still there and the application role can still execute it; CBR is deliberately
   * moving off the PL/SQL, so the cascade and the correction to the parent structure are written out
   * below. Two things that came with it:
   *
   * <ul>
   *   <li><b>The order is fixed by foreign keys</b>, not by preference. The attachment bytes point
   *       at their metadata row, so they go first; the inspection itself goes last.</li>
   *   <li><b>The structure's load rating is corrected afterwards</b>, to adopt whatever rating is
   *       now the most recent. The rule for "most recent" is
   *       {@link LoadRatingService#currentLoadRating(Long)}.</li>
   * </ul>
   *
   * <p><b>The procedure's other correction has no counterpart here, and that follows from the guard
   * above.</b> Before its deletes, {@code CBR.DELETE_INSPECTION} checks whether the inspection being
   * removed supplied the structure's current rating and, if so, sets
   * {@code LOAD_RATING_UNKNOWN_INDICATOR = 'Y'}. That branch cannot be reached through this
   * endpoint: a rating counts as current only when it comes from an inspection whose current status
   * is {@code RVD} or {@code ACC} (see {@link LoadRatingService}), and this method refuses to delete
   * anything that is not {@code OFL}. The two conditions are mutually exclusive.
   *
   * <p>It is reachable in legacy only because legacy does not enforce the {@code OFL} rule
   * server-side — the same gap {@link InspectionNotOfflineException} describes. Porting the branch
   * would mean carrying code that exists to handle a delete this service refuses to perform.
   *
   * <h3>One transaction</h3>
   * Everything below happens inside this method's transaction — the reads that decide, the eight
   * deletes, and the update to the structure. There is no point at which the inspection is half
   * gone: a failure anywhere rolls back the lot, including the load-rating correction, which would
   * otherwise leave a structure carrying the rating of an inspection that is no longer there.
   *
   * @param inspectionId the {@code STRUCTURE_INSPECTION.INSPECTION_ID}
   * @throws InspectionNotFoundException   if no such inspection exists
   * @throws InspectionNotOfflineException if it exists but is not {@code OFL}
   */
  @Transactional
  public void delete(Long inspectionId) {
    StructureInspectionEntity inspection = inspections.findById(inspectionId)
        .orElseThrow(() -> new InspectionNotFoundException(inspectionId));

    String status = currentStatusOf(inspection);
    if (!OFFLINE.equals(status)) {
      throw new InspectionNotOfflineException(inspectionId, describe(status));
    }

    Long structureId = inspection.getCrossingStructureId();
    log.info("Deleting offline inspection {} from structure {}", inspectionId, structureId);

    deleteAttachments(inspectionId);
    deleteFindings(inspectionId);
    deleteTheInspection(inspectionId);
    adoptTheRemainingLoadRating(structureId);

    log.info("Deleted offline inspection {}", inspectionId);
  }

  /**
   * Attachments: the bytes first, then the metadata rows they point at.
   *
   * <p>The foreign key runs from {@code CROSSING_STRUCTURE_FILE} to
   * {@code CROSSING_STRUCTURE_FILE_DETAIL}, so the other order fails. Legacy walks a cursor and
   * deletes one pair at a time; the ids are collected here and deleted in two statements instead.
   */
  private void deleteAttachments(Long inspectionId) {
    List<Long> fileIds = attachments.findByInspectionId(inspectionId).stream()
        .map(CrossingStructureFileDetailEntity::getFileId)
        .toList();
    if (fileIds.isEmpty()) {
      log.info("Inspection {} has no attachments", inspectionId);
      return;
    }
    attachmentBytes.deleteByFileIdIn(fileIds);
    attachments.deleteByInspectionId(inspectionId);
    log.info("Deleted {} attachment(s) of inspection {}", fileIds.size(), inspectionId);
  }

  /** What the inspection recorded: monitoring items, repairs, and the filled-in form. */
  private void deleteFindings(Long inspectionId) {
    monitorItems.deleteByInspectionId(inspectionId);
    log.info("Deleted the monitor items of inspection {}", inspectionId);

    repairs.deleteByInspectionId(inspectionId);
    log.info("Deleted the repairs of inspection {}", inspectionId);

    inspectionItems.deleteByInspectionId(inspectionId);
    log.info("Deleted the inspection items of inspection {}", inspectionId);
  }

  /** The inspection's own rows: its load rating, its status history, and the inspection. */
  private void deleteTheInspection(Long inspectionId) {
    loadRatings.deleteByInspectionId(inspectionId);
    log.info("Deleted the load rating(s) of inspection {}", inspectionId);

    statusHistory.deleteByInspectionId(inspectionId);
    log.info("Deleted the status history of inspection {}", inspectionId);

    inspections.deleteById(inspectionId);
    log.info("Deleted the inspection row {}", inspectionId);
  }

  /**
   * Adopt whatever rating is now the most recent — or none, if the structure has no rating left.
   *
   * <p>Runs after the deletes on purpose: the answer depends on what is left, and asking before
   * would return the rating that is about to disappear.
   */
  private void adoptTheRemainingLoadRating(Long structureId) {
    if (structureId == null) {
      return;
    }
    BigDecimal rating = loadRatingService.currentLoadRating(structureId)
        .map(StructureLoadRatingEntity::getLoadRating)
        .orElse(null);

    structures.findById(structureId).ifPresent(structure -> structure.setCurrentLoadRating(rating));
    log.info("Structure {} now carries load rating {}", structureId,
        rating == null ? "none" : rating.toPlainString());
  }

  private static String currentStatusOf(StructureInspectionEntity inspection) {
    return Optional.ofNullable(inspection.getCurrentStatus())
        .map(InspectionReportStatusEntity::getInspectionReportStatusCode)
        .orElse(null);
  }

  /**
   * How the refusal names the status it refused.
   *
   * <p>The raw code, because that is what the results table shows in the pill beside the row the
   * user pressed Delete on — so "is SUB" points at something on their screen.
   */
  private static String describe(String status) {
    return status == null ? "has no status" : "is " + status;
  }
}
