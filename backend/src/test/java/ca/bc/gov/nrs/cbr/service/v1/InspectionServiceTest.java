package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.exception.InspectionNotOfflineException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileDetailRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureFileRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureLoadRatingRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureMonitorItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deleting an offline inspection, against a real database.
 *
 * <p>An integration test rather than a mocked one, because what is worth proving here is the things
 * mocks cannot see: that the eight deletes happen in an order the foreign keys permit, and that the
 * structure's load rating ends up correct afterwards. This is a port of
 * {@code CBR.DELETE_INSPECTION} rather than a call to it, so the cascade is ours to get right.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({InspectionService.class, LoadRatingService.class})
class InspectionServiceTest {

  private static final long STRUCTURE = 100L;
  private static final long INSPECTION = 42L;

  @Autowired
  private InspectionService service;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private StructureInspectionRepository inspections;
  @Autowired
  private CrossingStructureRepository structures;
  @Autowired
  private StructureLoadRatingRepository loadRatings;
  @Autowired
  private InspectionReportStatusRepository statusHistory;
  @Autowired
  private StructureRepairRepository repairs;
  @Autowired
  private StructureMonitorItemRepository monitorItems;
  @Autowired
  private StructureInspectionItemRepository inspectionItems;
  @Autowired
  private CrossingStructureFileRepository attachmentBytes;
  @Autowired
  private CrossingStructureFileDetailRepository attachments;

  private long nextId = 1000L;

