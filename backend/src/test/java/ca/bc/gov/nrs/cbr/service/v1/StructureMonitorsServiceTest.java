package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.MonitorNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitorFrequencyCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse.Monitor;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse.View;
import ca.bc.gov.nrs.cbr.struct.v1.UserAudit;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The Monitoring tab's data, against the database: a filtered, ordered page and its decodes. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureMonitorsService.class)
class StructureMonitorsServiceTest {

  @Autowired
  private StructureMonitorsService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    for (String entity : List.of("StructureMonitorItemEntity", "StructureInspectionEntity",
        "MonitoringStatusCodeEntity", "MonitorFrequencyCodeEntity", "CrossingStructureEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(7L).crossingStructureName("B7").structureTypeClassCode("TB")
        .activeInd("Y").closeProximityInd("N").portableStructureInd("N").build());
  }

  private void givenInspection(long id, LocalDate date) {
    entityManager.persist(StructureInspectionEntity.builder()
        .inspectionId(id).crossingStructureId(7L).inspectionDate(date).build());
  }

  private void givenMonitor(long id, Long inspectionId, String status,
      Consumer<StructureMonitorItemEntity.StructureMonitorItemEntityBuilder> change) {
    StructureMonitorItemEntity.StructureMonitorItemEntityBuilder monitor =
        StructureMonitorItemEntity.builder()
            .monitorId(id).crossingStructureId(7L).inspectionId(inspectionId).monitorNumber(id)
            .monitoringStatusCode(status).description("Item " + id);
    change.accept(monitor);
    entityManager.persist(monitor.build());
  }

  private void givenMonitor(long id, Long inspectionId, String status) {
    givenMonitor(id, inspectionId, status, monitor -> { });
  }

  private PagedResponse<Monitor> monitors(View view) {
    entityManager.flush();
    entityManager.clear();
    return service.monitors(7L, view, 0, 10, false).page();
  }

  @Test
  @DisplayName("lists suggested and required items by default, as FIND_OUTSTANDING_STRC_MONITORS "
      + "does")
  void outstandingOnly() {
    givenMonitor(1L, null, "SUG");
    givenMonitor(2L, null, "REQ");
    givenMonitor(3L, null, "COMP");

    assertThat(monitors(View.OUTSTANDING).content()).extracting(Monitor::id)
        .containsExactlyInAnyOrder("1", "2");
    assertThat(monitors(View.ALL).content()).hasSize(3);
  }

  @Test
  @DisplayName("orders as legacy does: status descending, then no inspection first and newest "
      + "inspection, then number descending")
  void legacyOrder() {
    givenInspection(10L, LocalDate.of(2020, 6, 1));
    givenInspection(11L, LocalDate.of(2023, 6, 1));
    givenMonitor(1L, 10L, "SUG");
    givenMonitor(2L, 11L, "SUG");
    givenMonitor(3L, 11L, "SUG");
    givenMonitor(4L, null, "SUG");
    givenMonitor(5L, 10L, "REQ");

    // The status code sorts as text, so SUG comes before REQ descending. Within SUG: no inspection,
    // then 2023 by number descending, then 2020.
    assertThat(monitors(View.ALL).content()).extracting(Monitor::id)
        .containsExactly("4", "3", "2", "1", "5");
  }

  @Test
  @DisplayName("pages on the server")
  void pages() {
    for (long id = 1; id <= 12; id++) {
      givenMonitor(id, null, "REQ");
    }
    entityManager.flush();
    entityManager.clear();

    PagedResponse<Monitor> second = service.monitors(7L, View.ALL, 1, 10, false).page();

    assertThat(second.content()).extracting(Monitor::number).containsExactly(2L, 1L);
    assertThat(second.totalElements()).isEqualTo(12);
  }

