package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.InspectionLockedException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.LoadRatingReasonXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreLoadRatingRsnCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCommentRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionReviewRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

/** A P.Eng's review of an inspection — legacy's Accept dialog — and the PEng Comments. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({InspectionReviewService.class, LoadRatingService.class})
class InspectionReviewServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.of(2000, 1, 1, 0, 0);
  private static final LocalDate DAY = LocalDate.of(2024, 5, 1);
  private static final String PENG = "IDIR\\PENG";

  @Autowired
  private InspectionReviewService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("idir\\peng");
    entityManager.persist(CrossingStructureEntity.builder().crossingStructureId(7L)
        .crossingStructureName("B7").structureTypeClassCode("PB").activeInd("Y")
        .closeProximityInd("N").portableStructureInd("N").loadRatingUnknownIndicator("Y")
        .routineInspectionFrequency("3").nextPlannedInspectionDate(DAY).build());
    for (String status : List.of("PRO", "SUB", "RVD", "ACC", "REJ")) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(status).description(status).effectiveDate(PAST)
          .expiryDate(PAST.plusYears(999)).updateTimestamp(PAST).build());
    }
    for (String code : List.of("INSP", "OTHR", "DOWN", "HIST")) {
      entityManager.persist(StrctreLoadRatingRsnCodeEntity.builder()
          .strctreLoadRatingRsnCode(code).description(code).effectiveDate(PAST)
          .expiryDate(PAST.plusYears(999)).build());
    }
    entityManager.persist(new LoadRatingReasonXrefEntity("INSP", "INC"));
    entityManager.persist(new LoadRatingReasonXrefEntity("DOWN", "DEC"));
    entityManager.persist(new LoadRatingReasonXrefEntity("OTHR", "UNDEF"));
    entityManager.persist(new LoadRatingReasonXrefEntity("HIST", "UNDEF"));
    entityManager.persist(StructureInspectionReviewerEntity.builder().inspectionReviewerId(5L)
        .userid(PENG).firstName("Pat").lastName("Eng").activeInd("Y").build());
    entityManager.persist(StructureRepairEntity.builder().crossingStructureId(7L)
        .repairNumber(1L).repairStatusCode("SUG").description("Deck").build());
    entityManager.persist(StructureMonitorItemEntity.builder().crossingStructureId(7L)
        .monitorNumber(1L).monitoringStatusCode("SUG").description("Scour").build());
  }

  private void inspection(long id, String status, String type) {
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(id)
        .crossingStructureId(7L).inspectionDate(DAY).inspectorName("Jo")
        .inspectorCompanyName("Co").strctreInspectionTypeCode(type)
        .siteAtTimeOfInspection("S1").pengInspectionReqInd("N").build());
    entityManager.persist(InspectionReportStatusEntity.builder()
        .inspectionReportStatusId(-id).inspectionId(id).inspectionReportStatusCode(status)
        .entryUserid("IDIR\\X").entryTimestamp(PAST).build());
    entityManager.flush();
    entityManager.clear();
  }

  private static InspectionReviewRequest review(Long rating, String reason, String comment) {
    return new InspectionReviewRequest(rating, reason, comment, DAY.plusYears(2), 2, false);
  }

  private Map<String, String> refused(long id, InspectionReviewRequest request) {
    try {
      service.review(id, request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("Expected the review to be refused");
  }

  private <T> T found(Class<T> type, Object id) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(type, id);
  }

  @Test
  @DisplayName("marks it Reviewed, bound to the reviewer, with its rating, schedule and follow-ups")
  void reviews() {
    inspection(41L, "SUB", "ROUT");

    service.review(41L, review(40L, "INSP", "ignored"));

    StructureInspectionEntity inspection = found(StructureInspectionEntity.class, 41L);
    assertThat(inspection.getCurrentStatus().getInspectionReportStatusCode()).isEqualTo("RVD");
    assertThat(inspection.getInspectionReviewerId()).isEqualTo(5L);
    assertThat(inspection.getPengReviewerDate()).isNotNull();
    StructureLoadRatingEntity rating = entityManager.createQuery(
        "SELECT r FROM StructureLoadRatingEntity r WHERE r.inspectionId = 41",
        StructureLoadRatingEntity.class).getSingleResult();
    assertThat(rating.getLoadRating()).isEqualByComparingTo(BigDecimal.valueOf(40));
    assertThat(rating.getReasonCode()).isEqualTo("INSP");
    // Words are kept with Other only.
    assertThat(rating.getReasonComment()).isNull();
    assertThat(rating.getSiteAtTimeOfLoadRating()).isEqualTo("S1");

    CrossingStructureEntity structure = found(CrossingStructureEntity.class, 7L);
    assertThat(structure.getCurrentLoadRating()).isEqualByComparingTo(BigDecimal.valueOf(40));
    assertThat(structure.getLoadRatingUnknownIndicator()).isEqualTo("N");
    assertThat(structure.getRoutineInspectionFrequency()).isEqualTo("2");
    assertThat(structure.getNextPlannedInspectionDate()).isEqualTo(DAY.plusYears(2));
    assertThat(entityManager.createQuery(
        "SELECT r.repairStatusCode FROM StructureRepairEntity r", String.class)
        .getResultList()).containsOnly("REQ");
    assertThat(entityManager.createQuery(
        "SELECT m.monitoringStatusCode FROM StructureMonitorItemEntity m", String.class)
        .getResultList()).containsOnly("REQ");
  }

  @Test
  @DisplayName("keeps the next planned inspection of a structure inspected other than routinely")
  void unplannedKeepsTheSchedule() {
    inspection(41L, "SUB", "UNP");

    service.review(41L, new InspectionReviewRequest(40L, "INSP", null, null, 4, true));

    CrossingStructureEntity structure = found(CrossingStructureEntity.class, 7L);
    assertThat(structure.getNextPlannedInspectionDate()).isEqualTo(DAY);
    assertThat(structure.getRoutineInspectionFrequency()).isEqualTo("4");
    assertThat(structure.getLoadRatingUnknownIndicator()).isEqualTo("Y");
  }

  @Test
  @DisplayName("needs a rating, a reason fitting its change, Other's words, and the schedule")
  void rules() {
    inspection(41L, "SUB", "ROUT");

    assertThat(refused(41L, new InspectionReviewRequest(null, null, null, null, 0, false)))
        .containsOnly(
            Map.entry("loadRating", "Current Load Rating is required."),
            Map.entry("nextRoutineDate", "Next Planned Inspection Date is required."),
            Map.entry("routineFrequencyYears",
                "Routine Inspection Frequency must be from 1 to 6 years."));
    assertThat(refused(41L, review(40L, null, null))).containsOnly(
        Map.entry("reasonCode", "Load Rating Reason is required."));
    // From no rating, 40 is an increase: DOWN does not fit, nor HIST ever.
    assertThat(refused(41L, review(40L, "DOWN", null))).containsOnly(
        Map.entry("reasonCode", "Load Rating Reason does not fit this change of load rating."));
    assertThat(refused(41L, review(40L, "HIST", null))).containsKey("reasonCode");
    assertThat(refused(41L, review(40L, "OTHR", " "))).containsOnly(
        Map.entry("reasonComment", "Load Rating Reason Comment is required for Other."));

    service.review(41L, review(40L, "OTHR", "Re-rated"));
    assertThat(entityManager.createQuery(
        "SELECT r.reasonComment FROM StructureLoadRatingEntity r WHERE r.inspectionId = 41",
        String.class).getSingleResult()).isEqualTo("Re-rated");
  }

  @Test
  @DisplayName("needs the date, inspector and company the inspection may still lack")
  void needsTheInspection() {
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(41L)
        .crossingStructureId(7L).strctreInspectionTypeCode("UNP").pengInspectionReqInd("N")
        .build());
    entityManager.flush();

    assertThat(refused(41L, review(40L, "INSP", null))).containsOnlyKeys(
        "inspectionDate", "inspectorName", "inspectorCompanyName");
  }

  @Test
  @DisplayName("is refused to a P.Eng with no active reviewer row, and once Reviewed")
  void refusals() {
    inspection(41L, "SUB", "ROUT");
    inspection(42L, "RVD", "ROUT");
    InspectionReviewRequest request = review(40L, "INSP", null);

    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\SOMEONE");
    assertThatThrownBy(() -> service.review(41L, request))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            refusal -> assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    when(loggedUser.getLoggedUserId()).thenReturn(PENG);
    assertThatThrownBy(() -> service.review(42L, request))
        .isInstanceOf(InspectionLockedException.class);
  }

  @Test
  @DisplayName("saves the PEng Comments, at most 4000")
  void pengComment() {
    inspection(41L, "SUB", "ROUT");

    service.updatePengComment(41L, new InspectionCommentRequest(" Sound. "));
    assertThat(found(StructureInspectionEntity.class, 41L).getPengComment()).isEqualTo("Sound.");
    InspectionCommentRequest tooLong = new InspectionCommentRequest("x".repeat(4001));
    assertThatThrownBy(() -> service.updatePengComment(41L, tooLong))
        .isInstanceOf(FieldValidationException.class);
  }
}
