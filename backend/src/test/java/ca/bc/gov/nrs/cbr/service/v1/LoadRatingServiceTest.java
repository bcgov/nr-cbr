package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureLoadRatingRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * The rule for "what is this structure's current load rating", against a real database.
 *
 * <p>A port of {@code CBR.FIND_CRNT_LD_RATING_ID}, which is called from thirteen places in the
 * legacy package — so this is the definition of the rule for the whole application, and the cases
 * below are the ones the procedure distinguishes.
 *
 * <p>Against H2, so what these prove is the logic and the queries, not Oracle's behaviour. They do
 * <b>not</b> prove the port matches production data; that needs a comparison against real ratings
 * before this is trusted to rewrite a structure's capacity.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({LoadRatingService.class})
class LoadRatingServiceTest {

  private static final long STRUCTURE = 100L;

  @Autowired
  private LoadRatingService service;

  @Autowired
  private StructureLoadRatingRepository loadRatings;

  @Autowired
  private StructureInspectionRepository inspections;

  @Autowired
  private EntityManager entityManager;

  private long nextId = 1L;

  @BeforeEach
  void clear() {
    // STRUCTURE_INSPECTION has a real foreign key to CROSSING_STRUCTURE, so the parent has to be
    // there before any inspection can be.
    loadRatings.deleteAll();
    entityManager.createQuery("DELETE FROM InspectionReportStatusEntity").executeUpdate();
    inspections.deleteAll();
    entityManager.createQuery("DELETE FROM InspectionReportStatusCodeEntity").executeUpdate();
    nextId = 1L;
    if (entityManager.find(CrossingStructureEntity.class, STRUCTURE) == null) {
      entityManager.persist(CrossingStructureEntity.builder()
          .crossingStructureId(STRUCTURE)
          .crossingStructureName("BR000001")
          .structureTypeClassCode("BRDG")
          .closeProximityInd("N")
          .activeInd("Y")
          .build());
    }
  }

  /** A rating typed in by hand — no inspection behind it. */
  private StructureLoadRatingEntity givenManualRating(String rating, LocalDateTime enteredAt) {
    StructureLoadRatingEntity entity = StructureLoadRatingEntity.builder()
        .structureLoadRatingId(nextId++)
        .crossingStructureId(STRUCTURE)
        .loadRating(new BigDecimal(rating))
        .entryTimestamp(enteredAt)
        .build();
    entityManager.persist(entity);
    return entity;
  }