  @Test
  @DisplayName("carries each item's decoded status and frequency, and who moved it along")
  void rowDetails() {
    entityManager.persist(MonitoringStatusCodeEntity.builder()
        .monitoringStatusCode("REQ").description("Required").build());
    entityManager.persist(MonitorFrequencyCodeEntity.builder()
        .monitorFrequencyCode("ANN").description("Annually").build());
    givenInspection(10L, LocalDate.of(2023, 6, 1));
    givenMonitor(1L, 10L, "REQ", monitor -> monitor
        .description("Watch the scour at the south abutment.")
        .monitorFrequencyCode("ANN").monitorFrequencyCmt("After freshet.")
        .requiredByUserid("IDIR\\B").requiredByTimestamp(LocalDateTime.of(2023, 6, 5, 9, 0)));

    Monitor monitor = monitors(View.ALL).content().getFirst();

    assertThat(monitor.status()).isEqualTo(new CodeValue("REQ", "Required"));
    assertThat(monitor.frequency()).isEqualTo(new CodeValue("ANN", "Annually"));
    assertThat(monitor.frequencyComment()).isEqualTo("After freshet.");
    assertThat(monitor.description()).isEqualTo("Watch the scour at the south abutment.");
    assertThat(monitor.inspectionDate()).isEqualTo(LocalDate.of(2023, 6, 1));
    assertThat(monitor.suggested()).isNull();
    assertThat(monitor.required()).isEqualTo(new UserAudit("IDIR\\B", LocalDate.of(2023, 6, 5)));
  }

  @Test
  @DisplayName("leaves out items from inspections on or before the install year, and counts them, "
      + "as legacy does until its box is ticked")
  void beforeInstallLeftOutAndCounted() {
    entityManager.createQuery(
        "UPDATE CrossingStructureEntity s SET s.yearBuilt = 2010 WHERE s.crossingStructureId = 7")
        .executeUpdate();
    givenInspection(10L, LocalDate.of(2005, 6, 1));
    givenInspection(11L, LocalDate.of(2010, 1, 1));
    givenInspection(12L, LocalDate.of(2015, 6, 1));
    givenMonitor(1L, 10L, "REQ");
    givenMonitor(2L, 11L, "REQ");
    givenMonitor(3L, 12L, "REQ");
    givenMonitor(4L, null, "REQ");
    entityManager.flush();
    entityManager.clear();

    var shown = service.monitors(7L, View.ALL, 0, 10, false);
    var all = service.monitors(7L, View.ALL, 0, 10, true);

    // 1 January of the install year is not "after" it; no inspection is always listed.
    assertThat(shown.page().content()).extracting(Monitor::id).containsExactlyInAnyOrder("3", "4");
    assertThat(shown.page().totalElements()).isEqualTo(2);
    assertThat(shown.beforeInstallCount()).isEqualTo(2);
    assertThat(all.page().content()).hasSize(4);
  }

  @Test
  @DisplayName("leaves nothing out when the structure has no install year, as legacy does")
  void noInstallYear() {
    givenInspection(10L, LocalDate.of(1990, 6, 1));
    givenMonitor(1L, 10L, "REQ");
    entityManager.flush();
    entityManager.clear();

    var listing = service.monitors(7L, View.ALL, 0, 10, false);

    assertThat(listing.page().content()).hasSize(1);
    assertThat(listing.beforeInstallCount()).isZero();
  }

  /** The statuses and frequencies the dropdowns offer. */
  private void givenCodes() {
    for (String status : List.of("SUG", "REQ", "COM")) {
      entityManager.persist(MonitoringStatusCodeEntity.builder()
          .monitoringStatusCode(status).description(status).build());
    }
    for (String frequency : List.of("ANN", "OTH")) {
      entityManager.persist(MonitorFrequencyCodeEntity.builder()
          .monitorFrequencyCode(frequency).description(frequency).build());
    }
  }

