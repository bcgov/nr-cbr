package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairClassCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairGroupCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeOrderEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeXrefEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RepairsSummaryCriteria.RepairsSummaryCriteriaBuilder;
import ca.bc.gov.nrs.cbr.struct.v1.RepairsSummarySort;
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
 * The Repairs Summary, against the database: which repairs, in what order, each value as the
 * template prints it, and the PDF made from them.
 *
 * <p>H2 stands in for Oracle, so this proves the predicates, the joins and the formatting, not
 * Oracle's behaviour.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({RepairsSummaryService.class, RepairsSummaryNames.class, RepairsSummaryCsv.class,
    StructureInventoryNames.class, StructureInventoryReportSupport.class, ReportRenderer.class})
class RepairsSummaryServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(10);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(10);

  @Autowired
  private RepairsSummaryService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @MockitoBean
  private ConfigurationService codes;

  private long nextStructure = 1;
  private long nextInspection = 100;
  private long nextRepairNumber = 1;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\REPORTER");
    when(codes.getForestDistricts()).thenReturn(
        List.of(new OrgUnitResponse("18", "DPG", "Prince George Natural Resource District")));
    when(codes.getRepairStatusCodes()).thenReturn(List.of(
        new CodeOptionResponse("REQ", "Required"), new CodeOptionResponse("COM", "Completed")));
    when(codes.getRepairPriorityCodes()).thenReturn(List.of(new CodeOptionResponse("H", "High")));
    when(codes.getRepairClassCodes()).thenReturn(List.of(
        new CodeOptionResponse("S", "Structural"), new CodeOptionResponse("N", "Non-structural")));
    for (String entity : List.of("StructureRepairEntity", "StructureInspectionEntity",
        "ForestServiceBridgeEntity", "CrossingStructureEntity", "CrossingSiteEntity",
        "CbrRoadSectionEntity", "OrgUnitEntity", "CrossingSiteStatusCodeEntity",
        "RepairStatusCodeEntity",
        "RepairPriorityCodeEntity", "StructureRepairTypeCodeEntity",
        "StructureRepairTypeOrderEntity", "StructureRepairTypeXrefEntity",
        "StructureRepairGroupCodeEntity", "StructureRepairClassCodeEntity", "ClientPublicEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build());
    entityManager.persist(CrossingSiteStatusCodeEntity.builder()
        .crossingSiteStatusCode("ACT").description("Active").effectiveDate(PAST)
        .expiryDate(FUTURE).updateTimestamp(PAST).build());
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId("62-001").crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.5")).userKm(new BigDecimal("12.75"))
        .longitude(new BigDecimal("-122.765388")).latitude(new BigDecimal("53.92"))
        .crossingSiteStatusCode("ACT").crossingSiteTypeCode("CRS").orgUnitNo(18L)
        .forestFileId("R00123").roadSectionId("01").build());
    for (String[] code : List.of(new String[] {"SUG", "Suggested"},
        new String[] {"REQ", "Required"}, new String[] {"CF", "Carried Forward"},
        new String[] {"COM", "Completed"})) {
      entityManager.persist(RepairStatusCodeEntity.builder().repairStatusCode(code[0])
          .description(code[1]).effectiveDate(PAST).expiryDate(FUTURE).build());
    }
    for (String[] code : List.of(new String[] {"U", "Urgent"}, new String[] {"H", "High"},
        new String[] {"M", "Medium"}, new String[] {"L", "Low"})) {
      entityManager.persist(RepairPriorityCodeEntity.builder().repairPriorityCode(code[0])
          .description(code[1]).effectiveDate(PAST).expiryDate(FUTURE).build());
    }
    entityManager.persist(StructureRepairTypeCodeEntity.builder()
        .structureRepairTypeCode("DECK").description("Replace decking").build());
    entityManager.persist(StructureRepairTypeCodeEntity.builder()
        .structureRepairTypeCode("BRSH").description("Brush approaches").build());
    entityManager.persist(StructureRepairTypeOrderEntity.builder()
        .structureRepairTypeCode("DECK").structureRepairTypeOrder(1).structureRepairUnit("m2")
        .build());
    entityManager.persist(new StructureRepairTypeXrefEntity(
        new StructureRepairTypeXrefEntity.Key("PB", "DECK", "SUPER", "S")));
    entityManager.persist(new StructureRepairTypeXrefEntity(
        new StructureRepairTypeXrefEntity.Key("PB", "BRSH", "APPR", "N")));
    // Brushing is in a second group too: legacy's CSV lists its repairs once per group.
    entityManager.persist(new StructureRepairTypeXrefEntity(
        new StructureRepairTypeXrefEntity.Key("PB", "BRSH", "ROAD", "N")));
    for (String[] group : List.of(new String[] {"SUPER", "Superstructure"},
        new String[] {"APPR", "Approaches"}, new String[] {"ROAD", "Road"})) {
      entityManager.persist(StructureRepairGroupCodeEntity.builder()
          .structureRepairGroupCode(group[0]).description(group[1]).build());
    }
    entityManager.persist(StructureRepairClassCodeEntity.builder().structureRepairClassCode("S")
        .description("Structural").build());
    entityManager.persist(StructureRepairClassCodeEntity.builder().structureRepairClassCode("N")
        .description("Non-structural").build());
  }

  private long structure(
      String name, UnaryOperator<CrossingStructureEntity.CrossingStructureEntityBuilder> with) {
    long id = nextStructure++;
    entityManager.persist(with.apply(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName(name).crossingSiteId("62-001")
        .structureTypeClassCode("PB").activeInd("Y").closeProximityInd("N")
        .portableStructureInd("N").currentLoadRating(new BigDecimal("45.5"))).build());
    return id;
  }

  private long inspection(long structureId, String type, LocalDate date) {
    long id = nextInspection++;
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(id)
        .crossingStructureId(structureId).strctreInspectionTypeCode(type).inspectionDate(date)
        .inspectionComment("Deck worn at the left abutment").build());
    return id;
  }

  private void repair(
      long structureId, Long inspectionId,
      UnaryOperator<StructureRepairEntity.StructureRepairEntityBuilder> with) {
    entityManager.persist(with.apply(StructureRepairEntity.builder()
        .crossingStructureId(structureId).inspectionId(inspectionId)
        .repairNumber(nextRepairNumber++).repairStatusCode("REQ").repairPriorityCode("H")
        .structureRepairTypeCode("DECK").carriedForwardInd("N")
        .description("Replace the running planks")).build());
  }

  private List<Map<String, String>> rows(UnaryOperator<RepairsSummaryCriteriaBuilder> with) {
    entityManager.flush();
    entityManager.clear();
    return service.rows(with.apply(RepairsSummaryCriteria.builder()).build());
  }

  private List<String> descriptions(UnaryOperator<RepairsSummaryCriteriaBuilder> with) {
    return rows(with).stream().map(row -> row.get("REPAIR_DESCRIPTION")).toList();
  }

  @Test
  @DisplayName("prints a repair, its structure and its inspection as the template does")
  void formatsARepair() {
    long id = structure("B1", s -> s);
    long inspection = inspection(id, "ROUT", LocalDate.of(2025, 6, 12));
    repair(id, inspection, r -> r.repairQuantity(40L).estimate(125000L));

    assertThat(rows(c -> c)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("CROSSING_STRUCTURE_NAME", "B1"),
        entry("CROSSING_SITE_ID", "62-001"),
        entry("FOREST_SERVICE_ROAD", "Bowron FSR"),
        entry("DISTRICT", "Prince George"),
        entry("CROSSING_NAME", "Deadman Creek"),
        entry("USER_KM", "12.75"),
        entry("STRUCTURE_LOAD_RATING", "45.5"),
        entry("LONGITUDE", "-122° 45' 55.4\""),
        entry("LATITUDE", "53° 55' 12.0\""),
        entry("LAST_INSPECTION_DATE", "2025-06-12"),
        entry("INSPECTION_DATE", "2025-06-12"),
        entry("INSPECTION_COMMENT", "Deck worn at the left abutment"),
        entry("REPAIR_NUMBER", "1"),
        entry("REPAIR_STATUS", "Required"),
        entry("REPAIR_PRIORITY", "High"),
        entry("ESTIMATE", "125,000"),
        entry("REPAIR_TYPE", "Replace decking"),
        entry("REPAIR_QTY", "40"),
        entry("REPAIR_UNITS", "m2"),
        entry("PENG_COMMENT", null)));
  }

  @Test
  @DisplayName("works degrees, minutes and seconds out by truncating, carrying a full minute")
  void degreesMinutesSeconds() {
    assertThat(RepairsSummaryService.degreesMinutesSeconds(new BigDecimal("53.92")))
        .isEqualTo("53° 55' 12.0\"");
    assertThat(RepairsSummaryService.degreesMinutesSeconds(new BigDecimal("53.999999")))
        .isEqualTo("54° 00' 00.0\"");
    assertThat(RepairsSummaryService.degreesMinutesSeconds(new BigDecimal("-0.00001")))
        .isEqualTo("0° 00' 00.0\"");
    assertThat(RepairsSummaryService.degreesMinutesSeconds(null)).isNull();
  }

  @Test
  @DisplayName("says so of a repair on no inspection, and of a structure never inspected")
  void noInspection() {
    long id = structure("B1", s -> s);
    repair(id, null, r -> r);

    assertThat(rows(c -> c)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("INSPECTION_DATE", "Not attached to an inspection"),
        entry("LAST_INSPECTION_DATE", "Not attached to inspection"))
        .doesNotContainKeys("INSPECTION_ID", "INSPECTION_COMMENT"));
  }

  @Test
  @DisplayName("defaults to Outstanding: suggested, required or carried forward, never completed")
  void repairStatus() {
    long id = structure("B1", s -> s);
    for (String status : List.of("SUG", "REQ", "CF", "COM")) {
      repair(id, null, r -> r.repairStatusCode(status).description(status));
    }

    assertThat(descriptions(c -> c.repairStatusCode("OUT")))
        .containsExactlyInAnyOrder("SUG", "REQ", "CF");
    assertThat(descriptions(c -> c.repairStatusCode("SOR"))).containsExactlyInAnyOrder("SUG", "REQ");
    assertThat(descriptions(c -> c.repairStatusCode("COM"))).containsExactly("COM");
    assertThat(descriptions(c -> c)).hasSize(4);
  }

  @Test
  @DisplayName("matches any priority of a group, and any class of a Structural Safety pair")
  void groups() {
    long id = structure("B1", s -> s);
    repair(id, null, r -> r.repairPriorityCode("U").description("urgent deck"));
    repair(id, null, r -> r.repairPriorityCode("L").structureRepairTypeCode("BRSH")
        .description("low brush"));

    assertThat(descriptions(c -> c.repairPriorityCode("U&H&M"))).containsExactly("urgent deck");
    assertThat(descriptions(c -> c.repairPriorityCode("M&L"))).containsExactly("low brush");
    assertThat(descriptions(c -> c.repairClassCode("S"))).containsExactly("urgent deck");
    assertThat(descriptions(c -> c.repairClassCode("S&N"))).hasSize(2);
  }

  @Test
  @DisplayName("leaves out carried-forward repairs, archived structures and portables in service")
  void exclusions() {
    long id = structure("B1", s -> s);
    repair(id, null, r -> r.description("listed"));
    repair(id, null, r -> r.carriedForwardInd("Y").description("carried"));
    long archived = structure("B2", s -> s.activeInd("N"));
    repair(archived, null, r -> r.description("archived"));
    long portable = structure("B3", s -> s);
    entityManager.persist(ForestServiceBridgeEntity.builder().forestServiceBridgeId(30L)
        .crossingStructureId(portable).portableSuperstructureStatusCode("S").build());
    repair(portable, null, r -> r.description("portable"));

    assertThat(descriptions(c -> c)).containsExactly("listed");
  }

  @Test
  @DisplayName("keeps only the latest routine inspection's repairs when asked")
  void mostRecentInspections() {
    long id = structure("B1", s -> s);
    long older = inspection(id, "ROUT", LocalDate.of(2023, 5, 1));
    long latest = inspection(id, "ROUT", LocalDate.of(2025, 5, 1));
    long special = inspection(id, "SPEC", LocalDate.of(2025, 8, 1));
    repair(id, older, r -> r.description("older"));
    repair(id, latest, r -> r.description("latest"));
    repair(id, special, r -> r.description("special"));
    repair(id, null, r -> r.description("none"));

    assertThat(descriptions(c -> c.mostRecentInspections(true))).containsExactly("latest");
    assertThat(descriptions(c -> c.mostRecentInspections(false))).hasSize(4);
  }

  @Test
  @DisplayName("orders by the sort chosen, then each structure's newest inspection first")
  void order() {
    entityManager.persist(CrossingSiteEntity.builder().crossingSiteId("10-001")
        .forestFileId("A00001").roadSectionId("01").userKm(BigDecimal.ONE).build());
    long b = structure("B", s -> s);
    long a = structure("A", s -> s.crossingSiteId("10-001"));
    long older = inspection(b, "ROUT", LocalDate.of(2023, 5, 1));
    long newer = inspection(b, "ROUT", LocalDate.of(2025, 5, 1));
    repair(b, older, r -> r.description("B older"));
    repair(b, newer, r -> r.description("B newer 2").repairNumber(2L));
    repair(b, newer, r -> r.description("B newer 1").repairNumber(1L));
    repair(a, null, r -> r.description("A"));

    assertThat(descriptions(c -> c))
        .containsExactly("A", "B newer 1", "B newer 2", "B older");
    assertThat(descriptions(c -> c.sortBy(RepairsSummarySort.SITE_ID)))
        .containsExactly("A", "B newer 1", "B newer 2", "B older");
  }

  @Test
  @DisplayName("filters by structure, site and road text, as a case-insensitive contains")
  void textCriteria() {
    long id = structure("B1234", s -> s);
    repair(id, null, r -> r);

    assertThat(rows(c -> c.structureName("b12").siteId("62-").forestServiceRoad("bowron")))
        .hasSize(1);
    assertThat(rows(c -> c.forestServiceRoad("Nazko"))).isEmpty();
  }

  @Test
  @DisplayName("names legacy's groups and the switches on the criteria line")
  void criteriaLine() {
    RepairsSummaryNames names = new RepairsSummaryNames(null, null, null, null, codes);

    assertThat(names.selectedCriteria(RepairsSummaryCriteria.builder()
        .orgUnit("18").repairStatusCode("OUT").repairPriorityCode("H")
        .repairClassCode("S&N").mostRecentInspections(true).downrated(true).build()))
        .isEqualTo("Forest District: Prince George Natural Resource District"
            + " Downrated Structure: Yes Repair Status: Outstanding Repair Priority: High"
            + " Structural Safety: Structural and Non-structural"
            + " Most Recent Inspections Only: Yes Sort By: Project Number, Branch, KM (Asc)");
  }

  @Test
  @DisplayName("produces the PDF: a header on every page, each inspection's date, comments only when made")
  void producesThePdf() throws Exception {
    long bridge = structure("B1", s -> s);
    long inspection = inspection(bridge, "ROUT", LocalDate.of(2025, 6, 12));
    repair(bridge, inspection, r -> r.estimate(125000L));
    long culvert = structure("C1", s -> s.structureTypeClassCode("CUL"));
    repair(culvert, null, r -> r.description("Clear the inlet"));
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(
        RepairsSummaryCriteria.builder().repairStatusCode("OUT").build(), ReportFormat.PDF);

    assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(file.filename()).matches("RepairsSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    PdfReader reader = new PdfReader(file.content());
    assertThat(reader.getNumberOfPages()).isEqualTo(2);
    PdfTextExtractor text = new PdfTextExtractor(reader);
    String first = text.getTextFromPage(1).replaceAll("\\s+", " ");
    String second = text.getTextFromPage(2).replaceAll("\\s+", " ");
    for (String page : List.of(first, second)) {
      assertThat(page).contains("BC Ministry of Forests, Lands, and Natural Resource Operations",
          "Repairs Summary Report", "IDIR\\REPORTER", "CBR005", "Repair Status: Outstanding");
    }
    // Structure # descending, after the sort chosen: the culvert first. Legacy printed no header for
    // a culvert under the default sort.
    assertThat(first).contains("Structure #: C1", "Inspection Date:", "Not attached to an inspection",
        "Clear the inlet").doesNotContain("Inspection Comment:");
    assertThat(second).contains("Structure #: B1", "Deadman Creek", "-122° 45' 55.4\"",
        "Inspection Date:", "2025-06-12", "Replace decking", "125,000", "Inspection Comment:")
        .doesNotContain("PEng Comment:");
  }

  @Test
  @DisplayName("writes legacy's CSV columns, values as the other CSVs write them")
  void writesTheCsv() {
    entityManager.persist(ClientPublicEntity.builder().clientNumber("00001012")
        .clientName("CANFOR CORPORATION").build());
    entityManager.persist(CrossingSiteEntity.builder().crossingSiteId("62-002")
        .crossingSiteStatusCode("ACT").forestFileId("R00123").roadSectionId("01")
        .pointOfCommencementDistance(new BigDecimal("12.50")).userKm(new BigDecimal("12.75"))
        .latitude(new BigDecimal("53.92")).longitude(new BigDecimal("-122.7654"))
        .orgUnitNo(18L).clientNumber("00001012").clientLocnCode("00").build());
    long id = structure("B1", s -> s.crossingSiteId("62-002")
        .nextPlannedInspectionDate(LocalDate.of(2027, 6, 1)));
    entityManager.persist(ForestServiceBridgeEntity.builder().forestServiceBridgeId(10L)
        .crossingStructureId(id).totalBridgeLength(new BigDecimal("24.50"))
        .deckWidth(new BigDecimal("4.3")).build());
    long inspection = inspection(id, "ROUT", LocalDate.of(2025, 6, 12));
    repair(id, inspection, r -> r.repairQuantity(40L).estimate(125000L));
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(RepairsSummaryCriteria.builder().build(), ReportFormat.CSV);

    assertThat(file.filename()).matches("RepairsSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.csv");
    assertThat(new String(file.content(), StandardCharsets.UTF_8))
        .startsWith(String.join(",", RepairsSummaryCsv.COLUMNS) + "\r\n");
    List<List<String>> rows = service.csvRows(RepairsSummaryCriteria.builder().build());
    assertThat(RepairsSummaryCsv.COLUMNS).hasSize(41);
    assertThat(rows).singleElement().satisfies(row -> {
      assertThat(row).hasSameSizeAs(RepairsSummaryCsv.COLUMNS);
      assertThat(column(row, "Forest File Id")).isEqualTo("R00123");
      assertThat(column(row, "Road Name")).isEqualTo("Bowron FSR");
      assertThat(column(row, "KM User")).isEqualTo("12.75");
      assertThat(column(row, "KM")).isEqualTo("12.5");
      assertThat(column(row, "Latitude")).isEqualTo("53.92");
      assertThat(column(row, "Longitude")).isEqualTo("-122.7654");
      assertThat(column(row, "Primary User Name")).isEqualTo("CANFOR CORPORATION");
      assertThat(column(row, "Client Number")).isEqualTo("00001012");
      assertThat(column(row, "Type")).isEqualTo("PB");
      assertThat(column(row, "Load Rating")).isEqualTo("45.5");
      assertThat(column(row, "Total Length")).isEqualTo("24.5");
      assertThat(column(row, "Width")).isEqualTo("4.3");
      assertThat(column(row, "Last Inspection Date")).isEqualTo("2025-06-12");
      assertThat(column(row, "Next Inspection Date")).isEqualTo("2027-06-01");
      assertThat(column(row, "Opening Width")).isEmpty();
      assertThat(column(row, "Inspection Date")).isEqualTo("2025-06-12");
      assertThat(column(row, "Repair Status")).isEqualTo("Required");
      assertThat(column(row, "Estimate")).isEqualTo("125000");
      assertThat(column(row, "Repair Type")).isEqualTo("Replace decking");
      assertThat(column(row, "Repair Type Code")).isEqualTo("DECK");
      assertThat(column(row, "Repair Class")).isEqualTo("Structural");
      assertThat(column(row, "Repair Group Code")).isEqualTo("SUPER");
      assertThat(column(row, "Repair Group")).isEqualTo("Superstructure");
      assertThat(column(row, "Repair Qty")).isEqualTo("40");
      assertThat(column(row, "Repair Units")).isEqualTo("m2");
    });
  }

  @Test
  @DisplayName("lists a repair once per group its type is in, as legacy's CSV did, and once with none")
  void csvRowPerGroup() {
    long id = structure("B1", s -> s);
    repair(id, null, r -> r.structureRepairTypeCode("BRSH").description("brush"));
    repair(id, null, r -> r.structureRepairTypeCode("SIGN").description("sign"));

    List<List<String>> rows = service.csvRows(RepairsSummaryCriteria.builder().build());

    assertThat(rows).extracting(row -> column(row, "Repair Description"),
            row -> column(row, "Repair Group Code"))
        .containsExactlyInAnyOrder(
            tuple("brush", "APPR"),
            tuple("brush", "ROAD"),
            tuple("sign", ""));
    // The PDF lists each repair once.
    assertThat(rows(c -> c)).hasSize(2);
  }

  private static String column(List<String> row, String heading) {
    return row.get(RepairsSummaryCsv.COLUMNS.indexOf(heading));
  }
}
