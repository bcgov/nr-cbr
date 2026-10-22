package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementStructuresCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
 * Replacement Structures Summary, against the database: which structures, under which sections, in
 * what order, each value and total as the template prints it, and the PDF made from them.
 *
 * <p>H2 stands in for Oracle, so this proves the predicates, the joins and the formatting, not
 * Oracle's behaviour.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({ReplacementStructuresSummaryService.class, ReplacementStructuresRows.class,
    ReplacementStructuresCriteriaLine.class, StructureInventoryNames.class,
    StructureInventoryReportSupport.class, ReportRenderer.class})
class ReplacementStructuresSummaryServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(10);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(10);

  @Autowired
  private ReplacementStructuresSummaryService service;

  @Autowired
  private ReplacementStructuresRows rows;

  @Autowired
  private ReplacementStructuresCriteriaLine criteriaLine;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @MockitoBean
  private ConfigurationService codes;

  private long nextStructure = 1;
  private long nextBridge = 500;
  private long nextCulvert = 700;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\REPORTER");
    when(codes.getBusinessAreas()).thenReturn(
        List.of(new OrgUnitResponse("3000", "TCC", "Cariboo-Chilcotin Business Area")));
    when(codes.getSiteStatusCodes()).thenReturn(List.of(new CodeOptionResponse("ACT", "Active")));
    when(codes.getSiteTypeCodes()).thenReturn(List.of(new CodeOptionResponse("RD", "Road")));
    for (String entity : List.of("ForestServiceBridgeEntity", "ForestServiceCulvertEntity",
        "CrossingStructureEntity", "CrossingSiteEntity", "CbrRoadSectionEntity", "OrgUnitEntity",
        "CbrOrgUnitEntity", "CrossingSiteStatusCodeEntity", "ClientPublicEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (String status : List.of("ACT", "INA")) {
      entityManager.persist(CrossingSiteStatusCodeEntity.builder()
          .crossingSiteStatusCode(status).description(status).effectiveDate(PAST)
          .expiryDate(FUTURE).updateTimestamp(LocalDateTime.now()).build());
    }
    for (Object[] unit : List.of(new Object[] {18L, "DPG", "Prince George"},
        new Object[] {9L, "DMH", "100 Mile House"})) {
      entityManager.persist(OrgUnitEntity.builder().orgUnitNo((Long) unit[0])
          .orgUnitCode((String) unit[1]).orgUnitName((String) unit[2]).build());
    }
    entityManager.flush();
    // The region a district rolls up to is read-only on the entity; set it as Oracle has it.
    entityManager.createNativeQuery("UPDATE THE.ORG_UNIT SET ROLLUP_REGION_NO = 1 "
        + "WHERE ORG_UNIT_NO IN (9, 18)").executeUpdate();
    for (Object[] unit : List.of(new Object[] {1L, "RNI", "Northern Interior Region"},
        new Object[] {18L, "DPG", "Prince George District"},
        new Object[] {9L, "DMH", "100 Mile House District"},
        new Object[] {26L, "DRV", "Robson Valley"})) {
      entityManager.persist(CbrOrgUnitEntity.builder().orgUnitNo((Long) unit[0])
          .orgUnitCode((String) unit[1]).orgUnitName((String) unit[2]).orgLevelCode("D")
          .orgUnitType("D").effectiveDate(PAST).expiryDate(FUTURE).build());
    }
    entityManager.persist(ClientPublicEntity.builder()
        .clientNumber("00012345").clientName("CANFOR").build());
    for (String[] road : List.of(new String[] {"01", "Bowron FSR"},
        new String[] {"02", "Aleza FSR"})) {
      entityManager.persist(CbrRoadSectionEntity.builder()
          .forestFileId("R00123").roadSectionId(road[0]).roadSectName(road[1]).build());
    }
  }

  private void site(
      String id, UnaryOperator<CrossingSiteEntity.CrossingSiteEntityBuilder> with) {
    entityManager.persist(with.apply(CrossingSiteEntity.builder()
        .crossingSiteId(id).crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.5")).orgUnitNo(18L)
        .managementOrgUnitNo(26L).forestFileId("R00123").roadSectionId("01")
        .crossingSiteStatusCode("ACT").capitalRoadInd("N").clientNumber("00012345")).build());
  }

  private long structure(
      String name, String site,
      UnaryOperator<CrossingStructureEntity.CrossingStructureEntityBuilder> with) {
    long id = nextStructure++;
    entityManager.persist(with.apply(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName(name).crossingSiteId(site)
        .structureTypeClassCode("PB").activeInd("Y").closeProximityInd("N")
        .portableStructureInd("N").designLoadRating(new BigDecimal("75"))
        .currentLoadRating(new BigDecimal("45.4")).originalReplacementYear(2045)
        .estimatedReplacementCost(250000L)).build());
    return id;
  }

  private void bridge(long structureId, String length, String portableStatus) {
    entityManager.persist(ForestServiceBridgeEntity.builder()
        .forestServiceBridgeId(nextBridge++).crossingStructureId(structureId)
        .totalBridgeLength(new BigDecimal(length))
        .portableSuperstructureStatusCode(portableStatus).build());
  }

  private void culvert(long structureId, String length) {
    entityManager.persist(ForestServiceCulvertEntity.builder()
        .forestServiceCulvertId(nextCulvert++).crossingStructureId(structureId)
        .culvertLength(new BigDecimal(length)).build());
  }

  private List<Map<String, String>> rows(ReplacementStructuresCriteria criteria) {
    entityManager.flush();
    entityManager.clear();
    return rows.rows(criteria);
  }

  private List<Map<String, String>> rows() {
    return rows(ReplacementStructuresCriteria.builder().build());
  }

  private static List<String> outline(List<Map<String, String>> rows) {
    return rows.stream().map(row -> row.get("YEAR") + " " + row.get("REPLACEMENT_FOR") + " "
        + row.get("CROSSING_STRUCTURE_NAME")).toList();
  }

  @Test
  @DisplayName("lists a structure under each year it has, latest year first, then by road and km")
  void order() {
    site("62-001", s -> s);
    site("62-002", s -> s.roadSectionId("02"));
    site("62-003", s -> s.pointOfCommencementDistance(new BigDecimal("3")));
    structure("B1", "62-001", s -> s.fullLogHaulReplacementYear(2030)
        .lightVehicleReplacementYear(2035).estimatedClosureYear(2040));
    structure("B2", "62-002", s -> s.fullLogHaulReplacementYear(2030));
    structure("B3", "62-003", s -> s.lightVehicleReplacementYear(2030));
    structure("B4", "62-003", s -> s);

    assertThat(outline(rows())).containsExactly(
        "2040 Closure B1",
        "2035 Estimated Replacement B1",
        // Aleza FSR before Bowron FSR; within Bowron FSR, km 3 before 12.5.
        "2030 Estimated Load B2",
        "2030 Estimated Load B1",
        "2030 Estimated Replacement B3");
  }

  @Test
  @DisplayName("lists closures on active sites only, and no inactive structure")
  void closures() {
    site("62-001", s -> s.crossingSiteStatusCode("INA"));
    structure("B1", "62-001", s -> s.estimatedClosureYear(2040).fullLogHaulReplacementYear(2030));
    structure("B2", "62-001", s -> s.activeInd("N").fullLogHaulReplacementYear(2030));

    assertThat(outline(rows())).containsExactly("2030 Estimated Load B1");
  }

  @Test
  @DisplayName("prints a row per bridge and culvert record, less a portable bridge in service")
  void bridgesAndCulverts() {
    site("62-001", s -> s);
    long twoBridges = structure("B1", "62-001", s -> s.fullLogHaulReplacementYear(2030));
    bridge(twoBridges, "1234.56", null);
    bridge(twoBridges, "20", "S");
    bridge(twoBridges, "30", "N");
    long culverted = structure("C1", "62-001", s -> s.fullLogHaulReplacementYear(2030)
        .structureTypeClassCode("CUL"));
    culvert(culverted, "18.26");
    long portable = structure("P1", "62-001", s -> s.fullLogHaulReplacementYear(2030));
    bridge(portable, "40", "S");

    List<Map<String, String>> listed = rows();

    assertThat(listed).extracting(row -> row.get("CROSSING_STRUCTURE_NAME") + " "
            + row.get("TOTAL_LENGTH"))
        .containsExactlyInAnyOrder("B1 1,234.6", "B1 30.0", "C1 18.3");
  }

  @Test
  @DisplayName("prints each value as legacy's template did, and each year's totals on its rows")
  void values() {
    site("62-001", s -> s.capitalRoadInd("Y"));
    structure("B1", "62-001", s -> s.fullLogHaulReplacementYear(2030)
        .estimatedReplacementCost(1250000L));
    structure("B2", "62-001", s -> s.fullLogHaulReplacementYear(2030)
        .estimatedReplacementCost(null).designLoadRating(null));
    structure("B3", "62-001", s -> s.lightVehicleReplacementYear(2030));

    List<Map<String, String>> listed = rows();

    assertThat(listed.getFirst()).contains(
        entry("FOREST_REGION_NAME", "Northern Interior Region"),
        entry("FOREST_DISTRICT_NAME", "Prince George District"),
        entry("MANAGEMENT_AREA_NAME", "Robson Valley"),
        entry("YEAR", "2030"), entry("REPLACEMENT_FOR", "Estimated Load"),
        entry("CROSSING_SITE_ID", "62-001"), entry("FOREST_SERVICE_ROAD", "Bowron FSR"),
        entry("FOREST_FILE_ID", "R00123"), entry("ROAD_SECTION_ID", "01"),
        entry("CROSSING_NAME", "Deadman Creek"), entry("STRUCTURE_TYPE_CLASS_CODE", "PB"),
        entry("DESIGN_LOAD_RATING", "75"), entry("LOAD_RATING", "45"),
        entry("CLIENT_NAME", "CANFOR"), entry("END_OF_DESIGN_LIFE", "2045"),
        entry("CAPITAL_ROAD_IND", "Y"));
    assertThat(listed).filteredOn(row -> "Estimated Load".equals(row.get("REPLACEMENT_FOR")))
        .allSatisfy(row -> assertThat(row).contains(
            entry("TOTAL_COUNT", "2"), entry("TOTAL_COST", "1,250,000")))
        .extracting(row -> row.get("ESTIMATED_REPLACEMENT_COST"))
        .containsExactlyInAnyOrder("1,250,000", null);
    assertThat(listed).filteredOn(row -> "B3".equals(row.get("CROSSING_STRUCTURE_NAME")))
        .singleElement().satisfies(row -> assertThat(row).contains(
            entry("TOTAL_COUNT", "1"), entry("TOTAL_COST", "250,000")));
  }

  @Test
  @DisplayName("picks structures by both year ranges, ends included, whichever section they're under")
  void yearRanges() {
    site("62-001", s -> s);
    structure("B1", "62-001", s -> s.fullLogHaulReplacementYear(2030)
        .lightVehicleReplacementYear(2040));
    structure("B2", "62-001", s -> s.fullLogHaulReplacementYear(2031));
    structure("B3", "62-001", s -> s.fullLogHaulReplacementYear(2029));

    assertThat(outline(rows(ReplacementStructuresCriteria.builder()
        .loadRestrictionYearStart("2030").loadRestrictionYearEnd("2031").build())))
        .containsExactly("2040 Estimated Replacement B1", "2031 Estimated Load B2",
            "2030 Estimated Load B1");
    assertThat(outline(rows(ReplacementStructuresCriteria.builder()
        .loadRestrictionYearStart("2031").build())))
        .containsExactly("2031 Estimated Load B2");
    assertThat(outline(rows(ReplacementStructuresCriteria.builder()
        .replacementYearEnd("2040").build())))
        .containsExactly("2040 Estimated Replacement B1", "2030 Estimated Load B1");
  }

  @Test
  @DisplayName("filters by district, area, status, type, capital road and maintainer")
  void siteCriteria() {
    site("62-001", s -> s.capitalRoadInd("Y").crossingSiteTypeCode("RD"));
    site("62-002", s -> s.orgUnitNo(9L).managementOrgUnitNo(null).clientNumber(null));
    structure("B1", "62-001", s -> s.fullLogHaulReplacementYear(2030));
    structure("B2", "62-002", s -> s.fullLogHaulReplacementYear(2030));

    for (ReplacementStructuresCriteria criteria : List.of(
        ReplacementStructuresCriteria.builder().orgUnit("18").build(),
        ReplacementStructuresCriteria.builder().managementOrgUnit("26").build(),
        ReplacementStructuresCriteria.builder().capitalRoad(true).build(),
        ReplacementStructuresCriteria.builder().siteTypeCode("RD").build(),
        ReplacementStructuresCriteria.builder().clientNumber("00012345").build())) {
      assertThat(outline(rows(criteria))).as(criteria.toString())
          .containsExactly("2030 Estimated Load B1");
    }
    assertThat(outline(rows(ReplacementStructuresCriteria.builder().capitalRoad(false).build())))
        .hasSize(2);
  }

  @Test
  @DisplayName("writes legacy's criteria line, in its order")
  void criteriaLine() {
    assertThat(criteriaLine.of(ReplacementStructuresCriteria.builder()
        .businessAreaOrgUnit("3000").loadRestrictionYearStart("2030").loadRestrictionYearEnd("2035")
        .replacementYearStart("2040").siteStatusCode("ACT").capitalRoad(true).siteTypeCode("RD")
        .clientNumber("00012345").clientLocationCode("00").orgUnit("18").build()))
        .isEqualTo("Business Area: Cariboo-Chilcotin Business Area "
            + "Estimated Load Restriction Date: 2030 - 2035 Estimated Replacement Date: >= 2040 "
            + "Site Status: Active Capital Road: Yes Site Type: Road "
            + "Primary User Client Number: 00012345 Client Location Code: 00");
  }

  @Test
  @DisplayName("produces the PDF: a page to a district, each year headed on every page it runs onto")
  void producesThePdf() throws Exception {
    site("62-001", s -> s);
    site("62-002", s -> s.orgUnitNo(9L).managementOrgUnitNo(null));
    for (int index = 0; index < 40; index++) {
      structure("B" + index, "62-001", s -> s.fullLogHaulReplacementYear(2030));
    }
    structure("D1", "62-002", s -> s.lightVehicleReplacementYear(2031));
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(ReplacementStructuresCriteria.builder().build(),
        ReportFormat.PDF);

    assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(file.filename())
        .matches("ReplacementStructuresSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    PdfReader reader = new PdfReader(file.content());
    PdfTextExtractor text = new PdfTextExtractor(reader);
    assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(3);
    String first = text.getTextFromPage(1).replaceAll("\\s+", " ");
    assertThat(first).contains("Replacement Structures - Summary Report", "CBR009",
        "Forest District: 100 Mile House District", "REPLACEMENT YEAR:", "2031",
        "Estimated Replacement", "End of Design Life", "D1")
        .doesNotContain("(continued)", "Fabricated");
    String second = text.getTextFromPage(2).replaceAll("\\s+", " ");
    assertThat(second).contains("Forest District: Prince George District",
        "Management Area: Robson Valley", "2030", "Estimated Load")
        .doesNotContain("(continued)");
    String third = text.getTextFromPage(3).replaceAll("\\s+", " ");
    assertThat(third).contains("2030 (continued)", "TOTAL REPLACEMENTS:");
  }

  @Test
  @DisplayName("writes the CSV as the PDF's rows, each with its headings, values as the CSVs write them")
  void writesTheCsv() {
    site("62-001", s -> s.capitalRoadInd("Y"));
    long bridged = structure("B1", "62-001", s -> s.fullLogHaulReplacementYear(2030)
        .estimatedClosureYear(2040));
    bridge(bridged, "1234.50", null);
    structure("C1", "62-001", s -> s.lightVehicleReplacementYear(2035)
        .structureTypeClassCode("CUL").currentLoadRating(null).estimatedReplacementCost(null));
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(ReplacementStructuresCriteria.builder().build(),
        ReportFormat.CSV);

    assertThat(file.filename())
        .matches("ReplacementStructuresSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.csv");
    assertThat(new String(file.content(), java.nio.charset.StandardCharsets.UTF_8))
        .startsWith("Forest Region,Forest District,Management Area,Replacement Year,");
    List<String> where = List.of("Northern Interior Region", "Prince George District",
        "Robson Valley");
    List<String> site = List.of("62-001", "Bowron FSR", "R00123", "01", "Deadman Creek");
    assertThat(rows.csvRows(ReplacementStructuresCriteria.builder().build())).containsExactly(
        row(where, List.of("2040", "Closure", "B1"), site,
            List.of("PB", "1234.5", "75", "45.4", "CANFOR", "250000", "2045", "Yes")),
        row(where, List.of("2035", "Estimated Replacement", "C1"), site,
            List.of("CUL", "", "75", "", "CANFOR", "", "2045", "Yes")),
        row(where, List.of("2030", "Estimated Load", "B1"), site,
            List.of("PB", "1234.5", "75", "45.4", "CANFOR", "250000", "2045", "Yes")));
  }

  @SafeVarargs
  private static List<String> row(List<String>... parts) {
    return java.util.Arrays.stream(parts).flatMap(List::stream).toList();
  }
}
