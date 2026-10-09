package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.RepairNotFoundException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeOrderEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeXrefEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
        "RepairPriorityCodeEntity", "RepairStatusCodeEntity", "StructureRepairTypeCodeEntity",
        "StructureRepairTypeOrderEntity", "StructureRepairTypeXrefEntity",
        "CrossingStructureEntity")) {
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

  private void givenRepair(long id, Long inspectionId, String status, String priority,
      Consumer<StructureRepairEntity.StructureRepairEntityBuilder> change) {
    StructureRepairEntity.StructureRepairEntityBuilder repair = StructureRepairEntity.builder()
        .repairId(id).crossingStructureId(7L).inspectionId(inspectionId).repairNumber(id)
        .repairStatusCode(status).repairPriorityCode(priority).carriedForwardInd("N");
    change.accept(repair);
    entityManager.persist(repair.build());
  }

  private void givenRepair(long id, Long inspectionId, String status, String priority) {
    givenRepair(id, inspectionId, status, priority, repair -> { });
  }

  private PagedResponse<Repair> repairs(View view) {
    entityManager.flush();
    entityManager.clear();
    return service.repairs(7L, view, 0, 10, false).page();
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

    assertThat(repairs(View.OUTSTANDING).content()).extracting(Repair::id)
        .containsExactlyInAnyOrder("1", "2", "3");
    assertThat(repairs(View.ALL).content()).hasSize(5);
  }

  @Test
  @DisplayName("orders as legacy does: no inspection first, then newest inspection, then "
      + "priority, status descending, number")
  void legacyOrder() {
    givenInspection(10L, LocalDate.of(2020, 6, 1));
    givenInspection(11L, LocalDate.of(2023, 6, 1));
    givenRepair(1L, 10L, "REQ", "P1");
    givenRepair(2L, 11L, "REQ", "P2");
    givenRepair(3L, 11L, "REQ", "P1");
    givenRepair(4L, 11L, "SUG", "P1");
    givenRepair(5L, null, "REQ", "P3");

    assertThat(repairs(View.ALL).content()).extracting(Repair::id)
        .containsExactly("5", "4", "3", "2", "1");
  }

  @Test
  @DisplayName("pages on the server")
  void pages() {
    for (long id = 1; id <= 12; id++) {
      givenRepair(id, null, "REQ", "P1");
    }
    entityManager.flush();
    entityManager.clear();

    PagedResponse<Repair> second = service.repairs(7L, View.ALL, 1, 10, false).page();

    assertThat(second.content()).extracting(Repair::number).containsExactly(11L, 12L);
    assertThat(second.totalElements()).isEqualTo(12);
    assertThat(second.totalPages()).isEqualTo(2);
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

    var shown = service.repairs(7L, View.ALL, 0, 10, false);
    var all = service.repairs(7L, View.ALL, 0, 10, true);

    // 1 January of the install year is not "after" it; no inspection is always listed.
    assertThat(shown.page().content()).extracting(Repair::id).containsExactlyInAnyOrder("3", "4");
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

    var listing = service.repairs(7L, View.ALL, 0, 10, false);

    assertThat(listing.page().content()).hasSize(1);
    assertThat(listing.beforeInstallCount()).isZero();
  }

  @Test
  @DisplayName("deletes one repair of the structure, carried forward or not, leaving the others")
  void deletes() {
    givenInspection(10L, LocalDate.of(2023, 6, 1));
    givenRepair(1L, 10L, "CF", "P1", repair -> repair.carriedForwardInd("Y"));
    givenRepair(2L, null, "REQ", "P1");
    entityManager.flush();
    entityManager.clear();

    service.delete(7L, 1L);
    entityManager.flush();
    entityManager.clear();

    assertThat(service.repairs(7L, View.ALL, 0, 10, true).page().content())
        .extracting(Repair::id).containsExactly("2");
  }

  @Test
  @DisplayName("refuses to delete a repair under a structure it does not belong to, or none at all")
  void deleteOnlyThroughItsStructure() {
    givenRepair(1L, null, "REQ", "P1");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(() -> service.delete(8L, 1L))
        .isInstanceOf(RepairNotFoundException.class);
    assertThatThrownBy(() -> service.delete(7L, 99L))
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

  private Map<String, String> refused(RepairUpdateRequest request) {
    entityManager.flush();
    entityManager.clear();
    try {
      service.update(7L, 1L, request);
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
    givenRepair(1L, null, "SUG", "L", repair -> repair.carriedForwardInd("Y")
        .suggestedByUserid("IDIR\\A").suggestedByTimestamp(LocalDateTime.of(2020, 1, 1, 9, 0)));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, 1L, edit("COM", LocalDate.of(2026, 9, 1), 3800L));

    StructureRepairEntity repair = stored(1L);
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
    givenRepair(1L, null, "SUG", "H", repair -> repair
        .suggestedByUserid("IDIR\\A").suggestedByTimestamp(LocalDateTime.of(2020, 1, 1, 9, 0)));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, 1L, edit("SUG", null, null));

    assertThat(stored(1L).getSuggestedByUserid()).isEqualTo("IDIR\\EDITOR");
  }

  @Test
  @DisplayName("clears the completed date and actual cost unless the status is Completed")
  void completedFieldsOnlyWhenCompleted() {
    givenCodes();
    givenRepair(1L, null, "COM", "H", repair -> repair
        .completedDate(LocalDate.of(2020, 1, 1)).actualCost(100L));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, 1L, edit("CF", LocalDate.of(2026, 9, 1), 3800L));

    StructureRepairEntity repair = stored(1L);
    assertThat(repair.getCompletedDate()).isNull();
    assertThat(repair.getActualCost()).isNull();
  }

  @Test
  @DisplayName("keeps a blank estimate and quantity blank, where legacy saved 0")
  void blanksStayBlank() {
    givenCodes();
    givenRepair(1L, null, "SUG", "H", repair -> repair.estimate(10L).repairQuantity(3L));
    entityManager.flush();
    entityManager.clear();

    service.update(7L, 1L,
        new RepairUpdateRequest("SUG", "H", null, null, null, "DECK", null, null));

    StructureRepairEntity repair = stored(1L);
    assertThat(repair.getEstimate()).isNull();
    assertThat(repair.getRepairQuantity()).isNull();
    assertThat(repair.getDescription()).isNull();
  }

  @Test
  @DisplayName("needs a completed date when the status is Completed")
  void completedNeedsDate() {
    givenCodes();
    givenRepair(1L, null, "SUG", "H");

    assertThat(refused(edit("COM", null, 10L))).containsExactly(Map.entry("completedDate",
        "Repair Completed Date is required when the status is Completed."));
  }

  @Test
  @DisplayName("lets only a P.Eng set Required or Not Required, as legacy's list does")
  void engineerStatuses() {
    givenCodes();
    givenRepair(1L, null, "SUG", "H");

    assertThat(refused(edit("REQ", null, null))).containsOnlyKeys("statusCode");
    assertThat(refused(edit("NRQ", null, null))).containsOnlyKeys("statusCode");

    when(loggedUser.isPeng()).thenReturn(true);
    service.update(7L, 1L, edit("REQ", null, null));
    StructureRepairEntity repair = stored(1L);
    assertThat(repair.getRepairStatusCode()).isEqualTo("REQ");
    assertThat(repair.getRequiredByUserid()).isEqualTo("IDIR\\EDITOR");
  }

  @Test
  @DisplayName("names each field at fault: status, priority and type required and listed, "
      + "amounts in range")
  void refusesEachField() {
    givenCodes();
    givenRepair(1L, null, "SUG", "H");

    assertThat(refused(new RepairUpdateRequest(" ", "", null, -1L, null, null, 1_000_000L, null)))
        .containsExactly(
            Map.entry("statusCode", "Repair Status is required."),
            Map.entry("priorityCode", "Repair Priority is required."),
            Map.entry("estimate", "Repair Estimate Cost must be a whole number from 0 to 999,999."),
            Map.entry("typeCode", "Repair Type is required."),
            Map.entry("quantity", "Qty must be a whole number from 0 to 999,999."));
    assertThat(refused(new RepairUpdateRequest("XX", "XX", LocalDate.of(2026, 1, 1), 0L,
        1_000_000L, "XX", 0L, null)))
        .containsOnlyKeys("statusCode", "priorityCode", "typeCode");
    assertThat(refused(new RepairUpdateRequest("COM", "H", LocalDate.of(2026, 1, 1), 0L,
        1_000_000L, "DECK", 0L, null)))
        .containsOnlyKeys("actualCost");
  }

  @Test
  @DisplayName("needs a description for the type 800A, and at most 2000 bytes of one")
  void description() {
    givenCodes();
    givenRepair(1L, null, "SUG", "H");

    assertThat(refused(new RepairUpdateRequest("SUG", "H", null, null, null, "800A", null, " ")))
        .containsExactly(Map.entry("description",
            "Repair Description is required for this Repair Type."));
    assertThat(refused(new RepairUpdateRequest("SUG", "H", null, null, null, "DECK", null,
        "é".repeat(1001)))).containsOnlyKeys("description");
  }

  @Test
  @DisplayName("refuses to edit a repair under a structure it does not belong to, or none at all")
  void updateOnlyThroughItsStructure() {
    givenCodes();
    givenRepair(1L, null, "SUG", "H");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(() -> service.update(8L, 1L, edit("SUG", null, null)))
        .isInstanceOf(RepairNotFoundException.class);
    assertThatThrownBy(() -> service.update(7L, 99L, edit("SUG", null, null)))
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
  @DisplayName("refuses a structure that does not exist")
  void missingStructure() {
    assertThatThrownBy(() -> service.repairs(404L, View.ALL, 0, 10, false).page())
        .isInstanceOf(StructureNotFoundException.class);
  }
}
