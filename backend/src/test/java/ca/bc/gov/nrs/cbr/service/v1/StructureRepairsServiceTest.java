package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeOrderEntity;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse.Repair;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse.View;
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

/** The Repairs tab's data, against the database: a filtered, ordered page and its decodes. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureRepairsService.class)
class StructureRepairsServiceTest {

  @Autowired
  private StructureRepairsService service;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    for (String entity : List.of("StructureRepairEntity", "StructureInspectionEntity",
        "RepairPriorityCodeEntity", "RepairStatusCodeEntity", "StructureRepairTypeCodeEntity",
        "StructureRepairTypeOrderEntity", "CrossingStructureEntity")) {
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
    return service.repairs(7L, view, 0, 10);
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

    PagedResponse<Repair> second = service.repairs(7L, View.ALL, 1, 10);

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
  @DisplayName("refuses a structure that does not exist")
  void missingStructure() {
    assertThatThrownBy(() -> service.repairs(404L, View.ALL, 0, 10))
        .isInstanceOf(StructureNotFoundException.class);
  }
}
