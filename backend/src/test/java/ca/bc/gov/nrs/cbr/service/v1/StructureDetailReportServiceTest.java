package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.AbutmentCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.BuiltByCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.DeckTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.DesignVehicleLoadCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredClvrtMaterialCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.RecreationProjectEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCurbTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureReplacementXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.SuperstructureTypeCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
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
 * The Structure Detail Report, against the database: the rows of one structure, each value as
 * legacy's template printed it, and the PDF made from them.
 *
 * <p>H2 stands in for Oracle, so this proves the lookups and the formatting, not Oracle's
 * behaviour.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({StructureDetailReportService.class, StructureDetailRows.class,
    StructureInventoryReportSupport.class, StructureInventoryNames.class, ReportRenderer.class})
class StructureDetailReportServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(10);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(10);

  @Autowired
  private StructureDetailReportService service;

  @Autowired
  private StructureDetailRows rows;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @MockitoBean
  private ConfigurationService codes;

  private long nextId = 1;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\REPORTER");
    for (String entity : List.of("ForestServiceBridgeSpanEntity", "ForestServiceBridgeEntity",
        "ForestServiceCulvertEntity", "StructureCommentEntity", "StructureReplacementXrefEntity",
        "CrossingStructureEntity", "CrossingSiteEntity", "CbrRoadSectionEntity",
        "RecreationProjectEntity", "StructureTypeClassCodeEntity", "SuperstructureTypeCodeEntity",
        "DeckTypeCodeEntity", "AbutmentCodeEntity", "StructureCurbTypeCodeEntity",
        "BuiltByCodeEntity", "DesignVehicleLoadCodeEntity", "EngineeredClvrtMaterialCodeEntity", "ClientPublicEntity",
        "CbrOrgUnitEntity", "OrgUnitEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (String[] code : List.of(new String[] {"PB", "Permanent Bridge"},
        new String[] {"CUL", "Culvert"})) {
      entityManager.persist(StructureTypeClassCodeEntity.builder()
          .structureTypeClassCode(code[0]).description(code[1]).effectiveDate(PAST)
          .expiryDate(FUTURE).updateTimestamp(PAST).build());
    }
    entityManager.persist(SuperstructureTypeCodeEntity.builder()
        .superstructureTypeCode("STL").description("Steel girder").build());
    entityManager.persist(DeckTypeCodeEntity.builder().deckTypeCode("TIM").description("Timber")
        .build());
    entityManager.persist(AbutmentCodeEntity.builder().abutmentCode("CON").description("Concrete")
        .build());
    entityManager.persist(AbutmentCodeEntity.builder().abutmentCode("CLT").description("Crib")
        .build());
    entityManager.persist(StructureCurbTypeCodeEntity.builder().structureCurbTypeCode("TMB")
        .description("Timber curb").build());
    entityManager.persist(BuiltByCodeEntity.builder().builtByCode("MOF")
        .description("Ministry of Forests").build());
    entityManager.persist(DesignVehicleLoadCodeEntity.builder().designVehicleLoadCode("L100")
        .description("L-100 logging truck").build());
    entityManager.persist(EngineeredClvrtMaterialCodeEntity.builder()
        .engineeredClvrtMaterialCode("CSP").description("Corrugated steel").build());
    entityManager.persist(ClientPublicEntity.builder().clientNumber("00001012")
        .clientName("CANFOR CORPORATION").build());
    for (Object[] unit : List.of(new Object[] {18L, "Prince George"},
        new Object[] {26L, "Robson Valley"})) {
      entityManager.persist(CbrOrgUnitEntity.builder().orgUnitNo((Long) unit[0])
          .orgUnitName((String) unit[1]).build());
      // The site's own key to it; the report names it from CBR_ORG_UNIT, as legacy does.
      entityManager.persist(OrgUnitEntity.builder().orgUnitNo((Long) unit[0])
          .orgUnitCode("D" + unit[0]).orgUnitName("ORG_UNIT " + unit[1]).build());
    }
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
    entityManager.persist(RecreationProjectEntity.builder()
        .forestFileId("REC1234").projectName("Purden Lake Rec Site").build());
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId("62-001").crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.5")).crossingSiteTypeCode("CRS")
        .orgUnitNo(18L).managementOrgUnitNo(26L).forestFileId("R00123").roadSectionId("01")
        .clientNumber("00001012").build());
  }

  private long structure(
      String name, UnaryOperator<CrossingStructureEntity.CrossingStructureEntityBuilder> with) {
    long id = nextId++;
    entityManager.persist(with.apply(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName(name).crossingSiteId("62-001")
        .structureTypeClassCode("PB").activeInd("Y").closeProximityInd("N")
        .portableStructureInd("N")).build());
    return id;
  }

  private void bridge(
      long structureId, UnaryOperator<ForestServiceBridgeEntity.ForestServiceBridgeEntityBuilder> with,
      String... spans) {
    long bridgeId = structureId * 10;
    entityManager.persist(with.apply(ForestServiceBridgeEntity.builder()
        .forestServiceBridgeId(bridgeId).crossingStructureId(structureId).needleBeamInd("N")
        .totalBridgeLength(new BigDecimal("24.56")).deckWidth(new BigDecimal("4.3"))
        .superstructureTypeCode("STL").deckTypeCode("TIM").leftAbutmentCode("CON")
        .rightAbutmentCode("CLT").structureCurbTypeCode("TMB")).build());
    for (int span = 0; span < spans.length; span++) {
      entityManager.persist(ForestServiceBridgeSpanEntity.builder()
          .forestServiceBridgeId(bridgeId).spanNumber(span + 1).spanLength(new BigDecimal(spans[span]))
          .build());
    }
  }

  private void culvert(long structureId, long culvertId, int number) {
    entityManager.persist(ForestServiceCulvertEntity.builder()
        .forestServiceCulvertId(culvertId).crossingStructureId(structureId).culvertNumber(number)
        .culvertLength(new BigDecimal("18")).slope(new BigDecimal("2.25"))
        .engineeredCulvertMaterialCode("CSP").inletCoverDepth(1500L).openingWidth(2400L).build());
  }

  private void comment(long structureId, String planned, String by, LocalDateTime at, String text) {
    entityManager.persist(StructureCommentEntity.builder().crossingStructureId(structureId)
        .plannedInspectionCmtInd(planned).entryUserid(by).entryTimestamp(at).updateUserid(by)
        .updateTimestamp(at).structureComment(text).build());
  }

  /** The structure's rows as the PDF prints them. */
  private List<Map<String, String>> rows(long structureId) {
    entityManager.flush();
    entityManager.clear();
    return rows.rows(structureId).stream().map(StructureDetailReportService::pdfRow).toList();
  }

  /** The structure's CSV, as text. */
  private String csv(long structureId) {
    entityManager.flush();
    entityManager.clear();
    return new String(service.generate(structureId, ReportFormat.CSV).content(),
        StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("prints the structure, its site and its bridge as legacy's template did")
  void formatsABridge() {
    long id = structure("B1", s -> s.builtByCode("MOF").yearBuilt(1998).yearFabricated(1997)
        .currentLoadRating(new BigDecimal("45.5")).designLoadRating(new BigDecimal("1063.5"))
        .installationCost(125000L).estimatedReplacementCost(1250000L).asBuiltInfoPresentInd("Y")
        .estimatedClosureYear(2030));
    bridge(id, b -> b.needleBeamInd("Y"), "12.5", "9", "12.500");

    assertThat(rows(id)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("CROSSING_STRUCTURE_NAME", "B1"),
        entry("CROSSING_SITE_ID", "62-001"),
        entry("STRUCTURE_TYPE_CLASS_DESC", "Permanent Bridge"),
        entry("CROSSING_NAME", "Deadman Creek"),
        entry("FOREST_SERVICE_ROAD", "Bowron FSR"),
        entry("POINT_OF_COMMENCEMENT_DISTANCE", "12.50"),
        entry("ORG_UNIT_NAME", "Prince George"),
        entry("MANAGEMENT_AREA", "Robson Valley"),
        entry("CLIENT_NAME", "CANFOR CORPORATION"),
        entry("BUILT_FOR", "Ministry of Forests"),
        entry("YEAR_BUILT", "1998"),
        entry("YEAR_FABRICATED", "1997"),
        entry("LOAD_RATING", "46"),
        entry("DESIGN_LOAD_RATING", "1,064"),
        entry("INSTALLATION_COST", "125,000"),
        entry("ESTIMATED_REPLACEMENT_COST", "1,250,000"),
        entry("ESTIMATED_CLOSURE_DATE", "2030"),
        entry("SPAN_COUNT", "3"),
        entry("SPAN_LENGTHS", "12.5, 9, 12.5"),
        entry("TOTAL_BRIDGE_LENGTH", "24.6"),
        entry("DECK_WIDTH", "4.30"),
        entry("SUPERSTRUCTURE", "Steel girder"),
        entry("LEFT_ABUTMENT", "Concrete"),
        entry("RIGHT_ABUTMENT", "Crib"),
        entry("STRUCTURE_CURB_TYPE", "Timber curb"))
        .containsKey("FOREST_SERVICE_BRIDGE_ID")
        .doesNotContainKey("FOREST_SERVICE_CULVERT_ID"));
  }

  @Test
  @DisplayName("prints a culvert's number whole and its depths in millimetres")
  void formatsACulvert() {
    long id = structure("C1", s -> s.structureTypeClassCode("CUL"));
    culvert(id, 100, 1);

    assertThat(rows(id)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("CULVERT_NUMBER", "1"),
        entry("CULVERT_LENGTH", "18.0"),
        entry("SLOPE", "2.2"),
        entry("CULVERT_MATERIAL", "Corrugated steel"),
        entry("INLET_COVER_DEPTH", "1,500"),
        entry("OPENING_WIDTH", "2,400"))
        .containsKey("FOREST_SERVICE_CULVERT_ID")
        .doesNotContainKey("FOREST_SERVICE_BRIDGE_ID"));
  }

  @Test
  @DisplayName("repeats the report for each culvert record, as legacy's join did")
  void repeatsPerCulvert() {
    long id = structure("C1", s -> s.structureTypeClassCode("CUL"));
    culvert(id, 101, 1);
    culvert(id, 102, 2);

    assertThat(rows(id)).extracting(row -> row.get("CULVERT_NUMBER")).containsExactly("1", "2");
  }

  @Test
  @DisplayName("combines each kind of comment, newest first, dated yyyy-MM-dd")
  void combinesComments() {
    long id = structure("B1", s -> s);
    comment(id, "N", "IDIR\\OLDER", LocalDateTime.of(2020, 3, 4, 9, 0), "Old note");
    comment(id, "N", "IDIR\\NEWER", LocalDateTime.of(2024, 11, 2, 15, 30), "New note");
    comment(id, "Y", "IDIR\\PLANNER", LocalDateTime.of(2025, 1, 6, 8, 0), "Check the scour");

    assertThat(rows(id)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("STRUCT_COMMENT",
            "IDIR\\NEWER on 2024-11-02: New note\nIDIR\\OLDER on 2020-03-04: Old note"),
        entry("PLANNED_COMMENT", "IDIR\\PLANNER on 2025-01-06: Check the scour")));
  }

  @Test
  @DisplayName("lists the structures it replaced and was replaced by, by internal id")
  void listsReplacements() {
    long older = structure("OLD1", s -> s);
    long oldest = structure("OLD0", s -> s);
    long id = structure("B1", s -> s);
    long newer = structure("NEW1", s -> s);
    entityManager.persist(StructureReplacementXrefEntity.builder()
        .replacedStructureNumber(older).replacesStructureNumber(id).build());
    entityManager.persist(StructureReplacementXrefEntity.builder()
        .replacedStructureNumber(oldest).replacesStructureNumber(id).build());
    entityManager.persist(StructureReplacementXrefEntity.builder()
        .replacedStructureNumber(id).replacesStructureNumber(newer).build());

    assertThat(rows(id)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("REPLACES", older + ", " + oldest),
        entry("REPLACED_BY", Long.toString(newer))));
  }

  @Test
  @DisplayName("names a recreation site's road by its project")
  void namesARecreationProject() {
    entityManager.persist(CrossingSiteEntity.builder().crossingSiteId("REC-1")
        .crossingSiteTypeCode("REC").forestFileId("REC1234").build());
    long id = structure("B1", s -> s.crossingSiteId("REC-1"));

    assertThat(rows(id)).singleElement().satisfies(row -> assertThat(row)
        .containsEntry("FOREST_SERVICE_ROAD", "Purden Lake Rec Site"));
  }

  @Test
  @DisplayName("has no rows for a structure with no type/class, as legacy's inner join did")
  void noTypeClass() {
    long unclassed = structure("unclassed", s -> s.structureTypeClassCode(null));
    long unknown = structure("unknown", s -> s.structureTypeClassCode("XX"));

    assertThat(rows(unclassed)).isEmpty();
    assertThat(rows(unknown)).isEmpty();
  }

  @Test
  @DisplayName("refuses a structure that does not exist or is archived")
  void notFound() {
    long archived = structure("B1", s -> s.activeInd("N"));
    entityManager.flush();

    assertThatThrownBy(() -> rows.rows(archived)).isInstanceOf(StructureNotFoundException.class);
    assertThatThrownBy(() -> rows.rows(999)).isInstanceOf(StructureNotFoundException.class);
  }

  @Test
  @DisplayName("produces the PDF, a long comment for a code wrapped rather than cut off")
  void producesThePdf() throws Exception {
    String longComment = "Two steel girders salvaged from the old Bowron crossing, re-decked in "
        + "2019 with treated timber and bolted to new concrete lock blocks on both banks";
    long id = structure("B1", s -> s);
    bridge(id, b -> b.superstructureComment(longComment), "12");
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(id, ReportFormat.PDF);

    assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(file.filename()).matches("StructureDetailReport_B1_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    String page = new PdfTextExtractor(new PdfReader(file.content())).getTextFromPage(1)
        .replaceAll("\\s+", " ");
    assertThat(page).contains(
        "BC Ministry of Forests, Lands, and Natural Resource Operations",
        "Structure Detail Report", "IDIR\\REPORTER", "CBR006", "B1", "62-001",
        "Permanent Bridge", "Deadman Creek", "Forest Service Road", "Bowron FSR",
        "Number of Spans", "Timber curb", "Concrete\\Crib", "on both banks",
        "Corporate Bridge Register (CBR)");
    assertThat(page).doesNotContain("Culvert Number");
  }

  @Test
  @DisplayName("writes the CSV as a header and a row, values as the other CSVs write them")
  void writesTheCsv() {
    long id = structure("B1", s -> s.builtByCode("MOF").yearBuilt(1998)
        .currentLoadRating(new BigDecimal("45.5")).designLoadRating(new BigDecimal("1063.50"))
        .designVehicleLoadCode("L100").installationCost(125000L).asBuiltInfoPresentInd("Y")
        .nextPlannedInspectionDate(LocalDate.of(2027, 6, 1)));
    bridge(id, b -> b.needleBeamInd("Y").superstructureComment("Salvaged girders"), "12.5", "9");
    comment(id, "N", "IDIR\\NEWER", LocalDateTime.of(2024, 11, 2, 15, 30), "New note");
    comment(id, "N", "IDIR\\OLDER", LocalDateTime.of(2020, 3, 4, 9, 0), "Old, \"quoted\" note");

    String file = csv(id);

    assertThat(file).startsWith(String.join(",", StructureDetailReportService.CSV_COLUMNS) + "\r\n");
    List<String> row = StructureDetailReportService.csvRow(rows.rows(id).getFirst());
    assertThat(StructureDetailReportService.CSV_COLUMNS).hasSize(61).hasSameSizeAs(row);
    assertThat(column(row, "Structure #")).isEqualTo("B1");
    assertThat(column(row, "KM")).isEqualTo("12.5");
    assertThat(column(row, "Current Load Rating GVW (tonnes)")).isEqualTo("45.5");
    assertThat(column(row, "Design Load Rating GVW (tonnes)")).isEqualTo("1063.5");
    assertThat(column(row, "Design Vehicle Config.")).isEqualTo("L-100 logging truck");
    assertThat(column(row, "Installation Cost ($)")).isEqualTo("125000");
    assertThat(column(row, "As Built Information on File?")).isEqualTo("Yes");
    assertThat(column(row, "Portable Structure?")).isEqualTo("No");
    assertThat(column(row, "Number of Spans")).isEqualTo("2");
    assertThat(column(row, "Span Lengths")).isEqualTo("12.5, 9");
    assertThat(column(row, "Needle Beams?")).isEqualTo("Yes");
    assertThat(column(row, "Bridge Length (metres)")).isEqualTo("24.56");
    assertThat(column(row, "Superstructure")).isEqualTo("Steel girder");
    assertThat(column(row, "Superstructure Comment")).isEqualTo("Salvaged girders");
    assertThat(column(row, "Abutment Left End")).isEqualTo("Concrete");
    assertThat(column(row, "Abutment Right End")).isEqualTo("Crib");
    assertThat(column(row, "Culvert Number")).isEmpty();
    assertThat(column(row, "Next Planned Inspection")).isEqualTo("2027/06/01");
    // A cell of several lines is quoted, its line breaks and quotes kept.
    assertThat(file).contains(
        "\"IDIR\\NEWER on 2024-11-02: New note\nIDIR\\OLDER on 2020-03-04: Old, \"\"quoted\"\" note\"");
  }

  @Test
  @DisplayName("writes a row per culvert record, and names the file for the structure")
  void writesACsvRowPerCulvert() {
    long id = structure("C1", s -> s.structureTypeClassCode("CUL"));
    culvert(id, 101, 1);
    culvert(id, 102, 2);
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(id, ReportFormat.CSV);

    assertThat(file.filename()).matches("StructureDetailReport_C1_\\d{4}-\\d{2}-\\d{2}\\.csv");
    assertThat(new String(file.content(), StandardCharsets.UTF_8).split("\r\n")).hasSize(3);
    assertThat(rows.rows(id)).extracting(StructureDetailReportService::csvRow)
        .extracting(row -> column(row, "Culvert Number"), row -> column(row, "Number of Spans"))
        .containsExactly(tuple("1", ""), tuple("2", ""));
  }

  private static String column(List<String> row, String heading) {
    return row.get(StructureDetailReportService.CSV_COLUMNS.indexOf(heading));
  }
}
