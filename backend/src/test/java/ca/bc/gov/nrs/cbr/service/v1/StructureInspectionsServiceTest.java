package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CloseProximityInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.SpecialEquipmentRequirementCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreInspectionTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
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
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

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

  @BeforeEach
  void setUp() {
    for (String entity : List.of("InspectionReportStatusEntity", "StructureInspectionEntity",
        "InspectionReportStatusCodeEntity", "StrctreInspectionTypeCodeEntity",
        "StructureInspectionReviewerEntity", "StructureCommentEntity",
        "CloseProximityInspectionEntity", "SpecialEquipmentRequirementCodeEntity",
        "CrossingStructureEntity")) {
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
          .structureCommentId(1L).crossingStructureId(7L).structureComment("General.")
          .plannedInspectionCmtInd("N").updateUserid("IDIR\\A").updateTimestamp(EARLY).build());
      entityManager.persist(StructureCommentEntity.builder()
          .structureCommentId(2L).crossingStructureId(7L).structureComment("Bring a boat.")
          .plannedInspectionCmtInd("Y").updateUserid("IDIR\\B")
          .updateTimestamp(LocalDateTime.of(2020, 1, 1, 9, 0)).build());
      entityManager.persist(StructureCommentEntity.builder()
          .structureCommentId(3L).crossingStructureId(7L).structureComment("Use the UBIU.")
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
            .closeProximityInspectionId(id).crossingSiteId("62-001").crossingStructureId(7L)
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
}
