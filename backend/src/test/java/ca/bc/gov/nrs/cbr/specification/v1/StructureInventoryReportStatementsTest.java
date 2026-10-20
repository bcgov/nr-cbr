package ca.bc.gov.nrs.cbr.specification.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.service.v1.ConfigurationService;
import ca.bc.gov.nrs.cbr.service.v1.ReportRenderer;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventorySummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The Structure Inventory – Summary report reads a fixed number of statements however many
 * structures it lists — never one per structure. A province-wide run lists thousands, so a lookup
 * per row (an association loaded lazily, a status read one inspection at a time) is the difference
 * between a second and a minute, and is invisible in a test that only checks the rows.
 *
 * <p>The sites point at road sections the view does not hold, as many real ones do: that is what
 * would make Hibernate go back for the road one site at a time, were the report to load it so.
 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "ca.bc.gov.nrs.cbr.specification.v1.CapturingStatementInspector",
})
@Import({StructureInventorySummaryService.class, ReportRenderer.class})
class StructureInventoryReportStatementsTest {

  /** The structures, the two lengths, the inspection dates, the newest status rows, their codes. */
  private static final int STATEMENTS = 6;

  @Autowired
  private StructureInventorySummaryService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private ConfigurationService codes;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @ParameterizedTest(name = "{0} structures")
  @ValueSource(ints = {2, 20})
  @DisplayName("reads the same handful of statements however many structures it lists")
  void fixedStatements(int structures) {
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build());
    entityManager.persist(CrossingSiteStatusCodeEntity.builder()
        .crossingSiteStatusCode("ACT").description("Active")
        .effectiveDate(LocalDateTime.now().minusYears(1))
        .expiryDate(LocalDateTime.now().plusYears(1)).updateTimestamp(LocalDateTime.now()).build());
    entityManager.persist(InspectionReportStatusCodeEntity.builder()
        .inspectionReportStatusCode("RVD").description("Reviewed")
        .effectiveDate(LocalDateTime.now().minusYears(1))
        .expiryDate(LocalDateTime.now().plusYears(1)).updateTimestamp(LocalDateTime.now()).build());
    for (long i = 0; i < structures; i++) {
      entityManager.persist(CrossingSiteEntity.builder().crossingSiteId("S" + i)
          .crossingSiteStatusCode("ACT").orgUnitNo(18L)
          .forestFileId("R" + i).roadSectionId("01").build());
      entityManager.persist(CrossingStructureEntity.builder().crossingStructureId(i)
          .crossingStructureName("B" + i).crossingSiteId("S" + i).structureTypeClassCode("PB")
          .activeInd("Y").closeProximityInd("N").portableStructureInd("N").build());
      entityManager.persist(StructureInspectionEntity.builder().inspectionId(100 + i)
          .crossingStructureId(i).strctreInspectionTypeCode("ROUT")
          .inspectionDate(LocalDate.of(2024, 5, 1)).build());
      entityManager.persist(InspectionReportStatusEntity.builder()
          .inspectionReportStatusId(1000 + i).inspectionId(100 + i)
          .inspectionReportStatusCode("RVD").entryUserid("IDIR\\X")
          .entryTimestamp(LocalDateTime.of(2024, 5, 2, 8, 0)).build());
    }
    entityManager.flush();
    entityManager.clear();
    CapturingStatementInspector.clear();

    service.generate(StructureInventorySummaryCriteria.builder().build(), ReportFormat.CSV);

    assertThat(CapturingStatementInspector.count())
        .as("statements: %s", CapturingStatementInspector.all())
        .isEqualTo(STATEMENTS);
    // The structures and their sites as the values a line prints, not as entities — a handful of
    // columns, not the ninety-odd of the two tables.
    String lines = CapturingStatementInspector.lastContaining("the.crossing_structure ");
    assertThat(lines).doesNotContain("annual_inspection_req_ind", "utm_northing");
    assertThat(CapturingStatementInspector.occurrences(lines, "cbr_road_section_vw")).isEqualTo(1);
  }
}
