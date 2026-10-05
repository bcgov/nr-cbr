package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.MaintainerNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureInUseException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.ClientLocationEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CloseProximityInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureReplacementXrefEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityResponse;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

/**
 * Archiving and deleting structures. Against the database, because the statements are the feature
 * and the delete cannot be undone.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({StructureService.class, StructureDeleteBlockers.class})
class StructureServiceTest {

  private static final String ARCHIVER = "IDIR\\ARCHIVER";
  private static final LocalDateTime LAST_YEAR = LocalDateTime.of(2025, 3, 1, 9, 0);

  @Autowired
  private StructureService service;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @Autowired
  private CrossingStructureRepository structures;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    for (String entity : List.of("StructureInspectionEntity", "CrossingStructureFileDetailEntity",
        "StructureRepairEntity", "StructureMonitorItemEntity", "CloseProximityInspectionEntity",
        "StructureReplacementXrefEntity", "StructureLoadRatingEntity", "ForestServiceBridgeEntity",
        "ForestServiceCulvertEntity", "CrossingStructureEntity", "CrossingSiteEntity",
        "ClientLocationEntity", "ClientPublicEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (String table : List.of("FOREST_SERVICE_BRIDGE_PIER", "FOREST_SERVICE_BRIDGE_SPAN",
        "STRUCTURE_COMMENT", "CROSSING_STRUCTURE_NAME_HIST")) {
      entityManager.createNativeQuery("DELETE FROM THE." + table).executeUpdate();
    }
    when(loggedUser.getLoggedUserId()).thenReturn(ARCHIVER);
  }

  private void givenStructure(long id, String activeInd) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(id)
        .crossingStructureName("BR" + id)
        .activeInd(activeInd)
        .updateUserid("IDIR\\EARLIER")
        .updateTimestamp(LAST_YEAR)
        .build());
  }

  private CrossingStructureEntity reread(long id) {
    entityManager.flush();
    entityManager.clear();
    return structures.findById(id).orElseThrow();
  }

  @Test
  @DisplayName("archives each structure, and records who did it and when")
  void archivesAndStamps() {
    givenStructure(1L, "Y");
    givenStructure(2L, "Y");
    LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    assertThat(service.archive(List.of(1L, 2L)).archivedCount()).isEqualTo(2);

    for (long id : new long[] {1L, 2L}) {
      CrossingStructureEntity structure = reread(id);
      assertThat(structure.getActiveInd()).isEqualTo("N");
      // Legacy passed back the values it had read, so its archive never recorded the archiver.
      assertThat(structure.getUpdateUserid()).isEqualTo(ARCHIVER);
      assertThat(structure.getUpdateTimestamp()).isAfterOrEqualTo(before);
    }
  }

  @Test
  @DisplayName("leaves a structure that was not asked for alone")
  void touchesNothingElse() {
    givenStructure(1L, "Y");
    givenStructure(2L, "Y");

    service.archive(List.of(1L));

    CrossingStructureEntity untouched = reread(2L);
    assertThat(untouched.getActiveInd()).isEqualTo("Y");
    assertThat(untouched.getUpdateUserid()).isEqualTo("IDIR\\EARLIER");
    assertThat(untouched.getUpdateTimestamp()).isEqualTo(LAST_YEAR);
  }

  @Test
  @DisplayName("archives an already-archived structure again, and counts it, as legacy does")
  void reArchives() {
    givenStructure(1L, "N");

    assertThat(service.archive(List.of(1L)).archivedCount()).isEqualTo(1);

    CrossingStructureEntity structure = reread(1L);
    assertThat(structure.getActiveInd()).isEqualTo("N");
    assertThat(structure.getUpdateUserid()).isEqualTo(ARCHIVER);
  }

  @Test
  @DisplayName("skips an id with no structure, and does not count it")
  void skipsMissing() {
    givenStructure(1L, "Y");

    assertThat(service.archive(List.of(1L, 999L)).archivedCount()).isEqualTo(1);
    assertThat(reread(1L).getActiveInd()).isEqualTo("N");
  }

  @Test
  @DisplayName("counts a structure ticked twice once")
  void countsDuplicatesOnce() {
    givenStructure(1L, "Y");

    assertThat(service.archive(List.of(1L, 1L)).archivedCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("archives more than Oracle's thousand-item IN list, in batches")
  void batchesLargeSelections() {
    int count = StructureService.IN_LIST_LIMIT + 5;
    LongStream.rangeClosed(1, count).forEach(id -> givenStructure(id, "Y"));

    List<Long> ids = LongStream.rangeClosed(1, count).boxed().toList();
    assertThat(service.archive(ids).archivedCount()).isEqualTo(count);
    assertThat(reread(count).getActiveInd()).isEqualTo("N");
    assertThat(reread(1L).getActiveInd()).isEqualTo("N");
  }

  /* ------------------------------------------------------------------ delete */

  private void sql(String statement) {
    entityManager.createNativeQuery(statement).executeUpdate();
  }

  private long count(String table, String where) {
    return ((Number) entityManager.createNativeQuery(
        "SELECT COUNT(*) FROM THE." + table + " WHERE " + where).getSingleResult()).longValue();
  }

  /** A bridge with everything a delete clears: rating, pier, span, comment and name history. */
  private void givenBridgeWithItsParts(long id) {
    givenStructure(id, "Y");
    entityManager.persist(StructureLoadRatingEntity.builder()
        .structureLoadRatingId(id * 10).crossingStructureId(id).build());
    entityManager.persist(ForestServiceBridgeEntity.builder()
        .forestServiceBridgeId(id * 10).crossingStructureId(id).build());
    entityManager.flush();
    sql("INSERT INTO THE.FOREST_SERVICE_BRIDGE_PIER VALUES (" + id * 10 + ", " + id * 10 + ")");
    sql("INSERT INTO THE.FOREST_SERVICE_BRIDGE_SPAN VALUES (" + id * 10 + ", " + id * 10 + ")");
    sql("INSERT INTO THE.STRUCTURE_COMMENT VALUES (" + id * 10 + ", " + id + ")");
    sql("INSERT INTO THE.CROSSING_STRUCTURE_NAME_HIST VALUES ('OLD" + id + "', " + id + ")");
  }

  @Test
  @DisplayName("deletes a structure and everything legacy deletes with it")
  void deletesWithItsParts() {
    givenBridgeWithItsParts(1L);

    service.delete(1L);
    entityManager.flush();

    assertThat(count("CROSSING_STRUCTURE", "CROSSING_STRUCTURE_ID = 1")).isZero();
    assertThat(count("STRUCTURE_LOAD_RATING", "CROSSING_STRUCTURE_ID = 1")).isZero();
    assertThat(count("FOREST_SERVICE_BRIDGE", "CROSSING_STRUCTURE_ID = 1")).isZero();
    assertThat(count("FOREST_SERVICE_BRIDGE_PIER", "FOREST_SERVICE_BRIDGE_ID = 10")).isZero();
    assertThat(count("FOREST_SERVICE_BRIDGE_SPAN", "FOREST_SERVICE_BRIDGE_ID = 10")).isZero();
    assertThat(count("STRUCTURE_COMMENT", "CROSSING_STRUCTURE_ID = 1")).isZero();
    assertThat(count("CROSSING_STRUCTURE_NAME_HIST", "CROSSING_STRUCTURE_ID = 1")).isZero();
  }

  @Test
  @DisplayName("deletes a culvert's culvert record")
  void deletesTheCulvert() {
    givenStructure(1L, "Y");
    entityManager.persist(ForestServiceCulvertEntity.builder()
        .forestServiceCulvertId(10L).crossingStructureId(1L).build());
    entityManager.flush();

    service.delete(1L);
    entityManager.flush();

    assertThat(count("FOREST_SERVICE_CULVERT", "CROSSING_STRUCTURE_ID = 1")).isZero();
    assertThat(count("CROSSING_STRUCTURE", "CROSSING_STRUCTURE_ID = 1")).isZero();
  }

  @Test
  @DisplayName("leaves every other structure and its parts alone")
  void deletesOnlyThatOne() {
    givenBridgeWithItsParts(1L);
    givenBridgeWithItsParts(2L);

    service.delete(1L);
    entityManager.flush();

    assertThat(count("CROSSING_STRUCTURE", "CROSSING_STRUCTURE_ID = 2")).isEqualTo(1);
    assertThat(count("FOREST_SERVICE_BRIDGE_PIER", "FOREST_SERVICE_BRIDGE_ID = 20")).isEqualTo(1);
    assertThat(count("CROSSING_STRUCTURE_NAME_HIST", "CROSSING_STRUCTURE_ID = 2")).isEqualTo(1);
  }

  @Test
  @DisplayName("answers 404 for a structure that is gone")
  void refusesMissing() {
    assertThatThrownBy(() -> service.delete(999L))
        .isInstanceOf(StructureNotFoundException.class)
        .satisfies(failure -> assertThat(((ResponseStatusException) failure).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  @DisplayName("refuses a structure with inspections, naming them, and deletes nothing")
  void refusesWithInspections() {
    givenBridgeWithItsParts(1L);
    entityManager.persist(StructureInspectionEntity.builder()
        .inspectionId(100L).crossingStructureId(1L).build());
    entityManager.flush();

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOf(StructureInUseException.class)
        .hasMessageContaining("Structure BR1 has inspections and cannot be deleted.")
        .satisfies(failure -> assertThat(((ResponseStatusException) failure).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT));
    assertThat(count("CROSSING_STRUCTURE", "CROSSING_STRUCTURE_ID = 1")).isEqualTo(1);
    assertThat(count("FOREST_SERVICE_BRIDGE_PIER", "FOREST_SERVICE_BRIDGE_ID = 10")).isEqualTo(1);
  }

  @Test
  @DisplayName("names every kind of record in the way")
  void namesEveryBlocker() {
    givenStructure(1L, "Y");
    entityManager.persist(CrossingStructureFileDetailEntity.builder()
        .fileId(1L).crossingStructureId(1L).build());
    entityManager.persist(StructureRepairEntity.builder()
        .repairId(1L).crossingStructureId(1L).build());
    entityManager.persist(StructureMonitorItemEntity.builder()
        .monitorId(1L).crossingStructureId(1L).build());
    entityManager.flush();

    assertThatThrownBy(() -> service.delete(1L)).hasMessageContaining(
        "has documents or photos, repairs and monitors and cannot be deleted.");
  }

  @Test
  @DisplayName("refuses what legacy did not check: close proximity and replacement links")
  void refusesWhatLegacyMissed() {
    givenStructure(1L, "Y");
    givenStructure(2L, "Y");
    givenStructure(3L, "Y");
    entityManager.persist(CloseProximityInspectionEntity.builder()
        .closeProximityInspectionId(1L).crossingStructureId(1L).build());
    entityManager.persist(StructureReplacementXrefEntity.builder()
        .replacedStructureNumber(2L).replacesStructureNumber(3L).build());
    entityManager.flush();

    assertThatThrownBy(() -> service.delete(1L))
        .hasMessageContaining("has close proximity inspections and");
    // Either side of the link holds the other down.
    assertThatThrownBy(() -> service.delete(2L)).hasMessageContaining("has a replacement record");
    assertThatThrownBy(() -> service.delete(3L)).hasMessageContaining("has a replacement record");
  }

  @Test
  @DisplayName("joins the names of what is in the way as a sentence")
  void joinsLabels() {
    assertThat(StructureService.joined(List.of("repairs"))).isEqualTo("repairs");
    assertThat(StructureService.joined(List.of("repairs", "monitors")))
        .isEqualTo("repairs and monitors");
    assertThat(StructureService.joined(List.of("inspections", "repairs", "monitors")))
        .isEqualTo("inspections, repairs and monitors");
  }

  /* ------------------------------------------------------------------ repair responsibility */

  private void givenMaintainer(String number, String location) {
    entityManager.persist(ClientPublicEntity.builder()
        .clientNumber(number).clientName("CLIENT " + number).build());
    entityManager.persist(ClientLocationEntity.builder()
        .clientNumber(number).clientLocnCode(location).city("Prince George").build());
  }

  private void givenSiteMaintainedBy(String siteId, String number, String location) {
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId(siteId).capitalRoadInd("N")
        .clientNumber(number).clientLocnCode(location)
        .updateUserid("IDIR\\EARLIER").updateTimestamp(LAST_YEAR).build());
  }

  private void givenStructureOn(long id, String siteId, String activeInd) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName("BR" + id).crossingSiteId(siteId)
        .activeInd(activeInd).updateUserid("IDIR\\EARLIER").updateTimestamp(LAST_YEAR).build());
  }

  private StructureRepairResponsibilityResponse reassign(List<Long> ids) {
    return service.updateRepairResponsibility(
        new StructureRepairResponsibilityRequest(ids, "00001012", "01"));
  }

  private CrossingSiteEntity site(String siteId) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(CrossingSiteEntity.class, siteId);
  }

  @Test
  @DisplayName("sets the maintainer of each ticked structure's site, stamped with who and when")
  void setsTheSitesMaintainer() {
    givenMaintainer("00001012", "01");
    givenSiteMaintainedBy("SITE-1", "00000001", "00");
    givenStructureOn(1L, "SITE-1", "Y");

    StructureRepairResponsibilityResponse response = reassign(List.of(1L));

    assertThat(response.structureCount()).isEqualTo(1);
    assertThat(response.siteCount()).isEqualTo(1);
    CrossingSiteEntity site = site("SITE-1");
    assertThat(site.getClientNumber()).isEqualTo("00001012");
    assertThat(site.getClientLocnCode()).isEqualTo("01");
    assertThat(site.getUpdateUserid()).isEqualTo(ARCHIVER);
    assertThat(site.getUpdateTimestamp()).isAfter(LAST_YEAR);
  }

  @Test
  @DisplayName("updates a site once, however many of its structures were ticked")
  void countsEachSiteOnce() {
    givenMaintainer("00001012", "01");
    givenSiteMaintainedBy("SITE-1", "00000001", "00");
    givenStructureOn(1L, "SITE-1", "Y");
    givenStructureOn(2L, "SITE-1", "Y");

    StructureRepairResponsibilityResponse response = reassign(List.of(1L, 2L));

    assertThat(response.structureCount()).isEqualTo(2);
    assertThat(response.siteCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("leaves a site none of whose structures were ticked alone")
  void touchesOnlyTickedSites() {
    givenMaintainer("00001012", "01");
    givenSiteMaintainedBy("SITE-1", "00000001", "00");
    givenSiteMaintainedBy("SITE-2", "00000001", "00");
    givenStructureOn(1L, "SITE-1", "Y");
    givenStructureOn(2L, "SITE-2", "Y");

    reassign(List.of(1L));

    CrossingSiteEntity untouched = site("SITE-2");
    assertThat(untouched.getClientNumber()).isEqualTo("00000001");
    assertThat(untouched.getUpdateUserid()).isEqualTo("IDIR\\EARLIER");
  }

  @Test
  @DisplayName("stamps each ticked structure and, as legacy's save did, restores an archived one")
  void stampsAndRestoresStructures() {
    givenMaintainer("00001012", "01");
    givenSiteMaintainedBy("SITE-1", "00000001", "00");
    givenStructureOn(1L, "SITE-1", "N");

    reassign(List.of(1L));

    CrossingStructureEntity structure = reread(1L);
    assertThat(structure.getActiveInd()).isEqualTo("Y");
    assertThat(structure.getUpdateUserid()).isEqualTo(ARCHIVER);
    assertThat(structure.getUpdateTimestamp()).isAfter(LAST_YEAR);
  }

  @Test
  @DisplayName("skips a structure that is gone or stands on no site — legacy crashed on both")
  void skipsMissingAndSiteless() {
    givenMaintainer("00001012", "01");
    givenStructureOn(1L, null, "Y");

    StructureRepairResponsibilityResponse response = reassign(List.of(1L, 999L));

    assertThat(response.structureCount()).isZero();
    assertThat(response.siteCount()).isZero();
    assertThat(reread(1L).getUpdateUserid()).isEqualTo("IDIR\\EARLIER");
  }

  @Test
  @DisplayName("refuses a maintainer that does not exist, and changes nothing")
  void refusesUnknownMaintainer() {
    givenSiteMaintainedBy("SITE-1", "00000001", "00");
    givenStructureOn(1L, "SITE-1", "Y");

    assertThatThrownBy(() -> reassign(List.of(1L)))
        .isInstanceOf(MaintainerNotFoundException.class)
        .hasMessageContaining("Designated maintainer 00001012-01 does not exist.")
        .satisfies(failure -> assertThat(((ResponseStatusException) failure).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
    assertThat(site("SITE-1").getClientNumber()).isEqualTo("00000001");
  }
}
