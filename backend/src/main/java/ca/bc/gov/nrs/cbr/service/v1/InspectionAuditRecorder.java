package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionReviewerRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An inspection's audit record: its report (CBR001) as a PDF, stored among its Documents &amp;
 * Photos each time it is saved — what legacy emailed to TRIM ({@code cbr-inspection-audit-pdf.local.md}).
 *
 * <p>Applied to every save from the inspection's page: a record when, saved, it is Submitted, by
 * anyone — legacy skipped an active reviewer's save, nr-cbr does not — or Reviewed or Accepted by an
 * active inspection reviewer. None In Progress or Rejected. Every record is kept.
 *
 * <p>Made once the save has committed, in a transaction of its own, so it shows the inspection as
 * saved. As legacy, a record that cannot be made is logged and the save stands.
 */
@Component
public class InspectionAuditRecorder {

  private static final Logger log = LoggerFactory.getLogger(InspectionAuditRecorder.class);

  private static final String YES = "Y";
  /** The statuses a record is made at, with how they read. */
  private static final Map<String, String> RECORDED = Map.of(
      "SUB", "Submitted", "RVD", "Reviewed", "ACC", "Accepted");
  private static final String SUBMITTED = "SUB";
  private static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");
  private static final DateTimeFormatter PRINTED = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
  private static final DateTimeFormatter SAVED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final DateTimeFormatter IN_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");
  /** {@code FILENAME VARCHAR2(255 BYTE)}. */
  private static final int NAME_MAX = 255;

  private final StructureInspectionRepository inspections;
  private final StructureInspectionReviewerRepository reviewers;
  private final InspectionsConductedDetailService report;
  private final StructureDocumentsService documents;
  private final LoggedUserHelper loggedUser;
  private final TransactionTemplate ownTransaction;

  public InspectionAuditRecorder(StructureInspectionRepository inspections,
      StructureInspectionReviewerRepository reviewers, InspectionsConductedDetailService report,
      StructureDocumentsService documents, LoggedUserHelper loggedUser,
      PlatformTransactionManager transactions) {
    this.inspections = inspections;
    this.reviewers = reviewers;
    this.report = report;
    this.documents = documents;
    this.loggedUser = loggedUser;
    this.ownTransaction = new TransactionTemplate(transactions);
    this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Makes the inspection's audit record, if the save calls for one, once the current transaction
   * commits; none if it rolls back. Called by each save on the inspection's page; once a
   * transaction however often it is called.
   */
  public void afterCommit(long inspectionId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      recordSafely(inspectionId);
      return;
    }
    boolean registered = TransactionSynchronizationManager.getSynchronizations().stream()
        .anyMatch(sync -> sync instanceof After after && after.inspectionId == inspectionId);
    if (!registered) {
      TransactionSynchronizationManager.registerSynchronization(new After(inspectionId));
    }
  }

  /** The record, made once the transaction it was asked for in commits. */
  private final class After implements TransactionSynchronization {
    private final long inspectionId;

    private After(long inspectionId) {
      this.inspectionId = inspectionId;
    }

    @Override
    public void afterCommit() {
      recordSafely(inspectionId);
    }
  }

  /** The record, if the inspection as saved calls for one; any failure logged, never thrown. */
  void recordSafely(long inspectionId) {
    try {
      ownTransaction.executeWithoutResult(status -> make(inspectionId));
    } catch (RuntimeException failed) {
      log.error("Could not make the audit record of inspection {}", inspectionId, failed);
    }
  }

  private void make(long inspectionId) {
    StructureInspectionEntity inspection = inspections.findById(inspectionId).orElse(null);
    if (inspection == null) {
      return;
    }
    InspectionReportStatusEntity current = inspection.getCurrentStatus();
    String code = current == null ? null : current.getInspectionReportStatusCode();
    String user = loggedUser.getLoggedUserId();
    if (!RECORDED.containsKey(code)) {
      return;
    }
    // Submitted by anyone; Reviewed or Accepted by a reviewer alone, as legacy.
    if (!SUBMITTED.equals(code) && !isReviewer(user)) {
      return;
    }
    String status = RECORDED.get(code);
    LocalDateTime now = LocalDateTime.now(PACIFIC);
    // The PDF's line and the document's description, the same words. Hyphens, not dashes: the
    // database's character set does not keep an en dash.
    String line = StructureDocumentsService.AUDIT_RECORD + " - " + status + " - saved by " + user
        + " on " + now.format(SAVED);
    byte[] pdf = report.auditPdf(inspectionId, now.format(PRINTED), line);
    long fileId = documents.addAuditRecord(inspection.getCrossingStructureId(), inspectionId,
        filename(inspection, status, now), line, pdf);
    log.info("Made audit record {} of inspection {} ({})", fileId, inspectionId, status);
  }

  private boolean isReviewer(String user) {
    return reviewers.findFirstByUseridIgnoreCaseAndActiveInd(user, YES).isPresent();
  }

  /**
   * {@code <site> - <structure> - <year> inspection - <status> - <yyyy-MM-dd HHmm>.pdf}: legacy's
   * name for its sealed report, with which copy and when. The structure's name is shortened to keep
   * it within the column.
   */
  static String filename(StructureInspectionEntity inspection, String status, LocalDateTime at) {
    String year = inspection.getInspectionDate() == null
        ? "" : inspection.getInspectionDate().getYear() + " ";
    String tail = " - " + year + "inspection - " + status + " - " + at.format(IN_NAME) + ".pdf";
    String site = inspection.getStructure().getCrossingSiteId();
    String head = (site == null ? "" : site + " - ");
    String name = inspection.getStructure().getCrossingStructureName();
    name = name == null ? "" : name.strip();
    int room = NAME_MAX - bytes(head) - bytes(tail);
    while (bytes(name) > room) {
      name = name.substring(0, name.length() - 1);
    }
    return head + name + tail;
  }

  private static int bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }
}
