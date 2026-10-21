package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.RecreationProjectEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorsSummaryCriteria.MonitorsSummaryCriteriaBuilder;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorsSummarySort;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The Monitors Summary, against the database: which monitoring items, in what order, each value
 * as the template prints it, and the PDF made from them.
 *
 * <p>H2 stands in for Oracle, so this proves the predicates, the joins and the formatting, not
 * Oracle's behaviour.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({MonitorsSummaryService.class, MonitorsSummaryCriteriaLine.class,
    StructureInventoryNames.class, StructureInventoryReportSupport.class, ReportRenderer.class})
class MonitorsSummaryServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(10);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(10);

  @Autowired
  private MonitorsSummaryService service;

  @Autowired
  private MonitorsSummaryCriteriaLine criteriaLine;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @MockitoBean
  private ConfigurationService codes;

  private long nextStructure = 1;
  private long nextInspection = 100;
  private long nextMonitorNumber = 1;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\REPORTER");
    when(codes.getForestDistricts()).thenReturn(
        List.of(new OrgUnitResponse("18", "DPG", "Prince George Natural Resource District")));
    when(codes.getMonitoringStatusCodes()).thenReturn(
        List.of(new CodeOptionResponse("ACT", "Active")));
    when(codes.getSiteStatusCodes()).thenReturn(List.of(new CodeOptionResponse("ACT", "Active")));
    when(codes.getSiteTypeCodes()).thenReturn(
        List.of(new CodeOptionResponse("CRS", "Stream crossing")));
    for (String entity : List.of("StructureMonitorItemEntity", "StructureInspectionEntity",
        "CrossingStructureEntity", "CrossingSiteEntity", "CbrRoadSectionEntity",
        "RecreationProjectEntity", "OrgUnitEntity", "CrossingSiteStatusCodeEntity",
        "MonitoringStatusCodeEntity", "ClientPublicEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build());
    entityManager.persist(CrossingSiteStatusCodeEntity.builder()
        .crossingSiteStatusCode("ACT").description("Active").effectiveDate(PAST)
        .expiryDate(FUTURE).updateTimestamp(PAST).build());
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
    entityManager.persist(ClientPublicEntity.builder().clientNumber("00001012")
        .clientName("CANFOR CORPORATION").build());
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId("62-001").crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.5")).crossingSiteStatusCode("ACT")
        .crossingSiteTypeCode("CRS").orgUnitNo(18L).forestFileId("R00123").roadSectionId("01")
        .clientNumber("00001012").build());
    for (String[] code : List.of(new String[] {"ACT", "Active"},
        new String[] {"CLS", "Closed"})) {
      entityManager.persist(MonitoringStatusCodeEntity.builder().monitoringStatusCode(code[0])
          .description(code[1]).build());
    }
  }

  private long structure(
      String name, UnaryOperator<CrossingStructureEntity.CrossingStructureEntityBuilder> with) {
    long id = nextStructure++;
    entityManager.persist(with.apply(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName(name).crossingSiteId("62-001")
        .structureTypeClassCode("PB").activeInd("Y").closeProximityInd("N")
        .portableStructureInd("N")).build());
    return id;
  }

  private long inspection(long structureId, String type, LocalDate date, String comment) {
    long id = nextInspection++;
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(id)
        .crossingStructureId(structureId).strctreInspectionTypeCode(type).inspectionDate(date)
        .inspectionComment(comment).build());
    return id;
  }

  private void item(
      long structureId, Long inspectionId,
      UnaryOperator<StructureMonitorItemEntity.StructureMonitorItemEntityBuilder> with) {
    entityManager.persist(with.apply(StructureMonitorItemEntity.builder()
        .crossingStructureId(structureId).inspectionId(inspectionId)
        .monitorNumber(nextMonitorNumber++).monitoringStatusCode("ACT")
        .description("Watch the scour at the left abutment")).build());
  }

  private List<Map<String, String>> rows(UnaryOperator<MonitorsSummaryCriteriaBuilder> with) {
    entityManager.flush();
    entityManager.clear();
    return service.rows(with.apply(MonitorsSummaryCriteria.builder()).build());
  }

  private List<String> descriptions(UnaryOperator<MonitorsSummaryCriteriaBuilder> with) {
    return rows(with).stream().map(row -> row.get("MONITORING_DESCRIPTION")).toList();
  }

  @Test
  @DisplayName("prints an item, its structure and its inspection as the template does")
  void formatsAnItem() {
    long id = structure("B1", s -> s);
    long inspection = inspection(id, "ROUT", LocalDate.of(2025, 6, 12), "Banks eroding");
    item(id, inspection, i -> i);

    assertThat(rows(c -> c)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("CROSSING_STRUCTURE_NAME", "B1"),
        entry("CROSSING_SITE_ID", "62-001"),
        entry("CLIENT_NAME", "CANFOR CORPORATION"),
        entry("CROSSING_NAME", "Deadman Creek"),
        entry("FOREST_SERVICE_ROAD", "Bowron FSR"),
        entry("POINT_OF_COMMENCEMENT_DISTANCE", "12.50"),
        entry("INSPECTION_DATE", "2025/06/12"),
        entry("INSPECTION_COMMENT", "Banks eroding"),
        entry("MONITOR_NUMBER", "1"),
        entry("MONITORING_STATUS_DESC", "Active"),
        entry("MONITORING_DESCRIPTION", "Watch the scour at the left abutment")));
  }

  @Test
  @DisplayName("names a recreation site's road by its project")
  void recreationProject() {
    entityManager.persist(RecreationProjectEntity.builder()
        .forestFileId("REC1234").projectName("Purden Lake Rec Site").build());
    entityManager.persist(CrossingSiteEntity.builder().crossingSiteId("REC-1")
        .crossingSiteTypeCode("REC").forestFileId("REC1234").build());
    long id = structure("B1", s -> s.crossingSiteId("REC-1"));
    item(id, null, i -> i);

    assertThat(rows(c -> c)).singleElement().satisfies(row -> assertThat(row)
        .containsEntry("FOREST_SERVICE_ROAD", "Purden Lake Rec Site")
        .doesNotContainKeys("INSPECTION_ID", "INSPECTION_DATE"));
  }

  @Test
  @DisplayName("leaves out archived structures and items whose status is in no table")
  void exclusions() {
    long id = structure("B1", s -> s);
    item(id, null, i -> i.description("listed"));
    item(id, null, i -> i.monitoringStatusCode("XX").description("unknown status"));
    long archived = structure("B2", s -> s.activeInd("N"));
    item(archived, null, i -> i.description("archived"));

    assertThat(descriptions(c -> c)).containsExactly("listed");
  }

  @Test
  @DisplayName("filters by status, structure and road text, and the site")
  void criteria() {
    long id = structure("B1234", s -> s);
    item(id, null, i -> i.description("active"));
    item(id, null, i -> i.monitoringStatusCode("CLS").description("closed"));

    assertThat(descriptions(c -> c.monitoringStatusCode("CLS"))).containsExactly("closed");
    assertThat(descriptions(c -> c.structureName("b12").forestServiceRoad("bowron")
        .siteStatusCode("ACT").orgUnit("18"))).hasSize(2);
    assertThat(descriptions(c -> c.forestServiceRoad("Nazko"))).isEmpty();
  }

  @Test
  @DisplayName("Most Recent Inspections Only keeps the latest routine inspection's items, as legacy never did")
  void mostRecentInspections() {
    long id = structure("B1", s -> s);
    long older = inspection(id, "ROUT", LocalDate.of(2023, 5, 1), null);
    long latest = inspection(id, "ROUT", LocalDate.of(2025, 5, 1), null);
    item(id, older, i -> i.description("older"));
    item(id, latest, i -> i.description("latest"));
    item(id, null, i -> i.description("none"));

    assertThat(descriptions(c -> c.mostRecentInspections(true))).containsExactly("latest");
    assertThat(descriptions(c -> c)).hasSize(3);
  }

  @Test
  @DisplayName("orders by the sort chosen, then Structure #, newest inspection, monitor number")
  void order() {
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("A00001").roadSectionId("01").roadSectName("Zeballos FSR").build());
    entityManager.persist(CrossingSiteEntity.builder().crossingSiteId("10-001")
        .forestFileId("A00001").roadSectionId("01").build());
    long b = structure("B", s -> s);
    long a = structure("A", s -> s.crossingSiteId("10-001"));
    long older = inspection(b, "ROUT", LocalDate.of(2023, 5, 1), null);
    long newer = inspection(b, "ROUT", LocalDate.of(2025, 5, 1), null);
    item(b, older, i -> i.description("B older"));
    item(b, newer, i -> i.description("B newer 2").monitorNumber(2L));
    item(b, newer, i -> i.description("B newer 1").monitorNumber(1L));
    item(a, null, i -> i.description("A"));

    assertThat(descriptions(c -> c)).containsExactly("A", "B newer 1", "B newer 2", "B older");
    assertThat(descriptions(c -> c.sortBy(MonitorsSummarySort.ROAD_NAME_STRUCTURE)))
        .containsExactly("B newer 1", "B newer 2", "B older", "A");
  }

  @Test
  @DisplayName("writes legacy's criteria line, Site Type under its own name")
  void criteriaLine() {
    assertThat(criteriaLine.of(MonitorsSummaryCriteria.builder()
        .orgUnit("18").roadSectionId("01").monitoringStatusCode("ACT")
        .mostRecentInspections(true).siteTypeCode("CRS").build()))
        .isEqualTo("Forest District: Prince George Natural Resource District Br.: 01"
            + " Monitoring Status: Active Most Recent Inspections Only: Yes"
            + " Sort By: Project Number, Branch, KM (Asc) Site Type: Stream crossing");
  }

  @Test
  @DisplayName("produces the PDF, reprinting a structure's header on every page its items reach")
  void producesThePdf() throws Exception {
    long id = structure("B1", s -> s);
    long inspection = inspection(id, "ROUT", LocalDate.of(2025, 6, 12), "Banks eroding");
    for (int n = 0; n < 60; n++) {
      item(id, inspection, i -> i);
    }
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(MonitorsSummaryCriteria.builder().build(), ReportFormat.PDF);

    assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(file.filename()).matches("MonitorsSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    PdfReader reader = new PdfReader(file.content());
    assertThat(reader.getNumberOfPages()).isGreaterThan(1);
    PdfTextExtractor text = new PdfTextExtractor(reader);
    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
      assertThat(text.getTextFromPage(page).replaceAll("\\s+", " ")).contains(
          "BC Ministry of Forests, Lands, and Natural Resource Operations",
          "Monitors Summary Report", "IDIR\\REPORTER", "CBR011", "Structure #: B1",
          "Monitor #", "Corporate Bridge Register (CBR)");
    }
    assertThat(text.getTextFromPage(1).replaceAll("\\s+", " "))
        .contains("Inspection:", "2025/06/12", "Inspection Comment:", "Banks eroding",
            "Deadman Creek", "CANFOR CORPORATION");
  }

  @Test
  @DisplayName("writes the PDF's fields as a CSV, a row per item, values as the other CSVs write them")
  void writesTheCsv() {
    long id = structure("B1", s -> s);
    long inspection = inspection(id, "ROUT", LocalDate.of(2025, 6, 12), "Banks eroding,\nslowly");
    item(id, inspection, i -> i);
    item(id, null, i -> i.monitoringStatusCode("CLS").description("No inspection"));
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(MonitorsSummaryCriteria.builder().build(), ReportFormat.CSV);

    assertThat(file.filename()).matches("MonitorsSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.csv");
    String csv = new String(file.content(), StandardCharsets.UTF_8);
    assertThat(csv).startsWith(String.join(",", MonitorsSummaryService.CSV_COLUMNS) + "\r\n")
        .contains("\"Banks eroding,\nslowly\"");
    // Where the item on no inspection falls is the database's: Oracle sorts a blank date first.
    assertThat(service.csvRows(MonitorsSummaryCriteria.builder().build()))
        .containsExactlyInAnyOrder(
        List.of("B1", "62-001", "CANFOR CORPORATION", "12.5", "Deadman Creek", "R00123", "01",
            "Bowron FSR", "", "", "2", "Closed", "No inspection"),
        List.of("B1", "62-001", "CANFOR CORPORATION", "12.5", "Deadman Creek", "R00123", "01",
            "Bowron FSR", "2025-06-12", "Banks eroding,\nslowly", "1", "Active",
            "Watch the scour at the left abutment"));
  }
}