  private StructureMonitorItemEntity stored(long id) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(StructureMonitorItemEntity.class, id);
  }

  @Test
  @DisplayName("saves an edit, stamping the status it now has with the user, keeping the others")
  void updates() {
    givenCodes();
    givenMonitor(1L, null, "SUG", monitor -> monitor
        .suggestedByUserid("IDIR\\A").suggestedByTimestamp(LocalDateTime.of(2023, 6, 2, 9, 0)));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, 1L, new MonitorUpdateRequest("REQ", "ANN", "ignored", "  Scour, south.  "));

    StructureMonitorItemEntity monitor = stored(1L);
    assertThat(monitor.getMonitoringStatusCode()).isEqualTo("REQ");
    assertThat(monitor.getMonitorFrequencyCode()).isEqualTo("ANN");
    assertThat(monitor.getMonitorFrequencyCmt()).as("kept only with Other").isNull();
    assertThat(monitor.getDescription()).isEqualTo("Scour, south.");
    assertThat(monitor.getRequiredByUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(monitor.getRequiredByTimestamp()).isNotNull();
    assertThat(monitor.getSuggestedByUserid()).isEqualTo("IDIR\\A");
    assertThat(monitor.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(monitor.getMonitorNumber()).isEqualTo(1L);
  }

  @Test
  @DisplayName("re-stamps the status on every save, even when it did not change, as legacy does")
  void restampsEverySave() {
    givenCodes();
    givenMonitor(1L, null, "REQ", monitor -> monitor
        .requiredByUserid("IDIR\\B").requiredByTimestamp(LocalDateTime.of(2020, 1, 1, 9, 0)));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, 1L, new MonitorUpdateRequest("REQ", null, null, "Typo fixed."));

    StructureMonitorItemEntity monitor = stored(1L);
    assertThat(monitor.getRequiredByUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(monitor.getRequiredByTimestamp()).isAfter(LocalDateTime.of(2020, 1, 1, 9, 0));
  }

  @Test
  @DisplayName("keeps the frequency comment with Other, and requires it there")
  void otherNeedsItsComment() {
    givenCodes();
    givenMonitor(1L, null, "SUG");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(() -> service.update(7L, 1L,
        new MonitorUpdateRequest("SUG", "OTH", " ", "Scour.")))
        .isInstanceOfSatisfying(FieldValidationException.class, failure ->
            assertThat(failure.getFieldErrors()).containsOnlyKeys("frequencyComment"));

    service.update(7L, 1L, new MonitorUpdateRequest("SUG", "OTH", "After freshet.", "Scour."));

    assertThat(stored(1L).getMonitorFrequencyCmt()).isEqualTo("After freshet.");
  }

  @Test
  @DisplayName("refuses a missing description, an over-long one, and codes that do not exist")
  void refusesBadFields() {
    givenCodes();
    givenMonitor(1L, null, "SUG");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(() -> service.update(7L, 1L,
        new MonitorUpdateRequest("NOPE", "NOPE", null, " ")))
        .isInstanceOfSatisfying(FieldValidationException.class, failure ->
            assertThat(failure.getFieldErrors())
                .containsEntry("statusCode", "Monitoring Status is not one of the listed statuses.")
                .containsEntry("frequencyCode",
                    "Monitoring Frequency is not one of the listed frequencies.")
                .containsEntry("description", "Monitor Description is required."));
    assertThatThrownBy(() -> service.update(7L, 1L,
        new MonitorUpdateRequest("SUG", null, null, "x".repeat(2001))))
        .isInstanceOfSatisfying(FieldValidationException.class, failure ->
            assertThat(failure.getFieldErrors()).containsEntry("description",
                "Monitor Description can be at most 2000 characters."));
  }

  @Test
  @DisplayName("refuses to edit an item under a structure it does not belong to")
  void updateOnlyThroughItsStructure() {
    givenCodes();
    givenMonitor(1L, null, "SUG");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(() -> service.update(8L, 1L,
        new MonitorUpdateRequest("SUG", null, null, "Scour.")))
        .isInstanceOf(MonitorNotFoundException.class);
  }

  @Test
  @DisplayName("deletes one item of the structure, leaving the others")
  void deletes() {
    givenMonitor(1L, null, "REQ");
    givenMonitor(2L, null, "REQ");
    entityManager.flush();
    entityManager.clear();

    service.delete(7L, 1L);
    entityManager.flush();
    entityManager.clear();

    assertThat(service.monitors(7L, View.ALL, 0, 10, false).page().content())
        .extracting(Monitor::id).containsExactly("2");
  }

  @Test
  @DisplayName("refuses to delete an item under a structure it does not belong to, or none at all")
  void deleteOnlyThroughItsStructure() {
    givenMonitor(1L, null, "REQ");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(() -> service.delete(8L, 1L))
        .isInstanceOf(MonitorNotFoundException.class);
    assertThatThrownBy(() -> service.delete(7L, 99L))
        .isInstanceOf(MonitorNotFoundException.class);
  }

  @Test
  @DisplayName("refuses a structure that does not exist")
  void missingStructure() {
    assertThatThrownBy(() -> service.monitors(404L, View.ALL, 0, 10, false).page())
        .isInstanceOf(StructureNotFoundException.class);
  }
}
