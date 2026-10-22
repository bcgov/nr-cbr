package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionAnswerCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionSubsectionCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RunningSurfaceCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCurbTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionReviewerEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedSort;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
 * Inspections Conducted – Detail, against the database: which inspections, in what order, the
 * values of each, the lines of its items, and the PDF made from them.
 *
 * <p>H2 stands in for Oracle, so this proves the predicates, the joins and the formatting, not
 * Oracle's behaviour.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({InspectionsConductedDetailService.class, InspectionDetailRows.class,
    InspectionReportItems.class, InspectionReportRecords.class, RepairsSummaryNames.class,
    InspectionsConductedCriteriaLine.class, StructureInventoryReportSupport.class,
    ReportRenderer.class})
class InspectionsConductedDetailServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(10);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(10);

  @Autowired
  private InspectionsConductedDetailService service;

  @Autowired
  private InspectionDetailRows rows;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @MockitoBean
  private ConfigurationService codes;

  private long nextStructure = 1;
  private long nextInspection = 100;
  private long nextStatus = 1000;
  private long nextItem = 5000;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\REPORTER");
    when(codes.getInspectionTypeCodes()).thenReturn(List.of(
        new CodeOptionResponse("ROUT", "Routine"), new CodeOptionResponse("SPEC", "Special")));
    when(codes.getInspectionReportStatusCodes()).thenReturn(List.of(
        new CodeOptionResponse("SUB", "Submitted"), new CodeOptionResponse("ACC", "Accepted")));
    when(codes.getForestDistricts()).thenReturn(
        List.of(new OrgUnitResponse("18", "DPG", "Prince George Natural Resource District")));
    for (String entity : List.of("StructureInspectionItemEntity", "StructureRepairEntity",
        "StructureMonitorItemEntity", "StructureLoadRatingEntity", "InspectionReportStatusEntity",
        "StructureInspectionEntity", "StructureInspectionReviewerEntity",
        "ForestServiceBridgeSpanEntity", "ForestServiceBridgeEntity", "ForestServiceCulvertEntity",
        "CrossingStructureEntity", "CrossingSiteEntity", "CbrRoadSectionEntity", "OrgUnitEntity",
        "InspectionReportStatusCodeEntity", "InspectionSubsectionCodeEntity",
        "InspectionQuestionCodeEntity", "InspectionAnswerCodeEntity", "RepairStatusCodeEntity",
        "RepairPriorityCodeEntity", "StructureRepairTypeCodeEntity", "MonitoringStatusCodeEntity",
        "RunningSurfaceCodeEntity", "StructureCurbTypeCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build());
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId("62-001").crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.5")).userKm(new BigDecimal("12.75"))
        .orgUnitNo(18L).forestFileId("R00123").roadSectionId("01").utmZone(10)
        .utmEasting(512345L).utmNorthing(5987654L).pointOfAccessDesc("North of the bridge")
        .build());
    for (String[] code : List.of(new String[] {"SUB", "Submitted"},
        new String[] {"ACC", "Accepted"})) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(code[0]).description(code[1]).build());
    }
    entityManager.persist(RunningSurfaceCodeEntity.builder().runningSurfaceCode("GRV")
        .description("Gravel").build());
    entityManager.persist(StructureCurbTypeCodeEntity.builder().structureCurbTypeCode("TMB")
        .description("Timber curb").build());
    for (String[] code : List.of(new String[] {"DECK", "Deck", "2000-01-01"},
        new String[] {"STR", "Stringers", "2000-01-02"},
        new String[] {"APP", "Approaches", "2000-01-03"})) {
      entityManager.persist(InspectionSubsectionCodeEntity.builder().subsectionCode(code[0])
          .description(code[1]).effectiveDate(LocalDate.parse(code[2]).atStartOfDay())
          .expiryDate(FUTURE).build());
    }
    for (String[] code : List.of(new String[] {"COND", "Condition"},
        new String[] {"DML", "Deck margin left"}, new String[] {"PEEL", "Peeled"},
        new String[] {"RDB", "Rot depth bottom"}, new String[] {"WS", "Wood species"})) {
      entityManager.persist(InspectionQuestionCodeEntity.builder().questionCode(code[0])
          .description(code[1]).effectiveDate(PAST).expiryDate(FUTURE).build());
    }
    entityManager.persist(InspectionAnswerCodeEntity.builder().answerCode("G")
        .description("Good").effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(InspectionAnswerCodeEntity.builder().answerCode("Y")
        .description("Yes").effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(RepairStatusCodeEntity.builder().repairStatusCode("REQ")
        .description("Required").effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(RepairPriorityCodeEntity.builder().repairPriorityCode("H")
        .description("High").effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(StructureRepairTypeCodeEntity.builder()
        .structureRepairTypeCode("DECK").description("Replace decking").build());
    entityManager.persist(MonitoringStatusCodeEntity.builder().monitoringStatusCode("ACT")
        .description("Active").build());
  }

  private long structure(
      String name, UnaryOperator<CrossingStructureEntity.CrossingStructureEntityBuilder> with) {
    long id = nextStructure++;
    entityManager.persist(with.apply(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName(name).crossingSiteId("62-001")
        .structureTypeClassCode("PB").activeInd("Y").closeProximityInd("N")
        .portableStructureInd("N").designLoadRating(new BigDecimal("1063.5"))
        .yearBuilt(1998)).build());
    return id;
  }

  /** A bridge with these spans, numbered from 1; the spans' ids, in that order. */
  private List<Long> bridge(long structureId, String... spanLengths) {
    long bridgeId = structureId * 10;
    entityManager.persist(ForestServiceBridgeEntity.builder()
        .forestServiceBridgeId(bridgeId).crossingStructureId(structureId)
        .totalBridgeLength(new BigDecimal("24.56")).runningSurfaceCode("GRV")
        .structureCurbTypeCode("TMB").build());
    List<Long> ids = new java.util.ArrayList<>();
    for (int span = 0; span < spanLengths.length; span++) {
      ForestServiceBridgeSpanEntity entity = ForestServiceBridgeSpanEntity.builder()
          .forestServiceBridgeId(bridgeId).spanNumber(span + 1)
          .spanLength(new BigDecimal(spanLengths[span])).entryUserid("IDIR\\X")
          .entryTimestamp(PAST).updateUserid("IDIR\\X").updateTimestamp(PAST).build();
      entityManager.persist(entity);
      ids.add(entity.getForestServiceBridgeSpanId());
    }
    return ids;
  }

  private long inspection(
      long structureId, LocalDate date,
      UnaryOperator<StructureInspectionEntity.StructureInspectionEntityBuilder> with,
      String... statuses) {
    long id = nextInspection++;
    entityManager.persist(with.apply(StructureInspectionEntity.builder().inspectionId(id)
        .crossingStructureId(structureId).strctreInspectionTypeCode("ROUT").inspectionDate(date)
        .inspectorName("Jane Smith").pengInspectionReqInd("N")).build());
    for (String status : statuses) {
      entityManager.persist(InspectionReportStatusEntity.builder()
          .inspectionReportStatusId(nextStatus++).inspectionId(id)
          .inspectionReportStatusCode(status).entryUserid("IDIR\\SETTER")
          .entryTimestamp(LocalDateTime.of(2025, 7, 1, 9, 30)).build());
    }
    return id;
  }

  private void item(long inspectionId, String subsection, String question, String answer,
      Long stringer, Long spanId, String comment) {
    entityManager.persist(StructureInspectionItemEntity.builder()
        .structureInspectionItemId(nextItem++).inspectionId(inspectionId)
        .subsectionCode(subsection).questionCode(question).answerValue(answer)
        .stringerNumber(stringer).forestServiceBridgeSpanId(spanId).itemComment(comment).build());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(UnaryOperator<InspectionsConductedCriteria.InspectionsConductedCriteriaBuilder> with) {
    entityManager.flush();
    entityManager.clear();
    return rows.rows(with.apply(InspectionsConductedCriteria.builder()).build(),
        InspectionsConductedSort.PROJECT_BRANCH_USER_KM);
  }

  @Test
  @DisplayName("prints the structure, its bridge and the inspection as the template does")
  void formatsAnInspection() {
    long id = structure("B1", s -> s);
    bridge(id, "12.5", "9");
    inspection(id, LocalDate.of(2025, 6, 12), i -> i.highWaterHazardInd("Y")
        .scourHazardInd("N").pengComment("Looks sound"), "SUB", "ACC");

    assertThat(rows(c -> c)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("REPORT_TITLE", "Permanent Bridge Inspection Report"),
        entry("REPORT_ID", "CBR001A"),
        entry("CROSSING_SITE_ID", "62-001"),
        entry("CROSSING_STRUCTURE_NAME", "B1"),
        entry("ROAD_SECT_NAME", "Bowron FSR"),
        entry("INSPECTION_DATE", "2025/06/12"),
        entry("INSPECTION_TYPE", "Routine"),
        entry("BRIDGE", "Y"),
        entry("KM", "12.50"),
        entry("DISTRICT", "Prince George"),
        entry("RUNNING_SURFACE", "Gravel"),
        entry("CURB_TYPE", "Timber curb"),
        entry("TOTAL_LENGTH", "24.6"),
        entry("SPAN_COUNT", "2"),
        entry("SPAN_LENGTHS", "12.5, 9"),
        entry("UTM_EASTING", "512345"),
        entry("YEAR_BUILT", "1998"),
        entry("SITE_DETAILS", "North of the bridge"),
        entry("HZ_HIGH_WATER", "Yes"),
        entry("HZ_SCOUR", "No"),
        entry("HZ_ICE", ""),
        entry("DESIGN_LOAD", "1,064"),
        entry("LOAD_RATING", "See footnote"),
        entry("PENG_REQUIRED", "No"),
        entry("PENG_COMMENT", "Looks sound"),
        entry("REPORT_STATUS", "Accepted By IDIR\\SETTER on 2025/07/01 09:30:00"))
        .doesNotContainKey("CULVERT"));
  }

  @Test
  @DisplayName("names the reviewer and the review date once the inspection has one")
  void reviewedStatus() {
    entityManager.persist(StructureInspectionReviewerEntity.builder().inspectionReviewerId(7L)
        .userid("IDIR\\PENG").firstName("Pat").lastName("Engineer").activeInd("Y").build());
    long id = structure("B1", s -> s);
    long inspection = inspection(id, LocalDate.of(2025, 6, 12), i -> i.inspectionReviewerId(7L)
        .pengReviewerDate(LocalDateTime.of(2025, 7, 2, 0, 0)), "ACC");
    entityManager.persist(StructureLoadRatingEntity.builder()
        .crossingStructureId(id).inspectionId(inspection).loadRating(new BigDecimal("45.4"))
        .entryTimestamp(PAST).build());

    assertThat(rows(c -> c)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("REPORT_STATUS", "Accepted By Pat Engineer on 2025/07/02 00:00:00"),
        entry("LOAD_RATING", "45")));
  }

  @Test
  @DisplayName("lists repairs and monitoring items, and none for an inspection without them")
  void repairsAndMonitors() {
    long id = structure("B1", s -> s);
    long inspection = inspection(id, LocalDate.of(2025, 6, 12), i -> i, "ACC");
    entityManager.persist(StructureRepairEntity.builder().crossingStructureId(id)
        .inspectionId(inspection).repairNumber(1L).repairStatusCode("REQ")
        .repairPriorityCode("H").structureRepairTypeCode("DECK").description("Replace planks")
        .carriedForwardInd("N").build());
    entityManager.persist(StructureMonitorItemEntity.builder().crossingStructureId(id)
        .inspectionId(inspection).monitorNumber(1L).monitoringStatusCode("ACT")
        .description("Watch the scour").build());
    inspection(id, LocalDate.of(2024, 6, 12), i -> i, "ACC");

    List<Map<String, Object>> found = rows(c -> c);

    assertThat(found).hasSize(2);
    assertThat(found.getFirst())
        .containsEntry("REPAIRS", List.of(Map.of("NUMBER", "1", "STATUS", "Required",
            "PRIORITY", "High", "TYPE", "Replace decking", "DESCRIPTION", "Replace planks")))
        .containsEntry("MONITORS",
            List.of(Map.of("NUMBER", "1", "DESCRIPTION", "Watch the scour")));
    assertThat(found.get(1)).containsEntry("REPAIRS", List.of());
  }

  @Test
  @DisplayName("prints items as legacy: subsections by name, a stringer per row with every comment")
  @SuppressWarnings("unchecked")
  void items() {
    long id = structure("B1", s -> s);
    List<Long> spans = bridge(id, "12", "9");
    long inspection = inspection(id, LocalDate.of(2025, 6, 12), i -> i, "ACC");
    long span1 = spans.get(0);
    long span2 = spans.get(1);
    item(inspection, "DECK", "COND", "G", null, null, "Fine");
    item(inspection, "DECK", "DML", "G", null, null, null);
    item(inspection, "STR", "PEEL", "Y", 1L, span1, "Bark on");
    item(inspection, "STR", "RDB", "20", 1L, span1, "Soft at end");
    item(inspection, "STR", "WS", "FIR", 1L, span1, null);
    item(inspection, "STR", "RDB", "5", 2L, span1, null);
    item(inspection, "STR", "RDB", "8", 1L, span2, null);
    item(inspection, "APP", "COND", "Poor", null, null, null);

    List<Map<String, String>> lines =
        (List<Map<String, String>>) rows(c -> c).getFirst().get("ITEMS");

    assertThat(lines).extracting(line -> line.get("KIND") + " " + line.getOrDefault("TEXT",
            line.getOrDefault("STRINGER", line.get("QUESTION"))))
        .containsExactly(
            "SUBSECTION APPROACHES", "QUESTION Condition",
            "SUBSECTION DECK", "QUESTION Condition", "QUESTION_APPROACH Deck margin left",
            "SUBSECTION STRINGERS", "SPAN Span 1", "STR_HEADING null", "STR 1", "STR 2",
            "SPAN Span 2", "STR_HEADING null", "STR 1");
    assertThat(lines.get(8)).contains(entry("PEELED", "Yes"), entry("ROT_BTM", "20"),
        entry("SPECIES", "FIR"), entry("COMMENT", "Bark on; Soft at end"));
    assertThat(lines.get(1)).contains(entry("ANSWER", "Poor"));
    assertThat(lines.get(3)).contains(entry("ANSWER", "Good"), entry("COMMENT", "Fine"));
  }

  @Test
  @DisplayName("orders by Structure # first, the Sort By within it, then newest inspection")
  void order() {
    long b = structure("B", s -> s);
    long a = structure("A", s -> s);
    inspection(b, LocalDate.of(2025, 1, 1), i -> i, "ACC");
    inspection(a, LocalDate.of(2023, 1, 1), i -> i, "ACC");
    inspection(a, LocalDate.of(2024, 1, 1), i -> i, "ACC");

    assertThat(rows(c -> c)).extracting(row -> row.get("CROSSING_STRUCTURE_NAME") + " "
            + row.get("INSPECTION_DATE"))
        .containsExactly("A 2024/01/01", "A 2023/01/01", "B 2025/01/01");
  }

  @Test
  @DisplayName("lists an inspection without a status, and a portable superstructure, unlike the Summary")
  void differsFromSummary() {
    long id = structure("B1", s -> s);
    entityManager.persist(ForestServiceBridgeEntity.builder().forestServiceBridgeId(99L)
        .crossingStructureId(id).portableSuperstructureStatusCode("S").build());
    inspection(id, LocalDate.of(2025, 1, 1), i -> i);

    assertThat(rows(c -> c)).hasSize(1);
    assertThat(rows(c -> c.inspectionReportStatusCode("ACC"))).isEmpty();
  }

  @Test
  @DisplayName("prints a culvert's own rows, without Year Superstructure Installed")
  void culvert() {
    long id = structure("C1", s -> s.structureTypeClassCode("CUL"));
    entityManager.persist(ForestServiceCulvertEntity.builder().forestServiceCulvertId(50L)
        .crossingStructureId(id).culvertLength(new BigDecimal("18")).inletCoverDepth(1500L)
        .build());
    inspection(id, LocalDate.of(2025, 1, 1), i -> i, "ACC");

    assertThat(rows(c -> c)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("REPORT_TITLE", "Major Culvert Inspection Report"),
        entry("REPORT_ID", "CBR001C"),
        entry("CULVERT", "Y"),
        entry("CULVERT_LENGTH", "18.0"),
        entry("INLET_COVER", "1,500")).doesNotContainKey("BRIDGE"));
  }

  @Test
  @DisplayName("writes a CSV row per item, each with its inspection's fields, values as the other CSVs write them")
  void writesTheCsv() {
    long id = structure("B1", s -> s);
    List<Long> spans = bridge(id, "12", "9");
    long inspection = inspection(id, LocalDate.of(2025, 6, 12), i -> i.highWaterHazardInd("Y")
        .scourHazardInd("N").pengComment("Looks sound"), "ACC");
    item(inspection, "DECK", "COND", "G", null, null, "Fine");
    item(inspection, "STR", "RDB", "20", 1L, spans.get(0), "Soft at end");
    item(inspection, "STR", "PEEL", "Y", 1L, spans.get(0), "Bark on");
    entityManager.persist(StructureRepairEntity.builder().crossingStructureId(id)
        .inspectionId(inspection).repairNumber(1L).repairStatusCode("REQ")
        .repairPriorityCode("H").structureRepairTypeCode("DECK").description("Replace planks")
        .carriedForwardInd("N").build());
    entityManager.persist(StructureMonitorItemEntity.builder().crossingStructureId(id)
        .inspectionId(inspection).monitorNumber(1L).monitoringStatusCode("ACT")
        .description("Watch the scour").build());
    inspection(id, LocalDate.of(2024, 6, 12), i -> i, "SUB");
    entityManager.flush();
    entityManager.clear();
    InspectionsConductedCriteria criteria = InspectionsConductedCriteria.builder().build();

    ReportFile file = service.generate(criteria, ReportFormat.CSV);

    assertThat(file.mediaType().toString()).startsWith("text/csv");
    assertThat(file.filename())
        .matches("InspectionsConductedDetailReport_\\d{4}-\\d{2}-\\d{2}\\.csv");
    // A heading starting with "#" is quoted, so that it is not read as a comment line.
    assertThat(new String(file.content(), java.nio.charset.StandardCharsets.UTF_8))
        .startsWith("Site #,Structure #,Structure Type,Road Name,Inspection Date,")
        .contains(",Total Length (metres),\"# Spans\",Span Length(s) (metres),",
            ",Answer,Item Comment\r\n");
    List<List<String>> found = rows.csvRows(criteria, InspectionsConductedSort.PROJECT_BRANCH_USER_KM);
    assertThat(found).hasSize(4).allSatisfy(row ->
        assertThat(row).hasSize(InspectionDetailCsv.COLUMNS.size()));
    // The items, in the PDF's order: subsections by name, a stringer's questions each a row.
    int items = InspectionDetailCsv.COLUMNS.indexOf("Subsection");
    assertThat(found).extracting(row -> row.subList(items, row.size())).containsExactly(
        List.of("Deck", "", "", "", "Condition", "Good", "Fine"),
        List.of("Stringers", "1", "", "1", "Peeled", "Yes", "Bark on"),
        List.of("Stringers", "1", "", "1", "Rot depth bottom", "20", "Soft at end"),
        List.of("", "", "", "", "", "", ""));
    assertThat(byHeading(found.getFirst())).contains(
        entry("Site #", "62-001"),
        entry("Structure #", "B1"),
        entry("Structure Type", "Permanent Bridge"),
        entry("Inspection Date", "2025-06-12"),
        entry("Inspection Type", "Routine"),
        entry("KM", "12.5"),
        entry("Forest District", "Prince George"),
        entry("Running Surface", "Gravel"),
        entry("Curb Type", "Timber curb"),
        entry("Total Length (metres)", "24.56"),
        entry("Span Length(s) (metres)", "12, 9"),
        entry("Culvert Type", ""),
        entry("Year Superstructure Installed", "1998"),
        entry("High Water", "Yes"),
        entry("Scour", "No"),
        entry("Ice", ""),
        entry("Repairs", "1 - Required - High - Replace decking - Replace planks"),
        entry("Monitors", "1 - Watch the scour"),
        entry("PEng's Comment", "Looks sound"),
        entry("Design Load GVW (tonnes)", "1063.5"),
        entry("Current Inspection Load Rating (tonnes)", ""),
        entry("Professional Inspection Required?", "No"),
        entry("Report Status", "Accepted"),
        entry("Status Set By", "IDIR\\SETTER"),
        entry("Status Set On", "2025-07-01 09:30:00"));
    assertThat(byHeading(found.get(3))).contains(
        entry("Inspection Date", "2024-06-12"), entry("Repairs", ""),
        entry("Report Status", "Submitted"));
  }

  private static Map<String, String> byHeading(List<String> row) {
    Map<String, String> map = new java.util.HashMap<>();
    for (int column = 0; column < row.size(); column++) {
      map.put(InspectionDetailCsv.COLUMNS.get(column), row.get(column));
    }
    return map;
  }

  @Test
  @DisplayName("produces the PDF: a page per inspection, each under its own header")
  void producesThePdf() throws Exception {
    long b1 = structure("B1", s -> s);
    bridge(b1, "12", "9");
    long first = inspection(b1, LocalDate.of(2025, 6, 12), i -> i
        .inspectionComment("All good").pengComment("Reviewed on site"), "ACC");
    item(first, "DECK", "COND", "G", null, null, "Fine");
    long c1 = structure("C1", s -> s.structureTypeClassCode("CUL"));
    entityManager.persist(ForestServiceCulvertEntity.builder().forestServiceCulvertId(50L)
        .crossingStructureId(c1).culvertLength(new BigDecimal("18")).build());
    inspection(c1, LocalDate.of(2024, 3, 2), i -> i, "SUB");
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(InspectionsConductedCriteria.builder().build(),
        ReportFormat.PDF);

    assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(file.filename())
        .matches("InspectionsConductedDetailReport_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    PdfReader reader = new PdfReader(file.content());
    PdfTextExtractor text = new PdfTextExtractor(reader);
    StringBuilder all = new StringBuilder();
    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
      String content = text.getTextFromPage(page).replaceAll("\\s+", " ");
      all.append(content).append('\n');
      // Every page carries its own inspection's header.
      assertThat(content).containsAnyOf("Permanent Bridge Inspection Report",
          "Major Culvert Inspection Report");
      if (content.contains("Major Culvert")) {
        assertThat(content).contains("C1").doesNotContain("Structure #: B1");
      }
    }
    assertThat(all).contains("CBR001A", "CBR001C", "Deadman Creek", "Span Length(s)",
        "12, 9", "DECK", "Inspector's Comment:", "All good",
        "PEng's Comment:", "Reviewed on site", "Roles and Responsibilities", "None",
        "Corporate Bridge Register (CBR)");
  }
}
