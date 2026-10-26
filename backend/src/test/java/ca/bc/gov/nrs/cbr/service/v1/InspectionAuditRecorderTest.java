package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionReviewerRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** When an inspection's save makes its audit record, and what the record is called. */
class InspectionAuditRecorderTest {

  private static final long INSPECTION = 100L;
  private static final byte[] PDF = "%PDF".getBytes(StandardCharsets.US_ASCII);

  private final StructureInspectionRepository inspections = mock(StructureInspectionRepository.class);
  private final StructureInspectionReviewerRepository reviewers =
      mock(StructureInspectionReviewerRepository.class);
  private final InspectionsConductedDetailService report =
      mock(InspectionsConductedDetailService.class);
  private final StructureDocumentsService documents = mock(StructureDocumentsService.class);
  private final LoggedUserHelper loggedUser = mock(LoggedUserHelper.class);

  private final InspectionAuditRecorder recorder = new InspectionAuditRecorder(inspections,
      reviewers, report, documents, loggedUser, mock(PlatformTransactionManager.class));

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\JSMITH");
    when(report.auditPdf(eq(INSPECTION), anyString(), anyString())).thenReturn(PDF);
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private void given(String status, boolean reviewer) {
    when(inspections.findById(INSPECTION)).thenReturn(Optional.of(inspection(status, "B1")));
    when(reviewers.findFirstByUseridIgnoreCaseAndActiveInd("IDIR\\JSMITH", "Y"))
        .thenReturn(reviewer
            ? Optional.of(StructureInspectionReviewerEntity.builder().build())
            : Optional.empty());
  }

  private static StructureInspectionEntity inspection(String status, String structureName) {
    return StructureInspectionEntity.builder()
        .inspectionId(INSPECTION)
        .crossingStructureId(7L)
        .inspectionDate(LocalDate.of(2025, 6, 12))
        .structure(CrossingStructureEntity.builder()
            .crossingStructureId(7L).crossingSiteId("62-001")
            .crossingStructureName(structureName).build())
        .currentStatus(status == null ? null : InspectionReportStatusEntity.builder()
            .inspectionId(INSPECTION).inspectionReportStatusCode(status).build())
        .build();
  }

  @ParameterizedTest(name = "{0}, saved by a reviewer: {1}")
  @CsvSource({"SUB, false, Submitted", "SUB, true, Submitted", "RVD, true, Reviewed",
      "ACC, true, Accepted"})
  @DisplayName("records a save while Submitted by anyone, a reviewer too (unlike legacy); Reviewed "
      + "or Accepted by a reviewer")
  void records(String status, boolean reviewer, String reads) {
    given(status, reviewer);

    recorder.record(INSPECTION);

    String line = "Audit record - " + reads
        + " - saved by IDIR\\\\JSMITH on \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}";
    verify(report).auditPdf(eq(INSPECTION), matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}"),
        matches(line));
    // The description says what the PDF's line says.
    verify(documents).addAuditRecord(eq(7L), eq(INSPECTION),
        matches("62-001 - B1 - 2025 inspection - " + reads
            + " - \\d{4}-\\d{2}-\\d{2} \\d{4}\\.pdf"),
        matches(line), eq(PDF));
  }

  @ParameterizedTest(name = "{0}, saved by a reviewer: {1}")
  @CsvSource(value = {"RVD, false", "ACC, false", "PRO, false", "PRO, true", "REJ, true",
      "null, false"}, nullValues = "null")
  @DisplayName("records nothing otherwise: an inspector's save once Reviewed, nor In Progress or "
      + "Rejected")
  void recordsNothing(String status, boolean reviewer) {
    given(status, reviewer);

    recorder.record(INSPECTION);

    verify(report, never()).auditPdf(anyLong(), anyString(), anyString());
    verify(documents, never()).addAuditRecord(anyLong(), anyLong(), anyString(), anyString(),
        any());
  }

  @Test
  @DisplayName("logs a record it cannot make, and lets the save stand")
  void failureIsLogged() {
    given("SUB", false);
    when(documents.addAuditRecord(anyLong(), anyLong(), anyString(), anyString(), any()))
        .thenThrow(new IllegalStateException("bucket down"));

    assertThatNoException().isThrownBy(() -> recorder.record(INSPECTION));
  }

  @Test
  @DisplayName("records once the save commits, once however many changes it made; not on rollback")
  void afterCommit() {
    given("SUB", false);
    TransactionSynchronizationManager.initSynchronization();

    recorder.afterCommit(INSPECTION);
    recorder.afterCommit(INSPECTION);

    verify(documents, never()).addAuditRecord(anyLong(), anyLong(), anyString(), anyString(),
        any());
    assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    verify(documents, never()).addAuditRecord(anyLong(), anyLong(), anyString(), anyString(),
        any());
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(documents).addAuditRecord(anyLong(), anyLong(), anyString(), anyString(), any());
  }

  @Test
  @DisplayName("shortens a long structure name to keep the file name within 255 bytes")
  void longName() {
    String name = InspectionAuditRecorder.filename(inspection("SUB", "é".repeat(200)),
        "Submitted", LocalDateTime.of(2026, 10, 26, 14, 5));

    assertThat(name.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(255);
    assertThat(name).startsWith("62-001 - éé")
        .endsWith(" - 2025 inspection - Submitted - 2026-10-26 1405.pdf");
  }
}
