package ca.bc.gov.nrs.cbr.specification.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.service.v1.ConfigurationService;
import ca.bc.gov.nrs.cbr.service.v1.ReportRenderer;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryNames;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventorySummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
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
@Import({StructureInventorySummaryService.class, StructureInventoryDetailedService.class,
    StructureInventoryReportSupport.class, StructureInventoryNames.class, ReportRenderer.class})
class StructureInventoryReportStatementsTest {

  /** The structures, the two lengths, the inspection dates, the newest status rows, their codes. */
  private static final int SUMMARY_STATEMENTS = 6;

  /** The structures, the bridges with their span counts, the culvert lengths, the dates. */
  private static final int DETAILED_STATEMENTS = 4;

  /**
   * The Detailed's four, then for the CSV the newest status rows and their codes, and the five code
   * tables it names. (The maintainers and org units are read too when any structure has one.)
   */
  private static final int DETAILED_CSV_STATEMENTS = 11;

  @Autowired
  private StructureInventorySummaryService service;

  @Autowired
  private StructureInventoryDetailedService detailed;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private ConfigurationService codes;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  /** Structures on sites whose road the view lacks, each a bridge of two spans, inspected. */
  private void givenStructures(int structures) {
    entityManager.persist(StructureTypeClassCodeEntity.builder()
        .structureTypeClassCode("PB").description("Permanent Bridge")
        .effectiveDate(LocalDateTime.now().minusYears(1))
        .expiryDate(LocalDateTime.now().plusYears(1)).updateTimestamp(LocalDateTime.now()).build());
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
      entityManager.persist(ForestServiceBridgeEntity.builder().forestServiceBridgeId(500 + i)
          .crossingStructureId(i).needleBeamInd("N").build());
      for (int span = 1; span <= 2; span++) {
        entityManager.persist(ForestServiceBridgeSpanEntity.builder()
            .forestServiceBridgeId(500 + i).spanNumber(span).build());
      }
    }
    entityManager.flush();
    entityManager.clear();
    CapturingStatementInspector.clear();
  }

  @ParameterizedTest(name = "{0} structures")
  @ValueSource(ints = {2, 20})
  @DisplayName("the Detailed reads the same handful of statements however many structures it lists")
  void detailedFixedStatements(int structures) {
    givenStructures(structures);

    detailed.generate(StructureInventoryDetailedCriteria.builder().build(), ReportFormat.PDF);

    assertThat(CapturingStatementInspector.count())
        .as("statements: %s", CapturingStatementInspector.all())
        .isEqualTo(DETAILED_STATEMENTS);
  }

  @ParameterizedTest(name = "{0} structures")
  @ValueSource(ints = {2, 20})
  @DisplayName("the Detailed CSV reads the same handful of statements however many it lists")
  void detailedCsvFixedStatements(int structures) {
    givenStructures(structures);

    detailed.generate(StructureInventoryDetailedCriteria.builder().build(), ReportFormat.CSV);

    assertThat(CapturingStatementInspector.count())
        .as("statements: %s", CapturingStatementInspector.all())
        .isEqualTo(DETAILED_CSV_STATEMENTS);
  }

  @ParameterizedTest(name = "{0} structures")
  @ValueSource(ints = {2, 20})
  @DisplayName("the Summary reads the same handful of statements however many structures it lists")
  void fixedStatements(int structures) {
    givenStructures(structures);

    service.generate(StructureInventorySummaryCriteria.builder().build(), ReportFormat.CSV);

    assertThat(CapturingStatementInspector.count())
        .as("statements: %s", CapturingStatementInspector.all())
        .isEqualTo(SUMMARY_STATEMENTS);
    // The structures and their sites as the values a line prints, not as entities — a handful of
    // columns, not the ninety-odd of the two tables.
    String lines = CapturingStatementInspector.lastContaining("the.crossing_structure ");
    assertThat(lines).doesNotContain("annual_inspection_req_ind", "utm_northing");
    assertThat(CapturingStatementInspector.occurrences(lines, "cbr_road_section_vw")).isEqualTo(1);
  }
}
