package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.InspectionLockedException;
import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCommentRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionUpdateRequest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The Inspection tab of an inspection's page, edited with legacy's rules. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({InspectionEditService.class, InspectionFormService.class})
class InspectionEditServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.of(2000, 1, 1, 0, 0);
  private static final LocalDate DAY = LocalDate.of(2024, 5, 1);

  @Autowired
  private InspectionEditService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  /** Its rule and its PDF are its own test's; here, only that a save asks for it. */
  @MockitoBean
  private InspectionAuditRecorder audit;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\INSPECTOR");
    entityManager.persist(CrossingStructureEntity.builder().crossingStructureId(7L)
        .crossingStructureName("B7").structureTypeClassCode("PB").activeInd("Y")
        .closeProximityInd("N").portableStructureInd("N").build());
    for (String status : List.of("PRO", "SUB", "RVD", "ACC", "REJ", "OFL")) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(status).description(status).effectiveDate(PAST)
          .expiryDate(PAST.plusYears(999)).updateTimestamp(PAST).build());
    }
  }

  private void inspection(long id, String status, LocalDate date, String inspector) {
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(id)
        .crossingStructureId(7L).inspectionDate(date).inspectorName(inspector)
        .pengInspectionReqInd("N").build());
    entityManager.persist(InspectionReportStatusEntity.builder()
        // Below the sequence's ids, so a status the service adds is the latest, as in production.
        .inspectionReportStatusId(-id).inspectionId(id).inspectionReportStatusCode(status)
        .entryUserid("IDIR\\X").entryTimestamp(PAST).build());
    entityManager.flush();
    entityManager.clear();
  }

  private static InspectionUpdateRequest card(String status, LocalDate date, String inspector,
      String company) {
    return new InspectionUpdateRequest(status, date, inspector, company, 1200L, false);
  }

  private Map<String, String> refused(long id, InspectionUpdateRequest request) {
    try {
      service.update(id, request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("Expected the inspection to be refused");
  }

  private StructureInspectionEntity stored(long id) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(StructureInspectionEntity.class, id);
  }

  @Test
  @DisplayName("saves the card and submits it, adding a row to the status history")
  void savesAndSubmits() {
    inspection(41L, "PRO", null, null);

    service.update(41L, card("sub", DAY, " Jo Smith ", "Bridges Ltd"));

    StructureInspectionEntity inspection = stored(41L);
    assertThat(inspection.getInspectionDate()).isEqualTo(DAY);
    assertThat(inspection.getInspectorName()).isEqualTo("Jo Smith");
    assertThat(inspection.getInspectionCost()).isEqualTo(1200L);
    assertThat(inspection.getCurrentStatus().getInspectionReportStatusCode()).isEqualTo("SUB");
    assertThat(entityManager.createQuery(
        "SELECT COUNT(s) FROM InspectionReportStatusEntity s WHERE s.inspectionId = 41",
        Long.class).getSingleResult()).isEqualTo(2L);

    service.update(41L, card("SUB", DAY, "Jo Smith", "Bridges Ltd"));
    // The same status again adds no row.
    assertThat(entityManager.createQuery(
        "SELECT COUNT(s) FROM InspectionReportStatusEntity s WHERE s.inspectionId = 41",
        Long.class).getSingleResult()).isEqualTo(2L);
    // Each save may be recorded; whether it is, is the recorder's rule.
    verify(audit, times(2)).afterCommit(41L);
  }

  @Test
  @DisplayName("needs the date, inspector and company past In Progress, or once given")
  void required() {
    inspection(41L, "PRO", null, "Jo Smith");

    assertThat(refused(41L, card("PRO", null, null, null))).containsOnly(
        Map.entry("inspectorName", "Inspector Name is required."));
    assertThat(refused(41L, card("SUB", null, "Jo", null))).containsOnly(
        Map.entry("inspectionDate", "Date of Inspection is required."),
        Map.entry("inspectorCompanyName", "Inspection Company Name is required."));
  }

  @Test
  @DisplayName("refuses a future date or another inspection's, but keeps its own")
  void dates() {
    inspection(41L, "PRO", DAY, null);
    inspection(42L, "PRO", DAY.minusDays(1), null);

    service.update(41L, card("PRO", DAY, null, null));
    assertThat(refused(41L, card("PRO", DAY.minusDays(1), null, null)))
        .containsEntry("inspectionDate",
            "Inspection with this date already exists. Please choose another Inspection date.");
    assertThat(refused(41L, card("PRO", LocalDate.now().plusDays(1), null, null)))
        .containsEntry("inspectionDate", "Date of Inspection must not be after today.");
  }

  @Test
  @DisplayName("leaves Reviewed to the review, Rejected to a P.Eng to move")
  void statuses() {
    inspection(41L, "PRO", null, null);
    inspection(42L, "REJ", DAY, "Jo");

    assertThat(refused(41L, card("RVD", null, null, null))).containsEntry("statusCode",
        "Reviewed is set by an inspection reviewer through the review, not saved here.");
    assertThat(refused(42L, card("SUB", DAY, "Jo", "Co"))).containsOnly(
        Map.entry("statusCode", "You do not have permission to change this status."));
    service.update(42L, card("REJ", DAY, "Jo", "Co"));
    when(loggedUser.isPeng()).thenReturn(true);
    service.update(42L, card("SUB", DAY, "Jo", "Co"));
    assertThat(stored(42L).getCurrentStatus().getInspectionReportStatusCode()).isEqualTo("SUB");
  }

  @Test
  @DisplayName("rejects only for an inspection reviewer, from In Progress or Submitted")
  void whoRejects() {
    inspection(41L, "SUB", DAY, "Jo");
    inspection(42L, "OFL", DAY.minusDays(1), "Jo");

    // A P.Eng below the reviewer may move a Rejected inspection on, but not reject one.
    when(loggedUser.isPeng()).thenReturn(true);
    assertThat(refused(41L, card("REJ", DAY, "Jo", "Co"))).containsOnly(
        Map.entry("statusCode", "Only an inspection reviewer can reject an inspection."));
    when(loggedUser.isInspectionReviewer()).thenReturn(true);
    assertThat(refused(42L, card("REJ", DAY.minusDays(1), "Jo", "Co"))).containsOnly(
        Map.entry("statusCode", "Only an In Progress or Submitted inspection can be rejected."));

    service.update(41L, card("REJ", DAY, "Jo", "Co"));
    assertThat(stored(41L).getCurrentStatus().getInspectionReportStatusCode()).isEqualTo("REJ");
  }

  @Test
  @DisplayName("rejecting schedules the structure's next routine inspection a year out, yearly")
  void rejectingReschedules() {
    inspection(41L, "PRO", DAY, "Jo");
    when(loggedUser.isInspectionReviewer()).thenReturn(true);
    LocalDate yearOut = LocalDate.now().plusYears(1);

    service.update(41L, card("REJ", DAY, "Jo", "Co"));

    CrossingStructureEntity structure = structure();
    assertThat(structure.getNextPlannedInspectionDate()).isEqualTo(yearOut);
    assertThat(structure.getRoutineInspectionFrequency()).isEqualTo("1");
    assertThat(structure.getUpdateUserid()).isEqualTo("IDIR\\INSPECTOR");
    // Once, on the change to Rejected: saving it again Rejected leaves the schedule alone.
    entityManager.createQuery("UPDATE CrossingStructureEntity s SET s.nextPlannedInspectionDate"
        + " = :day, s.routineInspectionFrequency = '3' WHERE s.crossingStructureId = 7")
        .setParameter("day", DAY).executeUpdate();
    service.update(41L, card("REJ", DAY, "Jo", "Co"));
    assertThat(structure().getNextPlannedInspectionDate()).isEqualTo(DAY);
    assertThat(structure().getRoutineInspectionFrequency()).isEqualTo("3");
  }

  @Test
  @DisplayName("another status change leaves the structure's schedule alone")
  void submittingKeepsSchedule() {
    inspection(41L, "PRO", DAY, "Jo");

    service.update(41L, card("SUB", DAY, "Jo", "Co"));

    assertThat(structure().getNextPlannedInspectionDate()).isNull();
    assertThat(structure().getRoutineInspectionFrequency()).isNull();
  }

  private CrossingStructureEntity structure() {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(CrossingStructureEntity.class, 7L);
  }

  @Test
  @DisplayName("leaves Professional Inspection Required to Level 2, and the cost in range")
  void professionalAndCost() {
    inspection(41L, "PRO", null, null);
    InspectionUpdateRequest professional =
        new InspectionUpdateRequest("PRO", null, null, null, -1L, true);

    assertThat(refused(41L, professional)).containsOnlyKeys("cost",
        "professionalInspectionRequired");
    when(loggedUser.canDestroy()).thenReturn(true);
    service.update(41L, new InspectionUpdateRequest("PRO", null, null, null, null, true));
    assertThat(stored(41L).getPengInspectionReqInd()).isEqualTo("Y");
  }

  @Test
  @DisplayName("saves the comments, at most 4000")
  void comments() {
    inspection(41L, "SUB", DAY, "Jo");

    service.updateComment(41L, new InspectionCommentRequest("  All sound.  "));
    verify(audit).afterCommit(41L);
    assertThat(stored(41L).getInspectionComment()).isEqualTo("All sound.");
    InspectionCommentRequest tooLong = new InspectionCommentRequest("x".repeat(4001));
    assertThatThrownBy(() -> service.updateComment(41L, tooLong))
        .isInstanceOf(FieldValidationException.class);
  }

  @Test
  @DisplayName("is refused once Reviewed or Accepted, as legacy — not once Rejected")
  void locks() {
    inspection(41L, "RVD", DAY, "Jo");
    inspection(42L, "ACC", DAY.minusDays(2), "Jo");
    InspectionCommentRequest comment = new InspectionCommentRequest("x");
    InspectionUpdateRequest request = card("RVD", DAY, "Jo", "Co");

    assertThatThrownBy(() -> service.update(41L, request))
        .isInstanceOf(InspectionLockedException.class)
        .hasMessageContaining("can no longer be changed");
    assertThatThrownBy(() -> service.updateComment(42L, comment))
        .isInstanceOf(InspectionLockedException.class);
    assertThatThrownBy(() -> service.updateComment(404L, comment))
        .isInstanceOf(InspectionNotFoundException.class);
  }
}
