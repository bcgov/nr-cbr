package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.InspectionLockedException;
import ca.bc.gov.nrs.cbr.exception.InspectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import org.springframework.mock.web.MockMultipartFile;
import ca.bc.gov.nrs.cbr.struct.v1.DocumentDetailsRequest;
import ca.bc.gov.nrs.cbr.model.v1.FileMimeTypeXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.FileAttachmentTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureFileDetailEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitorFrequencyCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairUpdateRequest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Repairs and monitoring items changed from an inspection's page. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({InspectionFollowUpService.class, StructureRepairsService.class,
    StructureMonitorsService.class, StructureDocumentsService.class})
class InspectionFollowUpServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.of(2000, 1, 1, 0, 0);

  @Autowired
  private InspectionFollowUpService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  /** Passes every file: scanning is {@link VirusScanner}'s, tested there. */
  @MockitoBean
  private VirusScanner virusScanner;

  /** Files go nowhere: storing them is {@link StructureDocumentsService}'s, tested there. */
  @MockitoBean
  private ObjectStorageService storage;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\INSPECTOR");
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(7L).crossingStructureName("B7").structureTypeClassCode("TB")
        .activeInd("Y").closeProximityInd("N").portableStructureInd("N").build());
    for (String status : List.of("SUG", "REQ", "COM")) {
      entityManager.persist(RepairStatusCodeEntity.builder()
          .repairStatusCode(status).description(status).build());
      entityManager.persist(MonitoringStatusCodeEntity.builder()
          .monitoringStatusCode(status).description(status).build());
    }
    entityManager.persist(RepairPriorityCodeEntity.builder()
        .repairPriorityCode("H").description("High").build());
    entityManager.persist(StructureRepairTypeCodeEntity.builder()
        .structureRepairTypeCode("DECK").description("Deck").build());
    entityManager.persist(MonitorFrequencyCodeEntity.builder()
        .monitorFrequencyCode("ANN").description("Annual").build());
    entityManager.persist(FileAttachmentTypeCodeEntity.builder()
        .fileAttachmentTypeCode("PHOTO").description("Photograph").build());
    entityManager.persist(new FileMimeTypeXrefEntity(new FileMimeTypeXrefEntity.Key("JPG", "JPG")));
    for (String status : List.of("PRO", "SUB", "RVD", "ACC", "REJ")) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(status).description(status).effectiveDate(PAST)
          .expiryDate(PAST.plusYears(999)).updateTimestamp(PAST).build());
    }
  }

  private void inspection(long id, String status) {
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(id)
        .crossingStructureId(7L).inspectionDate(LocalDate.of(2024, 5, 1)).build());
    entityManager.persist(InspectionReportStatusEntity.builder()
        .inspectionReportStatusId(id * 10).inspectionId(id).inspectionReportStatusCode(status)
        .entryUserid("IDIR\\X").entryTimestamp(LocalDateTime.of(2024, 5, 1, 8, 0)).build());
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("adds a repair and a monitoring item raised by the inspection, Suggested, numbered next")
  void addsRaisedByTheInspection() {
    inspection(41L, "PRO");

    var repair = service.addRepair(41L, new RepairCreateRequest("H", null, "DECK", null, null));
    var monitor = service.addMonitor(41L, new MonitorCreateRequest("ANN", null, "Watch scour"));
    entityManager.flush();
    entityManager.clear();

    StructureRepairEntity storedRepair =
        entityManager.find(StructureRepairEntity.class, Long.valueOf(repair.id()));
    assertThat(storedRepair.getInspectionId()).isEqualTo(41L);
    assertThat(storedRepair.getCrossingStructureId()).isEqualTo(7L);
    assertThat(storedRepair.getRepairStatusCode()).isEqualTo("SUG");
    assertThat(repair.number()).isEqualTo(1L);
    assertThat(entityManager.find(StructureMonitorItemEntity.class, Long.valueOf(monitor.id()))
        .getInspectionId()).isEqualTo(41L);
  }

  @Test
  @DisplayName("edits and deletes any of the structure's repairs, as legacy")
  void editsAnyRepair() {
    inspection(41L, "SUB");
    entityManager.persist(StructureRepairEntity.builder().crossingStructureId(7L)
        .inspectionId(null).repairNumber(3L).repairStatusCode("SUG").repairPriorityCode("H")
        .structureRepairTypeCode("DECK").carriedForwardInd("N").build());
    entityManager.flush();
    long id = entityManager.createQuery("SELECT r.repairId FROM StructureRepairEntity r",
        Long.class).getSingleResult();

    service.updateRepair(41L, id, new RepairUpdateRequest("COM", "H", LocalDate.of(2024, 6, 1),
        null, 500L, "DECK", null, null));
    entityManager.flush();
    entityManager.clear();
    assertThat(entityManager.find(StructureRepairEntity.class, id).getRepairStatusCode())
        .isEqualTo("COM");

    service.deleteRepair(41L, id);
    entityManager.flush();
    assertThat(entityManager.find(StructureRepairEntity.class, id)).isNull();
  }

  @Test
  @DisplayName("edits and deletes any of the structure's monitoring items")
  void editsAnyMonitor() {
    inspection(41L, "PRO");
    long id = Long.parseLong(
        service.addMonitor(41L, new MonitorCreateRequest("ANN", null, "Watch scour")).id());

    service.updateMonitor(41L, id, new MonitorUpdateRequest("SUG", "ANN", null, "Watch the scour"));
    entityManager.flush();
    entityManager.clear();
    assertThat(entityManager.find(StructureMonitorItemEntity.class, id).getDescription())
        .isEqualTo("Watch the scour");

    service.deleteMonitor(41L, id);
    entityManager.flush();
    assertThat(entityManager.find(StructureMonitorItemEntity.class, id)).isNull();
  }

  @Test
  @DisplayName("refuses once the inspection is Reviewed or Rejected, and one that does not exist")
  void lockedAndMissing() {
    inspection(41L, "RVD");
    inspection(42L, "REJ");
    RepairCreateRequest repair = new RepairCreateRequest("H", null, "DECK", null, null);

    assertThatThrownBy(() -> service.addRepair(41L, repair))
        .isInstanceOf(InspectionLockedException.class)
        .hasMessageContaining("is Reviewed");
    assertThatThrownBy(() -> service.deleteMonitor(42L, 1L))
        .isInstanceOf(InspectionLockedException.class)
        .hasMessageContaining("is Rejected");
    assertThatThrownBy(() -> service.addRepair(404L, repair))
        .isInstanceOf(InspectionNotFoundException.class);
  }

  private static MockMultipartFile photo() {
    return new MockMultipartFile("file", "deck.jpg", "image/jpeg", "jpeg bytes".getBytes());
  }

  private static DocumentDetailsRequest details() {
    return new DocumentDetailsRequest("PHOTO", LocalDate.of(2024, 5, 1), "North abutment");
  }

  @Test
  @DisplayName("adds a document as the inspection's, and deletes any of the structure's")
  void documents() {
    inspection(41L, "SUB");

    long id = Long.parseLong(service.addDocument(41L, photo(), details()));
    entityManager.flush();
    entityManager.clear();
    assertThat(entityManager.find(CrossingStructureFileDetailEntity.class, id).getInspectionId())
        .isEqualTo(41L);

    service.deleteDocument(41L, id);
    entityManager.flush();
    entityManager.clear();
    assertThat(entityManager.find(CrossingStructureFileDetailEntity.class, id)).isNull();
  }

  @Test
  @DisplayName("as legacy: no document added once Reviewed or Accepted, but one may be once "
      + "Rejected, and deleted at any status")
  void documentsLockAsLegacy() {
    inspection(41L, "RVD");
    inspection(42L, "ACC");
    inspection(43L, "REJ");

    // Built first, so each refusal comes from the one call under test.
    MockMultipartFile photo = photo();
    DocumentDetailsRequest details = details();
    assertThatThrownBy(() -> service.addDocument(41L, photo, details))
        .isInstanceOf(InspectionLockedException.class)
        .hasMessageContaining("documents and photos can no longer be added");
    assertThatThrownBy(() -> service.addDocument(42L, photo, details))
        .isInstanceOf(InspectionLockedException.class);
    long id = Long.parseLong(service.addDocument(43L, photo(), details()));

    service.deleteDocument(41L, id);
    entityManager.flush();
    entityManager.clear();
    assertThat(entityManager.find(CrossingStructureFileDetailEntity.class, id)).isNull();
  }
}
