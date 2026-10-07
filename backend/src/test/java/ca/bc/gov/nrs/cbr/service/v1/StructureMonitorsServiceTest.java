package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitorFrequencyCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
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

/** The Monitoring tab's data, against the database: a filtered, ordered page and its decodes. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureMonitorsService.class)
class StructureMonitorsServiceTest {

  @Autowired
  private StructureMonitorsService service;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    for (String entity : List.of("StructureMonitorItemEntity", "StructureInspectionEntity",
        "MonitoringStatusCodeEntity", "MonitorFrequencyCodeEntity", "CrossingStructureEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
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
    return service.monitors(7L, view, 0, 10);
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

    PagedResponse<Monitor> second = service.monitors(7L, View.ALL, 1, 10);

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
  @DisplayName("refuses a structure that does not exist")
  void missingStructure() {
    assertThatThrownBy(() -> service.monitors(404L, View.ALL, 0, 10))
        .isInstanceOf(StructureNotFoundException.class);
  }
}