  @BeforeEach
  void given() {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(STRUCTURE)
        .crossingStructureName("BR000001")
        .structureTypeClassCode("BRDG")
        .closeProximityInd("N")
        .activeInd("Y")
        .loadRatingUnknownIndicator("N")
        .currentLoadRating(new BigDecimal("35"))
        .build());
  }

  private void givenStatusCode(String code) {
    if (entityManager.find(InspectionReportStatusCodeEntity.class, code) == null) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(code).description(code)
          .effectiveDate(LocalDateTime.now().minusYears(20))
          .expiryDate(LocalDateTime.now().plusYears(10))
          .updateTimestamp(LocalDateTime.now()).build());
    }
  }

  private void givenInspection(long id, LocalDate date, String status) {
    entityManager.persist(StructureInspectionEntity.builder()
        .inspectionId(id)
        .crossingStructureId(STRUCTURE)
        .inspectionDate(date)
        .siteAtTimeOfInspection("site-1")
        .build());
    givenStatusCode(status);
    entityManager.persist(InspectionReportStatusEntity.builder()
        .inspectionReportStatusId(nextId++)
        .inspectionId(id)
        .inspectionReportStatusCode(status)
        .entryUserid("IDIR\\TESTER")
        .entryTimestamp(LocalDateTime.now())
        .build());
  }

  /** The offline inspection under test, with a finding of every kind hanging off it. */
  private void givenAnOfflineInspectionWithEverything() {
    givenInspection(INSPECTION, LocalDate.of(2026, 6, 15), "OFL");

    entityManager.persist(StructureMonitorItemEntity.builder()
        .monitorId(nextId++).inspectionId(INSPECTION).build());
    entityManager.persist(StructureRepairEntity.builder()
        .repairId(nextId++).inspectionId(INSPECTION).build());
    entityManager.persist(StructureInspectionItemEntity.builder()
        .structureInspectionItemId(nextId++).inspectionId(INSPECTION).build());
    entityManager.persist(StructureLoadRatingEntity.builder()
        .structureLoadRatingId(nextId++)
        .crossingStructureId(STRUCTURE)
        .inspectionId(INSPECTION)
        .loadRating(new BigDecimal("35"))
        .entryTimestamp(LocalDateTime.of(2026, 6, 15, 9, 0))
        .build());

    long fileId = nextId++;
    entityManager.persist(CrossingStructureFileDetailEntity.builder()
        .fileId(fileId).inspectionId(INSPECTION).build());
    entityManager.persist(CrossingStructureFileEntity.builder().fileId(fileId).build());
  }

  private void flush() {
    entityManager.flush();
    entityManager.clear();
  }

  @Nested
  @DisplayName("the guards")
  class Guards {

    @Test
    @DisplayName("404s when the inspection is already gone")
    void refusesAMissingInspection() {
      assertThatThrownBy(() -> service.delete(999L))
          .isInstanceOf(InspectionNotFoundException.class)
          .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
          .isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRO", "SUB", "RVD", "ACC", "REJ"})
    @DisplayName("409s on anything that is not offline — the check legacy does not make")
    void refusesAnythingNotOffline(String status) {
      // inspection_search.jsp renders the delete control only on an OFL row, and that is the only
      // place the rule exists: InspectionAction.delete() checks the id parses and then deletes.
      givenInspection(INSPECTION, LocalDate.of(2026, 6, 15), status);
      flush();

      assertThatThrownBy(() -> service.delete(INSPECTION))
          .isInstanceOf(InspectionNotOfflineException.class)
          .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
          .isEqualTo(409);
      assertThat(inspections.existsById(INSPECTION)).isTrue();
    }

    @Test
    @DisplayName("names the status it refused, because that is what the user can see")
    void namesTheStatus() {
      givenInspection(INSPECTION, LocalDate.of(2026, 6, 15), "RVD");
      flush();

      assertThatThrownBy(() -> service.delete(INSPECTION))
          .hasMessageContaining("is RVD")
          .hasMessageContaining("Only an offline inspection can be deleted");
    }

    @Test
    @DisplayName("refuses an inspection with no status history at all")
    void refusesAnInspectionWithNoStatus() {
      entityManager.persist(StructureInspectionEntity.builder()
          .inspectionId(INSPECTION).crossingStructureId(STRUCTURE)
          .siteAtTimeOfInspection("site-1").build());
      flush();

      assertThatThrownBy(() -> service.delete(INSPECTION))
          .isInstanceOf(InspectionNotOfflineException.class)
          .hasMessageContaining("has no status");
    }
  }

  @Nested
  @DisplayName("the cascade")
  class Cascade {

    @Test
    @DisplayName("removes the inspection and every row that hangs off it")
    void removesEverything() {
      givenAnOfflineInspectionWithEverything();
      flush();

      service.delete(INSPECTION);
      flush();

      assertThat(inspections.existsById(INSPECTION)).isFalse();
      assertThat(statusHistory.count()).isZero();
      assertThat(repairs.count()).isZero();
      assertThat(monitorItems.count()).isZero();
      assertThat(inspectionItems.count()).isZero();
      assertThat(loadRatings.count()).isZero();
      assertThat(attachments.count()).as("attachment metadata").isZero();
      assertThat(attachmentBytes.count()).as("attachment bytes").isZero();
    }

    @Test
    @DisplayName("leaves the parent structure standing")
    void keepsTheStructure() {
      // The inspection is deleted, not the thing it inspected.
      givenAnOfflineInspectionWithEverything();
      flush();

      service.delete(INSPECTION);
      flush();

      assertThat(structures.existsById(STRUCTURE)).isTrue();
    }

    @Test
    @DisplayName("deletes an inspection with no findings at all")
    void deletesABareInspection() {
      givenInspection(INSPECTION, LocalDate.of(2026, 6, 15), "OFL");
      flush();

      service.delete(INSPECTION);
      flush();

      assertThat(inspections.existsById(INSPECTION)).isFalse();
    }
  }

  @Nested
  @DisplayName("the load rating correction")
  class LoadRatingCorrection {

    @Test
    @DisplayName("falls back to the previous reviewed inspection's rating")
    void adoptsThePreviousRating() {
      // The structure's rating came from an older reviewed inspection; the offline one being
      // deleted never supplied it, so nothing about the structure should change.
      givenInspection(1L, LocalDate.of(2024, 1, 1), "RVD");
      entityManager.persist(StructureLoadRatingEntity.builder()
          .structureLoadRatingId(nextId++)
          .crossingStructureId(STRUCTURE).inspectionId(1L)
          .loadRating(new BigDecimal("20"))
          .entryTimestamp(LocalDateTime.of(2024, 1, 1, 9, 0)).build());
      givenAnOfflineInspectionWithEverything();
      flush();

      service.delete(INSPECTION);
      flush();

      CrossingStructureEntity structure = structures.findById(STRUCTURE).orElseThrow();
      assertThat(structure.getCurrentLoadRating()).isEqualByComparingTo("20");
      assertThat(structure.getLoadRatingUnknownIndicator()).isEqualTo("N");
    }

    @Test
    @DisplayName("nulls the rating when the structure has none left")
    void nullsTheRatingWhenNoneRemains() {
      givenAnOfflineInspectionWithEverything();
      flush();

      service.delete(INSPECTION);
      flush();

      assertThat(structures.findById(STRUCTURE).orElseThrow().getCurrentLoadRating()).isNull();
    }

    @Test
    @DisplayName("leaves the unknown-capacity flag alone, because that branch cannot be reached")
    void neverMarksTheCapacityUnknown() {
      // CBR.DELETE_INSPECTION sets LOAD_RATING_UNKNOWN_INDICATOR = 'Y' when the inspection being
      // deleted supplied the structure's current rating. That cannot happen here: a rating counts
      // as current only from an RVD/ACC inspection, and this service deletes only OFL ones. Even
      // for an inspection that was reviewed and then sent back offline — the closest case there
      // is — its rating has stopped counting by the time it becomes deletable.
      givenInspection(INSPECTION, LocalDate.of(2026, 6, 15), "RVD");
      entityManager.persist(StructureLoadRatingEntity.builder()
          .structureLoadRatingId(nextId++)
          .crossingStructureId(STRUCTURE).inspectionId(INSPECTION)
          .loadRating(new BigDecimal("35"))
          .entryTimestamp(LocalDateTime.of(2026, 6, 15, 9, 0)).build());
      // ...and has since been sent back offline, which is what makes it deletable.
      givenStatusCode("OFL");
      entityManager.persist(InspectionReportStatusEntity.builder()
          .inspectionReportStatusId(nextId++)
          .inspectionId(INSPECTION)
          .inspectionReportStatusCode("OFL")
          .entryUserid("IDIR\\TESTER")
          .entryTimestamp(LocalDateTime.now()).build());
      flush();

      service.delete(INSPECTION);
      flush();

      CrossingStructureEntity structure = structures.findById(STRUCTURE).orElseThrow();
      assertThat(structure.getCurrentLoadRating()).isNull();
      assertThat(structure.getLoadRatingUnknownIndicator()).isEqualTo("N");
    }
  }
}
