package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CloseProximityInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.SpecialEquipmentRequirementCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreInspectionTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CloseProximityInspectionRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionScheduleRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Comment;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionScheduleResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionScheduleResponse.CloseProximityInspection;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionsResponse.Inspection;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

/** The Inspections tab's data, against the database: ordered reads, a date filter and decodes. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureInspectionsService.class)
class StructureInspectionsServiceTest {

  private static final LocalDateTime EARLY = LocalDateTime.of(2000, 1, 1, 0, 0);
  private static final LocalDateTime LATE = LocalDateTime.of(9999, 12, 31, 0, 0);

  @Autowired
  private StructureInspectionsService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    for (String entity : List.of("InspectionReportStatusEntity", "StructureInspectionEntity",
        "InspectionReportStatusCodeEntity", "StrctreInspectionTypeCodeEntity",
        "StructureInspectionReviewerEntity", "StructureCommentEntity",
        "CloseProximityInspectionEntity", "SpecialEquipmentRequirementCodeEntity",
        "CrossingStructureEntity", "CrossingSiteEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (String status : List.of("RVD", "OFL")) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(status).description(status + " description")
          .effectiveDate(EARLY).expiryDate(LATE).build());
    }
  }

  private void givenStructure(long id,
      Consumer<CrossingStructureEntity.CrossingStructureEntityBuilder> change) {
    CrossingStructureEntity.CrossingStructureEntityBuilder structure =
        CrossingStructureEntity.builder()
            .crossingStructureId(id).crossingStructureName("B" + id).structureTypeClassCode("TB")
            .activeInd("Y").closeProximityInd("N").portableStructureInd("N");
    change.accept(structure);
    entityManager.persist(structure.build());
  }

  /** An inspection with one status row — its current status. */
  private void givenInspection(long id, long structureId, LocalDate date, String status,
      Consumer<StructureInspectionEntity.StructureInspectionEntityBuilder> change) {
    StructureInspectionEntity.StructureInspectionEntityBuilder inspection =
        StructureInspectionEntity.builder()
            .inspectionId(id).crossingStructureId(structureId).inspectionDate(date);
    change.accept(inspection);
    entityManager.persist(inspection.build());
    if (status != null) {
      entityManager.persist(InspectionReportStatusEntity.builder()
          .inspectionReportStatusId(id).inspectionId(id).inspectionReportStatusCode(status)
          .build());
    }
  }

  private void givenInspection(long id, long structureId, LocalDate date) {
    givenInspection(id, structureId, date, "RVD", inspection -> { });
  }

  private void settle() {
    entityManager.flush();
    entityManager.clear();
  }

  @Nested
  @DisplayName("the schedule")
  class Schedule {

    @Test
    @DisplayName("carries the planned-inspection comments only, newest first")
    void plannedComments() {
      givenStructure(7L, structure -> { });
      entityManager.persist(StructureCommentEntity.builder()
          .crossingStructureId(7L).structureComment("General.")
          .plannedInspectionCmtInd("N").updateUserid("IDIR\\A").updateTimestamp(EARLY).build());
      entityManager.persist(StructureCommentEntity.builder()
          .crossingStructureId(7L).structureComment("Bring a boat.")
          .plannedInspectionCmtInd("Y").updateUserid("IDIR\\B")
          .updateTimestamp(LocalDateTime.of(2020, 1, 1, 9, 0)).build());
      entityManager.persist(StructureCommentEntity.builder()
          .crossingStructureId(7L).structureComment("Use the UBIU.")
          .plannedInspectionCmtInd("Y").updateUserid("IDIR\\C")
          .updateTimestamp(LocalDateTime.of(2024, 1, 1, 9, 0)).build());
      settle();

      assertThat(service.schedule(7L).plannedInspectionComments())
          .extracting(Comment::text).containsExactly("Use the UBIU.", "Bring a boat.");
    }

    @Test
    @DisplayName("carries the close proximity requirement, the next dates and the frequency")
    void scheduleFields() {
      entityManager.persist(SpecialEquipmentRequirementCodeEntity.builder()
          .specialEquipmentRequirementCode("UBIU").description("Under-bridge inspection unit")
          .effectiveDate(EARLY).expiryDate(LATE).build());
      givenStructure(7L, structure -> structure
          .closeProximityInd("Y").specialEquipmentRqmtCode("UBIU")
          .nextPlannedClsProxInspDt(LocalDate.of(2027, 5, 1))
          .nextPlannedInspectionDate(LocalDate.of(2026, 9, 1))
          .routineInspectionFrequency("3"));
      settle();

      StructureInspectionScheduleResponse schedule = service.schedule(7L);

      assertThat(schedule.closeProximityRequired()).isTrue();
      assertThat(schedule.closeProximityEquipment())
          .isEqualTo(new CodeValue("UBIU", "Under-bridge inspection unit"));
      assertThat(schedule.nextCloseProximityDate()).isEqualTo(LocalDate.of(2027, 5, 1));
      assertThat(schedule.nextRoutineDate()).isEqualTo(LocalDate.of(2026, 9, 1));
      assertThat(schedule.routineFrequencyYears()).isEqualTo(3);
    }

    @Test
    @DisplayName("lists the completed close proximity inspections, newest first")
    void completedCloseProximity() {
      givenStructure(7L, structure -> { });
      for (long id : List.of(1L, 2L)) {
        entityManager.persist(CloseProximityInspectionEntity.builder()
            .crossingSiteId("62-001").crossingStructureId(7L)
            .completionDate(LocalDate.of(2018 + (int) id * 3, 6, 1))
            .entryUserid("IDIR\\P" + id).build());
      }
      settle();

      assertThat(service.schedule(7L).completedCloseProximity())
          .extracting(CloseProximityInspection::completed, CloseProximityInspection::userId)
          .containsExactly(
              tuple(LocalDate.of(2024, 6, 1), "IDIR\\P2"),
              tuple(LocalDate.of(2021, 6, 1), "IDIR\\P1"));
    }

    @Test
    @DisplayName("refuses a structure that does not exist")
    void missingStructure() {
      assertThatThrownBy(() -> service.schedule(404L))
          .isInstanceOf(StructureNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("the inspection table")
  class Table {

    @Test
    @DisplayName("lists only inspections after the superstructure went in, newest first, and "
        + "counts the rest, as FIND_NEW_INSPECTIONS_BY_STRUCT does")
    void afterInstallByDefault() {
      givenStructure(7L, structure -> structure.yearBuilt(2010));
      givenInspection(1L, 7L, LocalDate.of(2005, 6, 1));
      givenInspection(2L, 7L, LocalDate.of(2015, 6, 1));
      givenInspection(3L, 7L, LocalDate.of(2020, 6, 1));
      givenInspection(4L, 7L, LocalDate.of(2010, 1, 1));
      settle();

      StructureInspectionsResponse response = service.inspections(7L, 0, 10, false);

      assertThat(response.page().content()).extracting(Inspection::id).containsExactly("3", "2");
      assertThat(response.page().totalElements()).isEqualTo(2);
      // 1 January of the year installed is not "after" it, as legacy's > has it.
      assertThat(response.beforeInstallCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("lists every inspection when asked")
    void everyInspectionWhenAsked() {
      givenStructure(7L, structure -> structure.yearBuilt(2010));
      givenInspection(1L, 7L, LocalDate.of(2005, 6, 1));
      givenInspection(2L, 7L, LocalDate.of(2015, 6, 1));
      settle();

      assertThat(service.inspections(7L, 0, 10, true).page().content())
          .extracting(Inspection::id).containsExactly("2", "1");
    }

    @Test
    @DisplayName("pages the table on the server")
    void pages() {
      givenStructure(7L, structure -> { });
      for (long id = 1; id <= 12; id++) {
        givenInspection(id, 7L, LocalDate.of(2000 + (int) id, 1, 15));
      }
      settle();

      StructureInspectionsResponse second = service.inspections(7L, 1, 10, false);

      assertThat(second.page().content()).extracting(Inspection::id).containsExactly("2", "1");
      assertThat(second.page().totalElements()).isEqualTo(12);
      assertThat(second.page().totalPages()).isEqualTo(2);
      assertThat(second.page().pageNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("carries each row's type, site at the time, status, review and inspector")
    void rowDetails() {
      entityManager.persist(StrctreInspectionTypeCodeEntity.builder()
          .strctreInspectionTypeCode("ROUT").description("Routine")
          .effectiveDate(EARLY).expiryDate(LATE).build());
      entityManager.persist(StructureInspectionReviewerEntity.builder()
          .inspectionReviewerId(5L).userid("IDIR\\PENG").firstName("Pat").lastName("Engineer")
          .build());
      givenStructure(7L, structure -> { });
      givenInspection(1L, 7L, LocalDate.of(2023, 6, 1), "RVD", inspection -> inspection
          .strctreInspectionTypeCode("ROUT").siteAtTimeOfInspection("62-001")
          .inspectionReviewerId(5L).pengReviewerDate(LocalDateTime.of(2023, 7, 2, 10, 0))
          .inspectorName("Sam Inspector"));
      settle();

      Inspection row = service.inspections(7L, 0, 10, false).page().content().getFirst();

      assertThat(row.type()).isEqualTo(new CodeValue("ROUT", "Routine"));
      assertThat(row.siteId()).isEqualTo("62-001");
      assertThat(row.status()).isEqualTo(new CodeValue("RVD", "RVD description"));
      assertThat(row.reviewedDate()).isEqualTo(LocalDate.of(2023, 7, 2));
      assertThat(row.reviewedBy()).isEqualTo("Pat Engineer");
      assertThat(row.inspectorName()).isEqualTo("Sam Inspector");
      assertThat(row.viewable()).isTrue();
    }

    @Test
    @DisplayName("offers no view of an inspection still out on the offline client")
    void offlineIsNotViewable() {
      givenStructure(7L, structure -> { });
      givenInspection(1L, 7L, LocalDate.of(2023, 6, 1), "OFL", inspection -> { });
      settle();

      assertThat(service.inspections(7L, 0, 10, false).page().content().getFirst().viewable())
          .isFalse();
    }

    @Test
    @DisplayName("leaves out an inspection with no status, as legacy's inner join does")
    void noStatusNotListed() {
      givenStructure(7L, structure -> { });
      givenInspection(1L, 7L, LocalDate.of(2023, 6, 1), null, inspection -> { });
      settle();

      assertThat(service.inspections(7L, 0, 10, false).page().content()).isEmpty();
    }

    @Test
    @DisplayName("refuses a structure that does not exist")
    void missingStructure() {
      assertThatThrownBy(() -> service.inspections(404L, 0, 10, false))
          .isInstanceOf(StructureNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("the latest reviewed inspection")
  class LatestReviewed {

    @Test
    @DisplayName("is the latest reviewed or accepted inspection's date, which a new frequency "
        + "counts from, and null with none")
    void latestReviewedDate() {
      givenStructure(7L, structure -> { });
      givenStructure(8L, structure -> { });
      givenInspection(1L, 7L, LocalDate.of(2022, 6, 1));
      givenInspection(2L, 7L, LocalDate.of(2024, 6, 15));
      givenInspection(3L, 7L, LocalDate.of(2025, 3, 1), "OFL", inspection -> { });
      settle();

      assertThat(service.schedule(7L).latestReviewedInspectionDate())
          .isEqualTo(LocalDate.of(2024, 6, 15));
      assertThat(service.schedule(8L).latestReviewedInspectionDate()).isNull();
    }
  }

  @Nested
  @DisplayName("editing the schedule")
  class EditSchedule {

    private static final LocalDate STORED_NEXT = LocalDate.of(2027, 5, 1);
    private static final LocalDate STORED_CLOSE = LocalDate.of(2028, 2, 1);

    /** A structure with a full schedule: close proximity required, UBIU, every 3 years. */
    private void givenScheduled(String closeProximity) {
      entityManager.persist(SpecialEquipmentRequirementCodeEntity.builder()
          .specialEquipmentRequirementCode("UBIU").description("Under-bridge unit").build());
      entityManager.persist(SpecialEquipmentRequirementCodeEntity.builder()
          .specialEquipmentRequirementCode("BOAT").description("Boat").build());
      givenStructure(7L, structure -> structure.closeProximityInd(closeProximity)
          .specialEquipmentRqmtCode("UBIU").nextPlannedClsProxInspDt(STORED_CLOSE)
          .nextPlannedInspectionDate(STORED_NEXT).routineInspectionFrequency("3"));
    }

    private CrossingStructureEntity stored() {
      settle();
      return entityManager.find(CrossingStructureEntity.class, 7L);
    }

    private FieldValidationException refused(InspectionScheduleRequest request) {
      settle();
      try {
        service.updateSchedule(7L, request);
      } catch (FieldValidationException refused) {
        return refused;
      }
      throw new AssertionError("the schedule was saved");
    }

    @Test
    @DisplayName("saves all of it for Level 2 and up, the next routine date as sent, and who "
        + "did it")
    void level2SavesEverything() {
      when(loggedUser.canDestroy()).thenReturn(true);
      givenScheduled("Y");
      settle();

      service.updateSchedule(7L, new InspectionScheduleRequest(true, " BOAT ",
          LocalDate.of(2029, 1, 1), LocalDate.of(2026, 12, 1), 2));

      CrossingStructureEntity structure = stored();
      assertThat(structure.getCloseProximityInd()).isEqualTo("Y");
      assertThat(structure.getSpecialEquipmentRqmtCode()).isEqualTo("BOAT");
      assertThat(structure.getNextPlannedClsProxInspDt()).isEqualTo(LocalDate.of(2029, 1, 1));
      assertThat(structure.getNextPlannedInspectionDate()).isEqualTo(LocalDate.of(2026, 12, 1));
      assertThat(structure.getRoutineInspectionFrequency()).isEqualTo("2");
      assertThat(structure.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
      assertThat(structure.getUpdateTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("keeps the close proximity equipment and date when none is required, as legacy")
    void closeProximityOffKeepsItsValues() {
      when(loggedUser.canDestroy()).thenReturn(true);
      givenScheduled("Y");
      settle();

      service.updateSchedule(7L,
          new InspectionScheduleRequest(false, "BOAT", null, STORED_NEXT, 3));

      CrossingStructureEntity structure = stored();
      assertThat(structure.getCloseProximityInd()).isEqualTo("N");
      assertThat(structure.getSpecialEquipmentRqmtCode()).isEqualTo("UBIU");
      assertThat(structure.getNextPlannedClsProxInspDt()).isEqualTo(STORED_CLOSE);
    }

    @Test
    @DisplayName("lets Level 1 change only the frequency, moving the next routine date to the "
        + "latest reviewed inspection plus the new frequency, as legacy's page")
    void level1FrequencyOnly() {
      givenScheduled("Y");
      givenInspection(1L, 7L, LocalDate.of(2024, 6, 15));
      givenInspection(2L, 7L, LocalDate.of(2025, 3, 1), "OFL", inspection -> { });
      settle();

      service.updateSchedule(7L, new InspectionScheduleRequest(false, "BOAT",
          LocalDate.of(2029, 1, 1), LocalDate.of(2030, 1, 1), 5));

      CrossingStructureEntity structure = stored();
      assertThat(structure.getRoutineInspectionFrequency()).isEqualTo("5");
      // The reviewed inspection, not the later offline one.
      assertThat(structure.getNextPlannedInspectionDate()).isEqualTo(LocalDate.of(2029, 6, 15));
      assertThat(structure.getCloseProximityInd()).isEqualTo("Y");
      assertThat(structure.getSpecialEquipmentRqmtCode()).isEqualTo("UBIU");
      assertThat(structure.getNextPlannedClsProxInspDt()).isEqualTo(STORED_CLOSE);
    }

    @Test
    @DisplayName("saves Level 1's frequency with the stored date when there is no reviewed "
        + "inspection, where legacy lost the change")
    void level1WithoutReviewedInspection() {
      givenScheduled("N");
      settle();

      service.updateSchedule(7L, new InspectionScheduleRequest(false, null, null, null, 4));

      CrossingStructureEntity structure = stored();
      assertThat(structure.getRoutineInspectionFrequency()).isEqualTo("4");
      assertThat(structure.getNextPlannedInspectionDate()).isEqualTo(STORED_NEXT);
    }

    @Test
    @DisplayName("keeps Level 1's stored date when the frequency is unchanged")
    void level1SameFrequency() {
      givenScheduled("N");
      givenInspection(1L, 7L, LocalDate.of(2024, 6, 15));
      settle();

      service.updateSchedule(7L, new InspectionScheduleRequest(false, null, null, null, 3));

      assertThat(stored().getNextPlannedInspectionDate()).isEqualTo(STORED_NEXT);
    }

    @Test
    @DisplayName("needs a frequency from 1 to 6, a known equipment code, and a next routine date "
        + "once one is set")
    void refusesEachField() {
      when(loggedUser.canDestroy()).thenReturn(true);
      givenScheduled("Y");

      assertThat(refused(new InspectionScheduleRequest(true, "NOPE", null, null, null))
          .getFieldErrors()).containsExactly(
              Map.entry("routineFrequencyYears",
                  "Routine Inspection Frequency is required."),
              Map.entry("closeProximityEquipmentCode", "Close Proximity Special "
                  + "Equipment Requirements is not one of the listed requirements."),
              Map.entry("nextRoutineDate",
                  "Next Planned Routine Inspection is required."));
      assertThat(refused(new InspectionScheduleRequest(false, null, null, STORED_NEXT, 7))
          .getFieldErrors()).containsOnlyKeys("routineFrequencyYears");
    }

    @Test
    @DisplayName("refuses a structure that does not exist")
    void missingStructure() {
      InspectionScheduleRequest request =
          new InspectionScheduleRequest(false, null, null, null, 3);
      assertThatThrownBy(() -> service.updateSchedule(404L, request))
          .isInstanceOf(StructureNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("recording a completed close proximity inspection")
  class AddCloseProximity {

    @BeforeEach
    void givenSite() {
      entityManager.persist(CrossingSiteEntity.builder()
          .crossingSiteId("62-001").capitalRoadInd("N").crossingSiteTypeCode("XNG").build());
    }

    @Test
    @DisplayName("stores the date, the structure's site, and who recorded it and when, leaving the "
        + "next planned close proximity date alone, as legacy")
    void records() {
      givenStructure(7L, structure -> structure.crossingSiteId("62-001")
          .nextPlannedClsProxInspDt(LocalDate.of(2028, 2, 1)));
      settle();

      String id = service.addCloseProximityInspection(7L,
          new CloseProximityInspectionRequest(LocalDate.of(2031, 5, 4)));
      settle();

      CloseProximityInspectionEntity done =
          entityManager.find(CloseProximityInspectionEntity.class, Long.parseLong(id));
      assertThat(done.getCrossingStructureId()).isEqualTo(7L);
      assertThat(done.getCrossingSiteId()).isEqualTo("62-001");
      // Any date: a future one is accepted.
      assertThat(done.getCompletionDate()).isEqualTo(LocalDate.of(2031, 5, 4));
      assertThat(done.getEntryUserid()).isEqualTo("IDIR\\EDITOR");
      assertThat(done.getEntryTimestamp()).isNotNull();
      assertThat(entityManager.find(CrossingStructureEntity.class, 7L)
          .getNextPlannedClsProxInspDt()).isEqualTo(LocalDate.of(2028, 2, 1));
      assertThat(service.schedule(7L).completedCloseProximity())
          .extracting(CloseProximityInspection::id).containsExactly(id);
    }

    @Test
    @DisplayName("needs a date, where legacy saved a row without one")
    void needsDate() {
      givenStructure(7L, structure -> structure.crossingSiteId("62-001"));
      settle();
      CloseProximityInspectionRequest request = new CloseProximityInspectionRequest(null);

      assertThatThrownBy(() -> service.addCloseProximityInspection(7L, request))
          .isInstanceOfSatisfying(FieldValidationException.class, refused ->
              assertThat(refused.getFieldErrors()).containsEntry("completedDate",
                  "Date is required."));
    }

    @Test
    @DisplayName("refuses a structure on no site, which the row needs, and one that does not exist")
    void needsSite() {
      givenStructure(7L, structure -> { });
      settle();
      CloseProximityInspectionRequest request =
          new CloseProximityInspectionRequest(LocalDate.of(2024, 1, 1));

      assertThatThrownBy(() -> service.addCloseProximityInspection(7L, request))
          .isInstanceOfSatisfying(ResponseStatusException.class, refused ->
              assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
      assertThatThrownBy(() -> service.addCloseProximityInspection(404L, request))
          .isInstanceOf(StructureNotFoundException.class);
    }
  }
}
