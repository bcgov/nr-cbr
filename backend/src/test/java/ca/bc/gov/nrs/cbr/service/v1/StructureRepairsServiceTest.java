package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.RepairNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeOrderEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeOrderEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeXrefEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.HeaderSort;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairSortColumn;
import ca.bc.gov.nrs.cbr.struct.v1.RepairTypeOption;
import ca.bc.gov.nrs.cbr.struct.v1.RepairUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse.Repair;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse.View;
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

/** The Repairs tab's data, against the database: a filtered, ordered page and its decodes. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureRepairsService.class)
class StructureRepairsServiceTest {

  @Autowired
  private StructureRepairsService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    for (String entity : List.of("StructureRepairEntity", "StructureInspectionEntity",
        "RepairPriorityCodeEntity", "RepairPriorityCodeOrderEntity", "RepairStatusCodeEntity",
        "StructureRepairTypeCodeEntity",
        "StructureRepairTypeOrderEntity", "StructureRepairTypeXrefEntity",
        "CrossingStructureEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
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

  /** A priority's rank in {@code REPAIR_PRIORITY_CODE_ORDER}, keyed by the rank itself. */
  private void givenPriorityRank(String priority, int rank) {
    entityManager.persist(RepairPriorityCodeOrderEntity.builder()
        .repairPriorityCodeOrderId(rank).repairPriorityCode(priority).repairPriorityCodeOrder(rank)
        .build());
  }

  /** A repair numbered {@code number}; its id, from the sequence, is returned. */
  private long givenRepair(long number, Long inspectionId, String status, String priority,
      Consumer<StructureRepairEntity.StructureRepairEntityBuilder> change) {
    StructureRepairEntity.StructureRepairEntityBuilder repair = StructureRepairEntity.builder()
        .crossingStructureId(7L).inspectionId(inspectionId).repairNumber(number)
        .repairStatusCode(status).repairPriorityCode(priority).carriedForwardInd("N");
    change.accept(repair);
    StructureRepairEntity built = repair.build();
    entityManager.persist(built);
    return built.getRepairId();
  }

  private long givenRepair(long number, Long inspectionId, String status, String priority) {
    return givenRepair(number, inspectionId, status, priority, repair -> { });
  }

  private PagedResponse<Repair> repairs(View view) {
    entityManager.flush();
    entityManager.clear();
    return service.repairs(7L, view, null, 0, 10, false, List.of()).page();
  }

  @Test
  @DisplayName("lists outstanding repairs by default: suggested, required or carried forward, "
      + "and not carried forward again, as FIND_OUTSTANDING_STRC_REPAIRS does")
  void outstandingOnly() {
    givenRepair(1L, null, "SUG", "P1");
    givenRepair(2L, null, "REQ", "P1");
    givenRepair(3L, null, "CF", "P1");
    givenRepair(4L, null, "COMP", "P1");
    givenRepair(5L, null, "REQ", "P1", repair -> repair.carriedForwardInd("Y"));

    assertThat(repairs(View.OUTSTANDING).content()).extracting(Repair::number)
        .containsExactlyInAnyOrder(1L, 2L, 3L);
    assertThat(repairs(View.ALL).content()).hasSize(5);
  }

  @Test
  @DisplayName("from an inspection's page, Current Inspection Items lists its outstanding repairs, "
      + "however early it was, as legacy's getCurrentRepairs")
  void currentInspection() {
    givenStructureBuilt();
    givenInspection(10L, LocalDate.of(1990, 6, 1));
    givenInspection(11L, LocalDate.of(2023, 6, 1));
    givenRepair(1L, 10L, "REQ", "P1");
    givenRepair(2L, 10L, "COMP", "P1");
    givenRepair(3L, 11L, "REQ", "P1");
    entityManager.flush();
    entityManager.clear();

    var listing = service.repairs(7L, View.CURRENT, 10L, 0, 10, false, List.of());
    assertThat(listing.page().content()).extracting(Repair::number).containsExactly(1L);
    assertThat(listing.beforeInstallCount()).isZero();
    assertThatThrownBy(() -> service.repairs(7L, View.CURRENT, null, 0,
        10, false, List.of()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("orders as legacy does: no inspection first, then newest inspection, then "
      + "priority rank, status descending, number")
  void legacyOrder() {
    // Ranked against their codes' order, so a sort by code would fail.
    givenPriorityRank("P2", 1);
    givenPriorityRank("P1", 2);
    givenPriorityRank("P3", 3);
    givenInspection(10L, LocalDate.of(2020, 6, 1));
    givenInspection(11L, LocalDate.of(2023, 6, 1));
    givenRepair(1L, 10L, "REQ", "P1");
    givenRepair(2L, 11L, "REQ", "P2");
    givenRepair(3L, 11L, "REQ", "P1");
    givenRepair(4L, 11L, "SUG", "P1");
    givenRepair(5L, null, "REQ", "P3");
    // No rank: after the ranked ones, where legacy's inner join dropped it.
    givenRepair(6L, 11L, "REQ", "P9");

    assertThat(repairs(View.ALL).content()).extracting(Repair::number)
        .containsExactly(5L, 2L, 4L, 3L, 6L, 1L);
  }

  @Test
  @DisplayName("pages on the server")
  void pages() {
    for (long id = 1; id <= 12; id++) {
      givenRepair(id, null, "REQ", "P1");
    }
    entityManager.flush();
    entityManager.clear();

    PagedResponse<Repair> second = service.repairs(7L, View.ALL, null, 1,
        10, false, List.of()).page();

    assertThat(second.content()).extracting(Repair::number).containsExactly(11L, 12L);
    assertThat(second.totalElements()).isEqualTo(12);
    assertThat(second.totalPages()).isEqualTo(2);
  }

  /** The numbers of every repair's first page, sorted by a header. */
  private List<Long> sortedBy(RepairSortColumn column, Sort.Direction direction) {
    entityManager.flush();
    entityManager.clear();
    return service.repairs(7L, View.ALL, null, 0, 10, false,
            List.of(new HeaderSort<>(column, direction))).page().content()
        .stream().map(Repair::number).toList();
  }

  private void givenStatus(String code, String description) {
    entityManager.persist(RepairStatusCodeEntity.builder()
        .repairStatusCode(code).description(description).build());
  }

  @Test
  @DisplayName("sorts by a header's shown value, both ways, legacy's order breaking the ties")
  void sortsByHeader() {
    // Described against their codes' order, so a sort by code would fail.
    givenStatus("SUG", "awaiting review");
    givenStatus("REQ", "Required");
    givenStatus("COM", "Completed");
    givenRepair(1L, null, "SUG", "P1");
    givenRepair(2L, null, "REQ", "P1");
    givenRepair(3L, null, "COM", "P1");
    givenRepair(4L, null, "REQ", "P1");

    // Case-blind, so the lower-case one is first. The two Required tie, and legacy's number order
    // settles them whichever way the header sorts.
    assertThat(sortedBy(RepairSortColumn.STATUS, Sort.Direction.ASC))
        .containsExactly(1L, 3L, 2L, 4L);
    assertThat(sortedBy(RepairSortColumn.STATUS, Sort.Direction.DESC))
        .containsExactly(2L, 4L, 3L, 1L);
  }

  @Test
  @DisplayName("sorts by several headers, each breaking the ties of the one before")
  void sortsBySeveralHeaders() {
    givenStatus("SUG", "Suggested");
    givenStatus("REQ", "Required");
    givenRepair(1L, null, "SUG", "P1");
    givenRepair(2L, null, "REQ", "P1");
    givenRepair(3L, null, "SUG", "P1");
    givenRepair(4L, null, "REQ", "P1");
    entityManager.flush();
    entityManager.clear();

    // Status alone leaves each pair in legacy's number order; Repair Number descending second
    // reverses each pair.
    assertThat(service.repairs(7L, View.ALL, null, 0, 10, false, List.of(
            new HeaderSort<>(RepairSortColumn.STATUS, Sort.Direction.ASC),
            new HeaderSort<>(RepairSortColumn.NUMBER, Sort.Direction.DESC)))
        .page().content()).extracting(Repair::number).containsExactly(4L, 2L, 3L, 1L);
  }

  @Test
  @DisplayName("sorts Priority by its rank and User Audits by the latest step taken")
  void sortsByRankAndLatestStep() {
    givenPriorityRank("P2", 1);
    givenPriorityRank("P1", 2);
    givenRepair(1L, null, "SUG", "P1",
        repair -> repair.suggestedByTimestamp(LocalDateTime.of(2026, 1, 1, 9, 0)));
    givenRepair(2L, null, "REQ", "P2",
        repair -> repair.suggestedByTimestamp(LocalDateTime.of(2025, 1, 1, 9, 0))
            .requiredByTimestamp(LocalDateTime.of(2026, 6, 1, 9, 0)));
    givenRepair(3L, null, "COM", "P1",
        repair -> repair.suggestedByTimestamp(LocalDateTime.of(2023, 1, 1, 9, 0))
            .completedByTimestamp(LocalDateTime.of(2024, 3, 1, 9, 0)));

    assertThat(sortedBy(RepairSortColumn.PRIORITY, Sort.Direction.ASC))
        .containsExactly(2L, 1L, 3L);
    assertThat(sortedBy(RepairSortColumn.USER_AUDITS, Sort.Direction.ASC))
        .containsExactly(3L, 1L, 2L);
  }

  @Test
  @DisplayName("sorts every repair on the server, not only the page on screen")
  void sortsAcrossPages() {
    for (long id = 1; id <= 12; id++) {
      givenRepair(id, null, "REQ", "P1");
    }

    assertThat(sortedBy(RepairSortColumn.NUMBER, Sort.Direction.DESC))
        .containsExactly(12L, 11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(RepairSortColumn.class)
  @DisplayName("every header sorts both ways and still lists every repair")
  void everyHeaderSorts(RepairSortColumn column) {
    givenStatus("REQ", "Required");
    entityManager.persist(StructureRepairTypeCodeEntity.builder()
        .structureRepairTypeCode("DECK").description("Deck planks").build());
    givenPriorityRank("P1", 1);
    givenInspection(10L, LocalDate.of(2023, 6, 1));
    givenRepair(1L, 10L, "REQ", "P1", repair -> repair.structureRepairTypeCode("DECK")
        .estimate(500L).actualCost(450L).repairQuantity(2L).description("Bolts")
        .completedDate(LocalDate.of(2024, 1, 1)));
    givenRepair(2L, null, "REQ", "P9");

    for (Sort.Direction direction : Sort.Direction.values()) {
      assertThat(sortedBy(column, direction)).containsExactlyInAnyOrder(1L, 2L);
    }
  }

  @Test
  @DisplayName("carries each repair's decoded codes, unit, costs and who moved it along")
  void rowDetails() {
    entityManager.persist(RepairPriorityCodeEntity.builder()
        .repairPriorityCode("P1").description("Urgent").build());
    entityManager.persist(RepairStatusCodeEntity.builder()
        .repairStatusCode("COMP").description("Completed").build());
    entityManager.persist(StructureRepairTypeCodeEntity.builder()
        .structureRepairTypeCode("DECK").description("Deck planks").build());
    entityManager.persist(StructureRepairTypeOrderEntity.builder()
        .structureRepairTypeCode("DECK").structureRepairUnit("m2").build());
    givenInspection(10L, LocalDate.of(2023, 6, 1));
    givenRepair(1L, 10L, "COMP", "P1", repair -> repair
        .structureRepairTypeCode("DECK").repairQuantity(12L).estimate(4000L).actualCost(3800L)
        .completedDate(LocalDate.of(2023, 9, 1)).description("Replace worn planks.")
        .suggestedByUserid("IDIR\\A").suggestedByTimestamp(LocalDateTime.of(2023, 6, 2, 9, 0))
        .completedByUserid("IDIR\\C").completedByTimestamp(LocalDateTime.of(2023, 9, 2, 9, 0)));

    Repair repair = repairs(View.ALL).content().getFirst();

    assertThat(repair.status()).isEqualTo(new CodeValue("COMP", "Completed"));
    assertThat(repair.priority()).isEqualTo(new CodeValue("P1", "Urgent"));
    assertThat(repair.type()).isEqualTo(new CodeValue("DECK", "Deck planks"));
    assertThat(repair.unit()).isEqualTo("m2");
    assertThat(repair.quantity()).isEqualTo(12L);
    assertThat(repair.estimate()).isEqualTo(4000L);
    assertThat(repair.actualCost()).isEqualTo(3800L);
    assertThat(repair.inspectionDate()).isEqualTo(LocalDate.of(2023, 6, 1));
    assertThat(repair.completedDate()).isEqualTo(LocalDate.of(2023, 9, 1));
    assertThat(repair.suggested()).isEqualTo(new UserAudit("IDIR\\A", LocalDate.of(2023, 6, 2)));
    assertThat(repair.required()).isNull();
    assertThat(repair.completed()).isEqualTo(new UserAudit("IDIR\\C", LocalDate.of(2023, 9, 2)));
    assertThat(repair.description()).isEqualTo("Replace worn planks.");
  }

  @Test
  @DisplayName("keeps a repair whose code has no row, which legacy's inner joins would drop")
  void unknownCodes() {
    givenRepair(1L, null, "GONE", "GONE");

    Repair repair = repairs(View.ALL).content().getFirst();

    assertThat(repair.status()).isEqualTo(new CodeValue("GONE", null));
    assertThat(repair.priority()).isEqualTo(new CodeValue("GONE", null));
  }

  @Test
  @DisplayName("leaves out repairs from inspections on or before the install year, and counts "
      + "them, as legacy does until its box is ticked")
  void beforeInstallLeftOutAndCounted() {
    entityManager.createQuery(
        "UPDATE CrossingStructureEntity s SET s.yearBuilt = 2010 WHERE s.crossingStructureId = 7")
        .executeUpdate();
    givenInspection(10L, LocalDate.of(2005, 6, 1));
    givenInspection(11L, LocalDate.of(2010, 1, 1));
    givenInspection(12L, LocalDate.of(2015, 6, 1));
    givenRepair(1L, 10L, "REQ", "P1");
    givenRepair(2L, 11L, "REQ", "P1");
    givenRepair(3L, 12L, "REQ", "P1");
    givenRepair(4L, null, "REQ", "P1");
    entityManager.flush();
    entityManager.clear();

    var shown = service.repairs(7L, View.ALL, null, 0, 10, false, List.of());
    var all = service.repairs(7L, View.ALL, null, 0, 10, true, List.of());

    // 1 January of the install year is not "after" it; no inspection is always listed.
    assertThat(shown.page().content()).extracting(Repair::number)
        .containsExactlyInAnyOrder(3L, 4L);
    assertThat(shown.page().totalElements()).isEqualTo(2);
    assertThat(shown.beforeInstallCount()).isEqualTo(2);
    assertThat(all.page().content()).hasSize(4);
  }

  @Test
  @DisplayName("leaves nothing out when the structure has no install year, as legacy does")
  void noInstallYear() {
    givenInspection(10L, LocalDate.of(1990, 6, 1));
    givenRepair(1L, 10L, "REQ", "P1");
    entityManager.flush();
    entityManager.clear();

    var listing = service.repairs(7L, View.ALL, null, 0, 10, false, List.of());

    assertThat(listing.page().content()).hasSize(1);
    assertThat(listing.beforeInstallCount()).isZero();
  }

  @Test
  @DisplayName("deletes one repair of the structure, carried forward or not, leaving the others")
  void deletes() {
    givenInspection(10L, LocalDate.of(2023, 6, 1));
    long carried = givenRepair(1L, 10L, "CF", "P1", repair -> repair.carriedForwardInd("Y"));
    givenRepair(2L, null, "REQ", "P1");
    entityManager.flush();
    entityManager.clear();

    service.delete(7L, carried);
    entityManager.flush();
    entityManager.clear();

    assertThat(service.repairs(7L, View.ALL, null, 0,
        10, true, List.of()).page().content())
        .extracting(Repair::number).containsExactly(2L);
  }

  @Test
  @DisplayName("refuses to delete a repair under a structure it does not belong to, or none at all")
  void deleteOnlyThroughItsStructure() {
    long id = givenRepair(1L, null, "REQ", "P1");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(() -> service.delete(8L, id))
        .isInstanceOf(RepairNotFoundException.class);
    assertThatThrownBy(() -> service.delete(7L, id + 1000))
        .isInstanceOf(RepairNotFoundException.class);
  }

  /** The codes an edit is checked against. */
  private void givenCodes() {
    for (String status : List.of("SUG", "REQ", "COM", "NRQ", "CF")) {
      entityManager.persist(RepairStatusCodeEntity.builder()
          .repairStatusCode(status).description(status).build());
    }
    entityManager.persist(RepairPriorityCodeEntity.builder()
        .repairPriorityCode("H").description("High").build());
    for (String type : List.of("DECK", "800A")) {
      entityManager.persist(StructureRepairTypeCodeEntity.builder()
          .structureRepairTypeCode(type).description(type).build());
    }
  }

  private static RepairUpdateRequest edit(String status, LocalDate completedDate,
      Long actualCost) {
    return new RepairUpdateRequest(status, "H", completedDate, 4000L, actualCost, "DECK", 12L,
        "  Replace planks.  ");
  }

  private StructureRepairEntity stored(long id) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(StructureRepairEntity.class, id);
  }

  private Map<String, String> refused(long id, RepairUpdateRequest request) {
    entityManager.flush();
    entityManager.clear();
    try {
      service.update(7L, id, request);
    } catch (FieldValidationException refused) {
      return refused.getFieldErrors();
    }
    throw new AssertionError("the edit was saved");
  }

  @Test
  @DisplayName("saves an edit, stamps the audit for its status and the update, and keeps the "
      + "number and the carried-forward flag")
  void updates() {
    givenCodes();
    long id = givenRepair(1L, null, "SUG", "L", repair -> repair.carriedForwardInd("Y")
        .suggestedByUserid("IDIR\\A").suggestedByTimestamp(LocalDateTime.of(2020, 1, 1, 9, 0)));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, id, edit("COM", LocalDate.of(2026, 9, 1), 3800L));

    StructureRepairEntity repair = stored(id);
    assertThat(repair.getRepairStatusCode()).isEqualTo("COM");
    assertThat(repair.getRepairPriorityCode()).isEqualTo("H");
    assertThat(repair.getCompletedDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(repair.getActualCost()).isEqualTo(3800L);
    assertThat(repair.getEstimate()).isEqualTo(4000L);
    assertThat(repair.getStructureRepairTypeCode()).isEqualTo("DECK");
    assertThat(repair.getRepairQuantity()).isEqualTo(12L);
    assertThat(repair.getDescription()).isEqualTo("Replace planks.");
    assertThat(repair.getRepairNumber()).isEqualTo(1L);
    assertThat(repair.getCarriedForwardInd()).isEqualTo("Y");
    assertThat(repair.getCompletedByUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(repair.getCompletedByTimestamp()).isNotNull();
    assertThat(repair.getSuggestedByUserid()).isEqualTo("IDIR\\A");
    assertThat(repair.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(repair.getUpdateTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("re-stamps the status's audit on every save, even when the status is unchanged")
  void restampsEverySave() {
    givenCodes();
    long id = givenRepair(1L, null, "SUG", "H", repair -> repair
        .suggestedByUserid("IDIR\\A").suggestedByTimestamp(LocalDateTime.of(2020, 1, 1, 9, 0)));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, id, edit("SUG", null, null));

    assertThat(stored(id).getSuggestedByUserid()).isEqualTo("IDIR\\EDITOR");
  }

  @Test
  @DisplayName("clears the completed date and actual cost unless the status is Completed")
  void completedFieldsOnlyWhenCompleted() {
    givenCodes();
    long id = givenRepair(1L, null, "COM", "H", repair -> repair
        .completedDate(LocalDate.of(2020, 1, 1)).actualCost(100L));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, id, edit("CF", LocalDate.of(2026, 9, 1), 3800L));

    StructureRepairEntity repair = stored(id);
    assertThat(repair.getCompletedDate()).isNull();
    assertThat(repair.getActualCost()).isNull();
  }

  @Test
  @DisplayName("keeps a blank estimate and quantity blank, where legacy saved 0")
  void blanksStayBlank() {
    givenCodes();
    long id = givenRepair(1L, null, "SUG", "H",
        repair -> repair.estimate(10L).repairQuantity(3L));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, id,
        new RepairUpdateRequest("SUG", "H", null, null, null, "DECK", null, null));

    StructureRepairEntity repair = stored(id);
    assertThat(repair.getEstimate()).isNull();
    assertThat(repair.getRepairQuantity()).isNull();
    assertThat(repair.getDescription()).isNull();
  }

  @Test
  @DisplayName("needs a completed date when the status is Completed")
  void completedNeedsDate() {
    givenCodes();
    long id = givenRepair(1L, null, "SUG", "H");

    assertThat(refused(id, edit("COM", null, 10L))).containsExactly(Map.entry("completedDate",
        "Repair Completed Date is required when the status is Completed."));
  }

  @Test
  @DisplayName("lets only an inspection reviewer set Required or Not Required, as legacy's list "
      + "does")
  void reviewerStatuses() {
    givenCodes();
    long id = givenRepair(1L, null, "SUG", "H");

    assertThat(refused(id, edit("REQ", null, null))).containsOnlyKeys("statusCode");
    assertThat(refused(id, edit("NRQ", null, null))).containsOnlyKeys("statusCode");

    when(loggedUser.isPeng()).thenReturn(true);
    assertThat(refused(id, edit("REQ", null, null))).as("a P.Eng alone").containsOnlyKeys(
        "statusCode");
    when(loggedUser.isInspectionReviewer()).thenReturn(true);
    service.update(7L, id, edit("REQ", null, null));
    StructureRepairEntity repair = stored(id);
    assertThat(repair.getRepairStatusCode()).isEqualTo("REQ");
    assertThat(repair.getRequiredByUserid()).isEqualTo("IDIR\\EDITOR");
  }

  @Test
  @DisplayName("names each field at fault: status, priority and type required and listed, "
      + "amounts in range")
  void refusesEachField() {
    givenCodes();
    long id = givenRepair(1L, null, "SUG", "H");

    assertThat(refused(id,
        new RepairUpdateRequest(" ", "", null, -1L, null, null, 1_000_000L, null)))
        .containsExactly(
            Map.entry("statusCode", "Repair Status is required."),
            Map.entry("priorityCode", "Repair Priority is required."),
            Map.entry("estimate", "Repair Estimate Cost must be a whole number from 0 to 999,999."),
            Map.entry("typeCode", "Repair Type is required."),
            Map.entry("quantity", "Qty must be a whole number from 0 to 999,999."));
    assertThat(refused(id, new RepairUpdateRequest("XX", "XX", LocalDate.of(2026, 1, 1), 0L,
        1_000_000L, "XX", 0L, null)))
        .containsOnlyKeys("statusCode", "priorityCode", "typeCode");
    assertThat(refused(id, new RepairUpdateRequest("COM", "H", LocalDate.of(2026, 1, 1), 0L,
        1_000_000L, "DECK", 0L, null)))
        .containsOnlyKeys("actualCost");
  }

  @Test
  @DisplayName("needs a description for the type 800A, and at most 2000 bytes of one")
  void description() {
    givenCodes();
    long id = givenRepair(1L, null, "SUG", "H");

    assertThat(refused(id,
        new RepairUpdateRequest("SUG", "H", null, null, null, "800A", null, " ")))
        .containsExactly(Map.entry("description",
            "Repair Description is required for this Repair Type."));
    assertThat(refused(id, new RepairUpdateRequest("SUG", "H", null, null, null, "DECK", null,
        "é".repeat(1001)))).containsOnlyKeys("description");
  }

  @Test
  @DisplayName("refuses to edit a repair under a structure it does not belong to, or none at all")
  void updateOnlyThroughItsStructure() {
    givenCodes();
    long id = givenRepair(1L, null, "SUG", "H");
    entityManager.flush();
    entityManager.clear();

    RepairUpdateRequest request = edit("SUG", null, null);
    assertThatThrownBy(() -> service.update(8L, id, request))
        .isInstanceOf(RepairNotFoundException.class);
    assertThatThrownBy(() -> service.update(7L, id + 1000, request))
        .isInstanceOf(RepairNotFoundException.class);
  }

  private void givenType(String code, String description, Integer order, String unit) {
    entityManager.persist(StructureRepairTypeCodeEntity.builder()
        .structureRepairTypeCode(code).description(description).build());
    if (order != null) {
      entityManager.persist(StructureRepairTypeOrderEntity.builder()
          .structureRepairTypeCode(code).structureRepairTypeOrder(order)
          .structureRepairUnit(unit).build());
    }
  }

  private void givenXref(String typeClass, String type, String group, String repairClass) {
    entityManager.persist(new StructureRepairTypeXrefEntity(
        new StructureRepairTypeXrefEntity.Key(typeClass, type, group, repairClass)));
  }

  @Test
  @DisplayName("lists the structure's repair types as legacy does: its type class only, by group "
      + "then order, once per group, leaving out a type with no order row")
  void repairTypes() {
    givenType("DECK", "Deck planks", 2, "m²");
    givenType("RAIL", "Railing", 1, "m");
    givenType("PIER", "Pier cap", 1, null);
    givenType("LOST", "No order", null, null);
    givenType("CULV", "Culvert pipe", 1, "m");
    givenXref("TB", "DECK", "STRU", "RM");
    givenXref("TB", "DECK", "STRU", "SA");
    givenXref("TB", "RAIL", "STRU", "RM");
    givenXref("TB", "RAIL", "APPR", "RM");
    givenXref("TB", "PIER", "SUBS", "ST");
    givenXref("TB", "LOST", "MISC", "RM");
    givenXref("CUL", "CULV", "CHNL", "RM");
    entityManager.flush();
    entityManager.clear();

    assertThat(service.repairTypes(7L)).containsExactly(
        new RepairTypeOption("RAIL", "Railing", "m", "APPR"),
        new RepairTypeOption("RAIL", "Railing", "m", "STRU"),
        new RepairTypeOption("DECK", "Deck planks", "m²", "STRU"),
        new RepairTypeOption("PIER", "Pier cap", null, "SUBS"));
  }

  @Test
  @DisplayName("adds a repair as legacy does: Suggested by the user, the structure's next number "
      + "over every repair, not carried forward, of the inspection it was added from")
  void creates() {
    givenCodes();
    givenInspection(10L, LocalDate.of(2023, 6, 1));
    givenRepair(4L, 10L, "REQ", "H");
    givenRepair(2L, null, "SUG", "H");
    entityManager.flush();
    entityManager.clear();

    RepairCreatedResponse created = service.create(7L, 10L,
        new RepairCreateRequest(" H ", 4000L, "DECK", 12L, "  Replace planks.  "));

    assertThat(created.number()).isEqualTo(5L);
    StructureRepairEntity repair = stored(Long.parseLong(created.id()));
    assertThat(repair.getCrossingStructureId()).isEqualTo(7L);
    assertThat(repair.getRepairNumber()).isEqualTo(5L);
    assertThat(repair.getRepairStatusCode()).isEqualTo("SUG");
    assertThat(repair.getRepairPriorityCode()).isEqualTo("H");
    assertThat(repair.getEstimate()).isEqualTo(4000L);
    assertThat(repair.getStructureRepairTypeCode()).isEqualTo("DECK");
    assertThat(repair.getRepairQuantity()).isEqualTo(12L);
    assertThat(repair.getDescription()).isEqualTo("Replace planks.");
    assertThat(repair.getCarriedForwardInd()).isEqualTo("N");
    assertThat(repair.getInspectionId()).isEqualTo(10L);
    assertThat(repair.getCompletedDate()).isNull();
    assertThat(repair.getActualCost()).isNull();
    assertThat(repair.getSuggestedByUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(repair.getSuggestedByTimestamp()).isNotNull();
    assertThat(repair.getRequiredByUserid()).isNull();
    assertThat(repair.getEntryUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(repair.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
  }

  @Test
  @DisplayName("numbers a structure's first repair 1")
  void firstNumber() {
    givenCodes();
    givenInspection(10L, LocalDate.of(2023, 6, 1));

    assertThat(service.create(7L, 10L, new RepairCreateRequest("H", null, "DECK", null, null))
        .number()).isEqualTo(1L);
  }

  @Test
  @DisplayName("adds nothing with a field at fault, and names each")
  void createRefused() {
    givenCodes();

    RepairCreateRequest request = new RepairCreateRequest(null, -5L, "800A", null, null);
    assertThatThrownBy(() -> service.create(7L, 10L, request))
        .isInstanceOfSatisfying(FieldValidationException.class, refused ->
            assertThat(refused.getFieldErrors())
                .containsOnlyKeys("priorityCode", "estimate", "description"));
    assertThat(service.repairs(7L, View.ALL, null, 0,
        10, true, List.of()).page().content()).isEmpty();
  }

  @Test
  @DisplayName("adds nothing to a structure that does not exist")
  void createMissingStructure() {
    RepairCreateRequest request = new RepairCreateRequest("H", null, "DECK", null, null);
    assertThatThrownBy(() -> service.create(404L, 10L, request))
        .isInstanceOf(StructureNotFoundException.class);
  }

  @Test
  @DisplayName("refuses a structure that does not exist")
  void missingStructure() {
    assertThatThrownBy(() -> service.repairs(404L, View.ALL, null, 0,
        10, false, List.of()))
        .isInstanceOf(StructureNotFoundException.class);
  }
}
