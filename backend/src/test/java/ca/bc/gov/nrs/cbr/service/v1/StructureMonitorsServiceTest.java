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
import ca.bc.gov.nrs.cbr.struct.v1.HeaderSort;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorSortColumn;
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
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

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

  /** The superstructure went in in 2000: earlier inspections are hidden by default. */
  private void givenStructureBuilt() {
    entityManager.flush();
    entityManager.createQuery("UPDATE CrossingStructureEntity s SET s.yearBuilt = 2000")
        .executeUpdate();
  }

  private void givenInspection(long id, LocalDate date) {
    entityManager.persist(StructureInspectionEntity.builder()
        .inspectionId(id).crossingStructureId(7L).inspectionDate(date).build());
  }

  /**
   * An item of structure 7, numbered {@code number}. Its id comes from the sequence, as an item's
   * does in production; it is returned for the tests that act on the item.
   */
  private long givenMonitor(long number, Long inspectionId, String status,
      Consumer<StructureMonitorItemEntity.StructureMonitorItemEntityBuilder> change) {
    StructureMonitorItemEntity.StructureMonitorItemEntityBuilder monitor =
        StructureMonitorItemEntity.builder()
            .crossingStructureId(7L).inspectionId(inspectionId).monitorNumber(number)
            .monitoringStatusCode(status).description("Item " + number);
    change.accept(monitor);
    StructureMonitorItemEntity built = monitor.build();
    entityManager.persist(built);
    return built.getMonitorId();
  }

  private long givenMonitor(long number, Long inspectionId, String status) {
    return givenMonitor(number, inspectionId, status, monitor -> { });
  }

  private PagedResponse<Monitor> monitors(View view) {
    entityManager.flush();
    entityManager.clear();
    return service.monitors(7L, view, null, 0, 10, false, List.of()).page();
  }

  @Test
  @DisplayName("from an inspection's page, Current Inspection Items lists every item it raised, "
      + "however early it was, as legacy's getCurrentMonitors")
  void currentInspection() {
    givenStructureBuilt();
    givenInspection(10L, LocalDate.of(1990, 6, 1));
    givenInspection(11L, LocalDate.of(2023, 6, 1));
    givenMonitor(1L, 10L, "REQ");
    givenMonitor(2L, 10L, "COMP");
    givenMonitor(3L, 11L, "REQ");
    entityManager.flush();
    entityManager.clear();

    var listing = service.monitors(7L, View.CURRENT, 10L, 0, 10, false, List.of());
    assertThat(listing.page().content()).extracting(Monitor::number)
        .containsExactlyInAnyOrder(1L, 2L);
    assertThat(listing.beforeInstallCount()).isZero();
    assertThatThrownBy(() -> service.monitors(7L, View.CURRENT, null, 0,
        10, false, List.of()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("lists suggested and required items by default, as FIND_OUTSTANDING_STRC_MONITORS "
      + "does")
  void outstandingOnly() {
    givenMonitor(1L, null, "SUG");
    givenMonitor(2L, null, "REQ");
    givenMonitor(3L, null, "COMP");

    assertThat(monitors(View.OUTSTANDING).content()).extracting(Monitor::number)
        .containsExactlyInAnyOrder(1L, 2L);
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
    assertThat(monitors(View.ALL).content()).extracting(Monitor::number)
        .containsExactly(4L, 3L, 2L, 1L, 5L);
  }

  @Test
  @DisplayName("pages on the server")
  void pages() {
    for (long id = 1; id <= 12; id++) {
      givenMonitor(id, null, "REQ");
    }
    entityManager.flush();
    entityManager.clear();

    PagedResponse<Monitor> second = service.monitors(7L, View.ALL, null, 1,
        10, false, List.of()).page();

    assertThat(second.content()).extracting(Monitor::number).containsExactly(2L, 1L);
    assertThat(second.totalElements()).isEqualTo(12);
  }

  /** The numbers of every item's first page, sorted by a header. */
  private List<Long> sortedBy(MonitorSortColumn column, Sort.Direction direction) {
    entityManager.flush();
    entityManager.clear();
    return service.monitors(7L, View.ALL, null, 0, 10, false,
            List.of(new HeaderSort<>(column, direction))).page().content()
        .stream().map(Monitor::number).toList();
  }

  private void givenFrequency(String code, String description) {
    entityManager.persist(MonitorFrequencyCodeEntity.builder()
        .monitorFrequencyCode(code).description(description).build());
  }

  @Test
  @DisplayName("sorts by a header's shown value, both ways, legacy's order breaking the ties")
  void sortsByHeader() {
    // Described against their codes' order, so a sort by code would fail.
    entityManager.persist(MonitoringStatusCodeEntity.builder()
        .monitoringStatusCode("SUG").description("awaiting review").build());
    entityManager.persist(MonitoringStatusCodeEntity.builder()
        .monitoringStatusCode("REQ").description("Required").build());
    givenMonitor(1L, null, "REQ");
    givenMonitor(2L, null, "SUG");
    givenMonitor(3L, null, "REQ");

    // Case-blind. The two Required tie, and legacy's number order (descending) settles them
    // whichever way the header sorts.
    assertThat(sortedBy(MonitorSortColumn.STATUS, Sort.Direction.ASC))
        .containsExactly(2L, 3L, 1L);
    assertThat(sortedBy(MonitorSortColumn.STATUS, Sort.Direction.DESC))
        .containsExactly(3L, 1L, 2L);
  }

  @Test
  @DisplayName("sorts Monitoring Frequency by its description, then the comment shown after it")
  void sortsByFrequencyThenComment() {
    givenFrequency("ANN", "Annually");
    givenFrequency("OTH", "Other");
    givenMonitor(1L, null, "REQ",
        monitor -> monitor.monitorFrequencyCode("OTH").monitorFrequencyCmt("Every spring"));
    givenMonitor(2L, null, "REQ",
        monitor -> monitor.monitorFrequencyCode("OTH").monitorFrequencyCmt("After floods"));
    givenMonitor(3L, null, "REQ", monitor -> monitor.monitorFrequencyCode("ANN"));

    assertThat(sortedBy(MonitorSortColumn.FREQUENCY, Sort.Direction.ASC))
        .containsExactly(3L, 2L, 1L);
  }

  @Test
  @DisplayName("sorts every item on the server, not only the page on screen")
  void sortsAcrossPages() {
    for (long id = 1; id <= 12; id++) {
      givenMonitor(id, null, "REQ");
    }

    // Legacy's own order is by number descending; ascending proves the header decides.
    assertThat(sortedBy(MonitorSortColumn.NUMBER, Sort.Direction.ASC))
        .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(MonitorSortColumn.class)
  @DisplayName("every header sorts both ways and still lists every item")
  void everyHeaderSorts(MonitorSortColumn column) {
    entityManager.persist(MonitoringStatusCodeEntity.builder()
        .monitoringStatusCode("REQ").description("Required").build());
    givenFrequency("ANN", "Annually");
    givenInspection(10L, LocalDate.of(2023, 6, 1));
    givenMonitor(1L, 10L, "REQ", monitor -> monitor.monitorFrequencyCode("ANN")
        .requiredByTimestamp(LocalDateTime.of(2024, 1, 1, 9, 0)));
    givenMonitor(2L, null, "REQ");

    for (Sort.Direction direction : Sort.Direction.values()) {
      assertThat(sortedBy(column, direction)).containsExactlyInAnyOrder(1L, 2L);
    }
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

    var shown = service.monitors(7L, View.ALL, null, 0, 10, false, List.of());
    var all = service.monitors(7L, View.ALL, null, 0, 10, true, List.of());

    // 1 January of the install year is not "after" it; no inspection is always listed.
    assertThat(shown.page().content()).extracting(Monitor::number)
        .containsExactlyInAnyOrder(3L, 4L);
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

    var listing = service.monitors(7L, View.ALL, null, 0, 10, false, List.of());

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
    long id = givenMonitor(1L, null, "SUG", monitor -> monitor
        .suggestedByUserid("IDIR\\A").suggestedByTimestamp(LocalDateTime.of(2023, 6, 2, 9, 0)));
    entityManager.flush();
    entityManager.clear();
    when(loggedUser.isInspectionReviewer()).thenReturn(true);

    service.update(7L, id, new MonitorUpdateRequest("REQ", "ANN", "ignored", "  Scour, south.  "));

    StructureMonitorItemEntity monitor = stored(id);
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
    long id = givenMonitor(1L, null, "REQ", monitor -> monitor
        .requiredByUserid("IDIR\\B").requiredByTimestamp(LocalDateTime.of(2020, 1, 1, 9, 0)));
    entityManager.flush();
    entityManager.clear();
    when(loggedUser.isInspectionReviewer()).thenReturn(true);

    service.update(7L, id, new MonitorUpdateRequest("REQ", null, null, "Typo fixed."));

    StructureMonitorItemEntity monitor = stored(id);
    assertThat(monitor.getRequiredByUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(monitor.getRequiredByTimestamp()).isAfter(LocalDateTime.of(2020, 1, 1, 9, 0));
  }

  @Test
  @DisplayName("lets only an inspection reviewer set Required or Not Required, as legacy's list does")
  void reviewerStatuses() {
    givenCodes();
    long id = givenMonitor(1L, null, "SUG");
    entityManager.flush();
    entityManager.clear();

    MonitorUpdateRequest required = new MonitorUpdateRequest("REQ", null, null, "Scour.");
    assertThatThrownBy(() -> service.update(7L, id, required))
        .isInstanceOfSatisfying(FieldValidationException.class, failure ->
            assertThat(failure.getFieldErrors()).containsExactly(Map.entry("statusCode",
                "Only an inspection reviewer can set Monitoring Status to Required or Not "
                    + "Required.")));
  }

  @Test
  @DisplayName("keeps the frequency comment with Other, and requires it there")
  void otherNeedsItsComment() {
    givenCodes();
    long id = givenMonitor(1L, null, "SUG");
    entityManager.flush();
    entityManager.clear();

    MonitorUpdateRequest request = new MonitorUpdateRequest("SUG", "OTH", " ", "Scour.");
    assertThatThrownBy(() -> service.update(7L, id, request))
        .isInstanceOfSatisfying(FieldValidationException.class, failure ->
            assertThat(failure.getFieldErrors()).containsOnlyKeys("frequencyComment"));

    service.update(7L, id, new MonitorUpdateRequest("SUG", "OTH", "After freshet.", "Scour."));

    assertThat(stored(id).getMonitorFrequencyCmt()).isEqualTo("After freshet.");
  }

  @Test
  @DisplayName("refuses a missing description, an over-long one, and codes that do not exist")
  void refusesBadFields() {
    givenCodes();
    long id = givenMonitor(1L, null, "SUG");
    entityManager.flush();
    entityManager.clear();

    MonitorUpdateRequest unknown = new MonitorUpdateRequest("NOPE", "NOPE", null, " ");
    assertThatThrownBy(() -> service.update(7L, id, unknown))
        .isInstanceOfSatisfying(FieldValidationException.class, failure ->
            assertThat(failure.getFieldErrors())
                .containsEntry("statusCode", "Monitoring Status is not one of the listed statuses.")
                .containsEntry("frequencyCode",
                    "Monitoring Frequency is not one of the listed frequencies.")
                .containsEntry("description", "Monitor Description is required."));
    MonitorUpdateRequest tooLong =
        new MonitorUpdateRequest("SUG", null, null, "x".repeat(2001));
    assertThatThrownBy(() -> service.update(7L, id, tooLong))
        .isInstanceOfSatisfying(FieldValidationException.class, failure ->
            assertThat(failure.getFieldErrors()).containsEntry("description",
                "Monitor Description can be at most 2000 characters."));
  }

  @Test
  @DisplayName("refuses to edit an item under a structure it does not belong to")
  void updateOnlyThroughItsStructure() {
    givenCodes();
    long id = givenMonitor(1L, null, "SUG");
    entityManager.flush();
    entityManager.clear();

    MonitorUpdateRequest request = new MonitorUpdateRequest("SUG", null, null, "Scour.");
    assertThatThrownBy(() -> service.update(8L, id, request))
        .isInstanceOf(MonitorNotFoundException.class);
  }

  @Test
  @DisplayName("adds an item as Suggested, by the user, numbered after the structure's highest")
  void creates() {
    givenCodes();
    givenMonitor(1L, null, "REQ");
    givenMonitor(2L, null, "COM", monitor -> monitor.monitorNumber(5L));
    entityManager.flush();
    entityManager.clear();

    MonitorCreatedResponse created =
        service.create(7L, null, new MonitorCreateRequest("OTH", "After freshet.", " Scour. "));

    assertThat(created.number()).isEqualTo(6L);
    StructureMonitorItemEntity monitor = stored(Long.parseLong(created.id()));
    assertThat(monitor.getCrossingStructureId()).isEqualTo(7L);
    assertThat(monitor.getMonitoringStatusCode()).isEqualTo("SUG");
    assertThat(monitor.getMonitorFrequencyCode()).isEqualTo("OTH");
    assertThat(monitor.getMonitorFrequencyCmt()).isEqualTo("After freshet.");
    assertThat(monitor.getDescription()).isEqualTo("Scour.");
    assertThat(monitor.getInspectionId()).isNull();
    assertThat(monitor.getSuggestedByUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(monitor.getSuggestedByTimestamp()).isNotNull();
    assertThat(monitor.getRequiredByUserid()).isNull();
    assertThat(monitor.getEntryUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(monitor.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
  }

  @Test
  @DisplayName("numbers a structure's first item 1, and takes each id from the sequence")
  void firstItemAndDistinctIds() {
    givenCodes();
    entityManager.flush();

    MonitorCreatedResponse first = service.create(7L, null, new MonitorCreateRequest(null, null, "A."));
    MonitorCreatedResponse second = service.create(7L, null, new MonitorCreateRequest(null, null, "B."));

    assertThat(first.number()).isEqualTo(1L);
    assertThat(second.number()).isEqualTo(2L);
    assertThat(second.id()).isNotEqualTo(first.id());
  }

  @Test
  @DisplayName("refuses an item with no description, and adds nothing")
  void createRefusesBadFields() {
    givenCodes();
    entityManager.flush();

    MonitorCreateRequest request = new MonitorCreateRequest("OTH", null, " ");
    assertThatThrownBy(() -> service.create(7L, null, request))
        .isInstanceOfSatisfying(FieldValidationException.class, failure ->
            assertThat(failure.getFieldErrors())
                .containsOnlyKeys("frequencyComment", "description"));
    assertThat(service.monitors(7L, View.ALL, null, 0,
        10, true, List.of()).page().content()).isEmpty();
  }

  @Test
  @DisplayName("refuses to add an item to a structure that does not exist")
  void createOnMissingStructure() {
    MonitorCreateRequest request = new MonitorCreateRequest(null, null, "A.");
    assertThatThrownBy(() -> service.create(404L, null, request))
        .isInstanceOf(StructureNotFoundException.class);
  }

  @Test
  @DisplayName("deletes one item of the structure, leaving the others")
  void deletes() {
    long id = givenMonitor(1L, null, "REQ");
    givenMonitor(2L, null, "REQ");
    entityManager.flush();
    entityManager.clear();

    service.delete(7L, id);
    entityManager.flush();
    entityManager.clear();

    assertThat(service.monitors(7L, View.ALL, null, 0,
        10, false, List.of()).page().content())
        .extracting(Monitor::number).containsExactly(2L);
  }

  @Test
  @DisplayName("refuses to delete an item under a structure it does not belong to, or none at all")
  void deleteOnlyThroughItsStructure() {
    long id = givenMonitor(1L, null, "REQ");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(() -> service.delete(8L, id))
        .isInstanceOf(MonitorNotFoundException.class);
    assertThatThrownBy(() -> service.delete(7L, id + 1_000))
        .isInstanceOf(MonitorNotFoundException.class);
  }

  @Test
  @DisplayName("refuses a structure that does not exist")
  void missingStructure() {
    assertThatThrownBy(() -> service.monitors(404L, View.ALL, null, 0,
        10, false, List.of()))
        .isInstanceOf(StructureNotFoundException.class);
  }
}