  /** An inspection with a status and, optionally, a load rating of its own. */
  private StructureLoadRatingEntity givenInspection(LocalDate date, String status, String rating) {
    long inspectionId = nextId++;
    entityManager.persist(StructureInspectionEntity.builder()
        .inspectionId(inspectionId)
        .crossingStructureId(STRUCTURE)
        .inspectionDate(date)
        .siteAtTimeOfInspection("site-1")
        .build());
    if (entityManager.find(InspectionReportStatusCodeEntity.class, status) == null) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(status)
          .description(status)
          .effectiveDate(LocalDateTime.now().minusYears(20))
          .expiryDate(LocalDateTime.now().plusYears(10))
          .updateTimestamp(LocalDateTime.now())
          .build());
    }
    entityManager.persist(InspectionReportStatusEntity.builder()
        .inspectionReportStatusId(nextId++)
        .inspectionId(inspectionId)
        .inspectionReportStatusCode(status)
        .entryUserid("IDIR\\TESTER")
        .entryTimestamp(LocalDateTime.now())
        .build());
    if (rating == null) {
      return null;
    }
    StructureLoadRatingEntity entity = StructureLoadRatingEntity.builder()
        .structureLoadRatingId(nextId++)
        .crossingStructureId(STRUCTURE)
        .inspectionId(inspectionId)
        .loadRating(new BigDecimal(rating))
        .entryTimestamp(date.atStartOfDay())
        .build();
    entityManager.persist(entity);
    return entity;
  }

  private void flush() {
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("a structure with no ratings at all has none")
  void noRatings() {
    assertThat(service.currentLoadRating(STRUCTURE)).isEmpty();
  }

  @Test
  @DisplayName("a manual rating wins when it is the only one")
  void onlyManual() {
    StructureLoadRatingEntity manual = givenManualRating("20", LocalDateTime.of(2026, 1, 1, 9, 0));
    flush();

    assertThat(service.currentLoadRating(STRUCTURE))
        .map(StructureLoadRatingEntity::getStructureLoadRatingId)
        .contains(manual.getStructureLoadRatingId());
  }

  @Test
  @DisplayName("the most recent manual rating wins over an older one")
  void latestManual() {
    givenManualRating("20", LocalDateTime.of(2024, 1, 1, 9, 0));
    StructureLoadRatingEntity newer =
        givenManualRating("30", LocalDateTime.of(2026, 1, 1, 9, 0));
    flush();

    assertThat(service.currentLoadRating(STRUCTURE))
        .map(StructureLoadRatingEntity::getLoadRating)
        .map(BigDecimal::intValue)
        .contains(newer.getLoadRating().intValue());
  }

  @Test
  @DisplayName("a reviewed inspection's rating wins when it is later than the manual one")
  void inspectionBeatsOlderManual() {
    givenManualRating("20", LocalDateTime.of(2024, 1, 1, 9, 0));
    StructureLoadRatingEntity fromInspection =
        givenInspection(LocalDate.of(2026, 6, 1), "RVD", "35");
    flush();

    assertThat(service.currentLoadRating(STRUCTURE))
        .map(StructureLoadRatingEntity::getStructureLoadRatingId)
        .contains(fromInspection.getStructureLoadRatingId());
  }

  @Test
  @DisplayName("a manual rating wins when it is later than the inspection")
  void manualBeatsOlderInspection() {
    givenInspection(LocalDate.of(2024, 6, 1), "RVD", "35");
    StructureLoadRatingEntity manual = givenManualRating("20", LocalDateTime.of(2026, 1, 1, 9, 0));
    flush();

    assertThat(service.currentLoadRating(STRUCTURE))
        .map(StructureLoadRatingEntity::getStructureLoadRatingId)
        .contains(manual.getStructureLoadRatingId());
  }

  @Test
  @DisplayName("on the same day the inspection wins, because legacy's test is strictly greater")
  void tiesGoToTheInspection() {
    // Legacy compares NVL(manualDate, 0001/01/01) > NVL(inspectionDate, 0001/01/01) and falls to
    // the inspection branch on equality. Ported as written: flipping it changes which rating a
    // structure shows on any day both were recorded.
    StructureLoadRatingEntity fromInspection =
        givenInspection(LocalDate.of(2026, 6, 1), "RVD", "35");
    givenManualRating("20", LocalDateTime.of(2026, 6, 1, 17, 0));
    flush();

    assertThat(service.currentLoadRating(STRUCTURE))
        .map(StructureLoadRatingEntity::getStructureLoadRatingId)
        .contains(fromInspection.getStructureLoadRatingId());
  }

  @Test
  @DisplayName("an inspection that is not terminal contributes nothing, however recent")
  void unreviewedInspectionIsIgnored() {
    StructureLoadRatingEntity manual = givenManualRating("20", LocalDateTime.of(2024, 1, 1, 9, 0));
    givenInspection(LocalDate.of(2026, 6, 1), "SUB", "35");
    flush();

    assertThat(service.currentLoadRating(STRUCTURE))
        .map(StructureLoadRatingEntity::getStructureLoadRatingId)
        .contains(manual.getStructureLoadRatingId());
  }

  @Test
  @DisplayName("ACC counts as terminal, because legacy and the LRM views both honour it")
  void acceptedCountsAsReviewed() {
    // ACC is expired and unsettable, but it still sits on rows nobody has re-saved, and the
    // outbound LRM views read it as terminal alongside RVD.
    StructureLoadRatingEntity fromInspection =
        givenInspection(LocalDate.of(2026, 6, 1), "ACC", "35");
    flush();

    assertThat(service.currentLoadRating(STRUCTURE))
        .map(StructureLoadRatingEntity::getStructureLoadRatingId)
        .contains(fromInspection.getStructureLoadRatingId());
  }

  @Test
  @DisplayName("a reviewed inspection that recorded no rating leaves the structure without one")
  void reviewedInspectionWithNoRating() {
    // Legacy raises NO_DATA_FOUND here and its handler turns that into -1 — "no rating" — even
    // though an older manual rating may exist. Ported as written.
    givenManualRating("20", LocalDateTime.of(2024, 1, 1, 9, 0));
    givenInspection(LocalDate.of(2026, 6, 1), "RVD", null);
    flush();

    assertThat(service.currentLoadRating(STRUCTURE)).isEmpty();
  }
}
