package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.AbutmentCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.BuiltByCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.DeckTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.DesignVehicleLoadCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredClvrtMaterialCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredCulvertTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCurbTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureSourceCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.SuperstructureTypeCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService.Block;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria.StructureInventoryDetailedCriteriaBuilder;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySort;
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
 * The Structure Inventory – Detailed report, against the database: which structures, each
 * block's values as legacy's template printed them, and the PDF made from them.
 *
 * <p>H2 stands in for Oracle, so this proves the predicates, the joins, the batch lookups and the
 * formatting, not Oracle's behaviour.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({StructureInventoryDetailedService.class, StructureInventoryReportSupport.class,
    StructureInventoryNames.class, ReportRenderer.class})
class StructureInventoryDetailedServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(10);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(10);

  @Autowired
  private StructureInventoryDetailedService service;

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
    when(codes.getForestDistricts()).thenReturn(
        List.of(new OrgUnitResponse("18", "DPG", "Prince George Natural Resource District")));
    for (String entity : List.of("ForestServiceBridgeSpanEntity", "StructureInspectionEntity",
        "ForestServiceBridgeEntity", "ForestServiceCulvertEntity", "CrossingStructureEntity",
        "CrossingSiteEntity", "CbrRoadSectionEntity", "OrgUnitEntity",
        "CrossingSiteStatusCodeEntity", "StructureTypeClassCodeEntity",
        "SuperstructureTypeCodeEntity", "DeckTypeCodeEntity", "AbutmentCodeEntity",
        "StructureCurbTypeCodeEntity", "DesignVehicleLoadCodeEntity", "BuiltByCodeEntity",
        "CrossingSiteTypeCodeEntity", "StructureSourceCodeEntity",
        "StructureInspectionStatusCodeEntity", "EngineeredCulvertTypeCodeEntity",
        "EngineeredClvrtMaterialCodeEntity", "ClientPublicEntity", "CbrOrgUnitEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build());
    entityManager.persist(CrossingSiteStatusCodeEntity.builder()
        .crossingSiteStatusCode("ACT").description("Active").effectiveDate(PAST)
        .expiryDate(FUTURE).updateTimestamp(PAST).build());
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
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
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId("62-001").crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.5")).userKm(new BigDecimal("1234.5"))
        .crossingSiteStatusCode("ACT").crossingSiteTypeCode("CRS").orgUnitNo(18L)
        .forestFileId("R00123").roadSectionId("01").capitalRoadInd("Y").build());
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

  private void bridge(long structureId, int spans) {
    long bridgeId = structureId * 10;
    entityManager.persist(ForestServiceBridgeEntity.builder()
        .forestServiceBridgeId(bridgeId).crossingStructureId(structureId).needleBeamInd("N")
        .totalBridgeLength(new BigDecimal("24.56")).superstructureTypeCode("STL")
        .deckTypeCode("TIM").leftAbutmentCode("CON").rightAbutmentCode("CLT")
        .structureCurbTypeCode("TMB").build());
    for (int span = 1; span <= spans; span++) {
      entityManager.persist(ForestServiceBridgeSpanEntity.builder()
          .forestServiceBridgeId(bridgeId).spanNumber(span).spanLength(new BigDecimal("12"))
          .build());
    }
  }

  private List<Block> blocks(UnaryOperator<StructureInventoryDetailedCriteriaBuilder> with) {
    entityManager.flush();
    entityManager.clear();
    return service.blocks(with.apply(StructureInventoryDetailedCriteria.builder()).build());
  }

  @Test
  @DisplayName("prints each value of a bridge's block as legacy's template did")
  void formatsABridge() {
    long id = structure("B1", s -> s.portableStructureInd("Y")
        .designLoadRating(new BigDecimal("1063.5")).currentLoadRating(new BigDecimal("45.5"))
        .yearBuilt(1998).loadRatingUnknownIndicator("Y"));
    bridge(id, 2);
    // Any status counts here — legacy's Detailed asks for none.
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(100L)
        .crossingStructureId(id).strctreInspectionTypeCode("ROUT")
        .inspectionDate(LocalDate.of(2023, 5, 2)).build());

    assertThat(blocks(c -> c)).containsExactly(new Block(
        "B1", "62-001", "Active", "2023/05/02", "12.50", "1,234.50", "Permanent Bridge", "Yes",
        "R00123", "01", "Prince George", "Bowron FSR", "Deadman Creek", "Steel girder", "Timber",
        "Concrete", "Crib", "1,064", "46", "2", "24.6", "1998", "", "Y", "Timber curb", "Y"));
  }

  @Test
  @DisplayName("shows a culvert's length and no spans, its bridge values blank")
  void formatsACulvert() {
    long id = structure("C1", s -> s.structureTypeClassCode("CUL"));
    entityManager.persist(ForestServiceCulvertEntity.builder()
        .forestServiceCulvertId(id * 10).crossingStructureId(id).culvertNumber(1)
        .culvertLength(new BigDecimal("1200")).build());

    assertThat(blocks(c -> c)).extracting(Block::structureType, Block::totalLength,
            Block::spanCount, Block::superstructureType, Block::curbType)
        .containsExactly(tuple("Culvert", "1200.0", "0", "", ""));
  }

  @Test
  @DisplayName("leaves out a structure with no type/class, as legacy's inner join did")
  void leavesOutNoTypeClass() {
    structure("B1", s -> s);
    structure("unclassed", s -> s.structureTypeClassCode(null));
    structure("unknown", s -> s.structureTypeClassCode("XX"));

    assertThat(blocks(c -> c)).extracting(Block::structureName).containsExactly("B1");
  }

  @Test
  @DisplayName("filters on the criteria it shares with the Summary, and sorts as it does")
  void sharedFilters() {
    structure("B2", s -> s.yearBuilt(1990));
    structure("B1", s -> s.yearBuilt(2010));
    structure("archived", s -> s.activeInd("N").yearBuilt(2010));

    assertThat(blocks(c -> c.yearBuiltStart("2000"))).extracting(Block::structureName)
        .containsExactly("B1");
    assertThat(blocks(c -> c.capitalRoad(true).sortBy(StructureInventorySort.STRUCTURE_ID)))
        .extracting(Block::structureName).containsExactly("B1", "B2");
  }

  /** The code tables, maintainer and org units the CSV names, and a region for the district. */
  private void givenCsvNames() {
    entityManager.persist(DesignVehicleLoadCodeEntity.builder().designVehicleLoadCode("L165")
        .description("L-165").effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(BuiltByCodeEntity.builder().builtByCode("FS").description("Forest Service")
        .effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(CrossingSiteTypeCodeEntity.builder().crossingSiteTypeCode("CRS")
        .description("Stream crossing").effectiveDate(PAST).expiryDate(FUTURE)
        .updateTimestamp(PAST).build());
    entityManager.persist(StructureSourceCodeEntity.builder().structureSourceCode("INV")
        .description("Inventory").effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(StructureInspectionStatusCodeEntity.builder()
        .structureInspectionStatusCode("INS").description("Inspected").effectiveDate(PAST)
        .expiryDate(FUTURE).updateTimestamp(PAST).build());
    entityManager.persist(EngineeredCulvertTypeCodeEntity.builder().engineeredCulvertTypeCode("RND")
        .description("Round").effectiveDate(PAST).expiryDate(FUTURE).updateTimestamp(PAST).build());
    entityManager.persist(EngineeredClvrtMaterialCodeEntity.builder()
        .engineeredClvrtMaterialCode("CSP").description("Corrugated steel").effectiveDate(PAST)
        .expiryDate(FUTURE).build());
    entityManager.persist(ClientPublicEntity.builder().clientNumber("00001012")
        .clientName("CANFOR CORPORATION").build());
    for (Object[] unit : List.of(new Object[] {26L, "Robson Valley"},
        new Object[] {1832L, "BCTS Prince George"}, new Object[] {1001L, "Northern Interior"})) {
      entityManager.persist(CbrOrgUnitEntity.builder().orgUnitNo((Long) unit[0])
          .orgUnitName((String) unit[1]).build());
    }
    // Read only on the entity: set as the database holds it.
    entityManager.flush();
    entityManager.createNativeQuery("UPDATE THE.ORG_UNIT SET ROLLUP_REGION_NO = 1001 "
        + "WHERE ORG_UNIT_NO = 18").executeUpdate();
    entityManager.createNativeQuery("UPDATE THE.CROSSING_SITE SET MANAGEMENT_ORG_UNIT_NO = 26, "
        + "BUSINESS_AREA_ORG_UNIT_NO = 1832, CLIENT_NUMBER = '00001012', "
        + "STRUCTURE_INSPCTN_STATUS_CODE = 'INS', LONGITUDE = -122.75, LATITUDE = 53.9 "
        + "WHERE CROSSING_SITE_ID = '62-001'").executeUpdate();
  }

  @Test
  @DisplayName("writes legacy's fuller CSV, values as the Summary's CSV writes them")
  void csvRow() {
    givenCsvNames();
    long id = structure("B1", s -> s.portableStructureInd("Y")
        .designLoadRating(new BigDecimal("1063.50")).currentLoadRating(new BigDecimal("45.5"))
        .yearBuilt(1998).loadRatingUnknownIndicator("N").designVehicleLoadCode("L165")
        .builtByCode("FS").nextPlannedInspectionDate(LocalDate.of(2027, 6, 1))
        .lightVehicleReplacementYear(2040).estimatedReplacementCost(250000L)
        .estimatedClosureYear(2045).fullLogHaulReplacementYear(2031).structureSourceCode("INV"));
    bridge(id, 2);
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(100L)
        .crossingStructureId(id).strctreInspectionTypeCode("ROUT")
        .inspectionDate(LocalDate.of(2023, 5, 2)).build());

    entityManager.flush();
    entityManager.clear();
    List<List<String>> rows = service.csvRows(StructureInventoryDetailedCriteria.builder().build());

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsExactly(
        "B1", "62-001", "Active", "2023/05/02", "12.5", "1234.5", "Permanent Bridge", "Yes",
        "R00123", "01", "BCTS Prince George", "Bowron FSR", "Deadman Creek", "Steel girder",
        "Timber", "Concrete", "Crib", "1063.5", "2", "24.56", "1998", "", "L-165", "",
        "Forest Service", "2027/06/01", "CANFOR CORPORATION", "Stream crossing", "2040", "250000",
        "2045", "2031", "-122.75", "53.9", "Inventory", "", "", "", "", "", "", "", "",
        "Robson Valley", "Prince George", "Northern Interior", "Inspected", "", "45.5", "Yes",
        "Timber curb", "No");
  }

  @Test
  @DisplayName("writes a culvert's details in the CSV, its bridge columns blank and no spans")
  void csvCulvert() {
    givenCsvNames();
    long id = structure("C1", s -> s.structureTypeClassCode("CUL"));
    entityManager.persist(ForestServiceCulvertEntity.builder()
        .forestServiceCulvertId(id * 10).crossingStructureId(id).culvertNumber(1)
        .culvertLength(new BigDecimal("12.0")).engineeredCulvertTypeCode("RND")
        .engineeredCulvertMaterialCode("CSP").openingHeight(900L).openingWidth(1200L)
        .slope(new BigDecimal("2.50")).inletCoverDepth(300L).outletCoverDepth(450L).build());

    entityManager.flush();
    entityManager.clear();
    List<String> row =
        service.csvRows(StructureInventoryDetailedCriteria.builder().build()).get(0);

    assertThat(row.subList(13, 20)).containsExactly("", "", "", "", "", "0", "12");
    assertThat(row.subList(35, 43))
        .containsExactly("Round", "Corrugated steel", "900", "1200", "12", "2.5", "300", "450");
  }

  @Test
  @DisplayName("writes the CSV under legacy's headings, less Structure ID, named as legacy named it")
  void csvFile() {
    structure("B1", s -> s);
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(StructureInventoryDetailedCriteria.builder().build(),
        ReportFormat.CSV);

    assertThat(file.mediaType()).isEqualTo(new MediaType("text", "csv"));
    assertThat(file.filename())
        .matches("StructureInventoryDetailReport_\\d{4}-\\d{2}-\\d{2}\\.csv");
    String header = new String(file.content(), StandardCharsets.UTF_8)
        .split("\r\n")[0];
    assertThat(header.split(",")).hasSize(52).startsWith("Structure #", "Site #")
        .endsWith("Curb Type", "Load Rating Review Required").doesNotContain("Structure ID");
  }

  @Test
  @DisplayName("maps each structure with a location, its pop-up legacy's fields as the CSV writes them")
  void kmz() throws Exception {
    givenCsvNames();
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId("62-002").crossingName("No GPS").crossingSiteStatusCode("ACT")
        .orgUnitNo(18L).build());
    long id = structure("B1 & <2>", s -> s.designVehicleLoadCode("L165")
        .currentLoadRating(new BigDecimal("45.5")));
    bridge(id, 2);
    structure("NOWHERE", s -> s.crossingSiteId("62-002"));
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.kmz(StructureInventoryDetailedCriteria.builder().build());

    assertThat(file.mediaType().toString()).isEqualTo("application/vnd.google-earth.kmz");
    assertThat(file.filename())
        .matches("StructureInventoryDetailReport_\\d{4}-\\d{2}-\\d{2}\\.kmz");
    org.w3c.dom.Document kml = kml(file.content());
    org.w3c.dom.NodeList placemarks = kml.getElementsByTagName("Placemark");
    // The structure with no latitude or longitude is left out.
    assertThat(placemarks.getLength()).isEqualTo(1);
    org.w3c.dom.Element placemark = (org.w3c.dom.Element) placemarks.item(0);
    assertThat(text(placemark, "name")).isEqualTo("B1 & <2>");
    assertThat(text(placemark, "coordinates")).isEqualTo("-122.75,53.9,0");
    Map<String, String> data = new java.util.HashMap<>();
    org.w3c.dom.NodeList values = placemark.getElementsByTagName("SimpleData");
    for (int index = 0; index < values.getLength(); index++) {
      org.w3c.dom.Element value = (org.w3c.dom.Element) values.item(index);
      data.put(value.getAttribute("name"), value.getTextContent());
    }
    assertThat(data).hasSize(38).contains(
        Map.entry("Site__", "62-001"), Map.entry("Structure__", "B1 & <2>"),
        Map.entry("Site_Type", "Stream crossing"), Map.entry("Forest_District", "Prince George"),
        Map.entry("Br_", "01"), Map.entry("KM", "12.5"), Map.entry("Design_Vehicle", "L-165"),
        Map.entry("Current_Load_Rating_GVW__tonnes_", "45.5"),
        Map.entry("Inspection_Status", "Inspected"), Map.entry("__Spans", "2"),
        Map.entry("Total_Length__metres_", "24.56"),
        Map.entry("GPS_Coordinates_in_decimal_degrees_Longs", "-122.75"));
    String balloon = kml.getElementsByTagName("BalloonStyle").item(0).getTextContent();
    assertThat(balloon).contains("<b>Site #</b>", "$[StructureInventoryDetailReport/Site__]",
        "<b>Outlet Cover Depth</b>");
    assertThat(kml.getElementsByTagName("href").item(0).getTextContent())
        .endsWith("ylw-pushpin.png");
  }

  @Test
  @DisplayName("maps another report's structures in its order, each once, under its name")
  void kmzOf() throws Exception {
    givenCsvNames();
    long first = structure("B1", s -> s);
    structure("B2", s -> s);
    long third = structure("B3", s -> s);
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.kmzOf(List.of(third, first, third, 999L), "Inspections Due - Summary",
        "InspectionsDueSummaryReport");

    assertThat(file.filename()).matches("InspectionsDueSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.kmz");
    org.w3c.dom.NodeList placemarks = kml(file.content()).getElementsByTagName("Placemark");
    List<String> names = new java.util.ArrayList<>();
    for (int index = 0; index < placemarks.getLength(); index++) {
      names.add(text((org.w3c.dom.Element) placemarks.item(index), "name"));
    }
    // Not B2, which the report did not list; B3 once; the unknown id skipped.
    assertThat(names).containsExactly("B3", "B1");
  }

  /** The KML inside a KMZ, parsed. */
  private static org.w3c.dom.Document kml(byte[] kmz) throws Exception {
    try (java.util.zip.ZipInputStream zip =
        new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(kmz))) {
      assertThat(zip.getNextEntry().getName()).isEqualTo("doc.kml");
      javax.xml.parsers.DocumentBuilderFactory factory =
          javax.xml.parsers.DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(zip.readAllBytes()));
    }
  }

  private static String text(org.w3c.dom.Element element, String tag) {
    return element.getElementsByTagName(tag).item(0).getTextContent();
  }

  @Test
  @DisplayName("fills legacy's template: header, criteria and a block, named as legacy named it")
  void pdf() throws Exception {
    bridge(structure("B1", s -> s), 1);
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(
        StructureInventoryDetailedCriteria.builder().orgUnit("18").build(), ReportFormat.PDF);

    assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(file.filename())
        .matches("StructureInventoryDetailReport_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    String page = new PdfTextExtractor(new PdfReader(file.content())).getTextFromPage(1);
    assertThat(page).contains(
        "BC Ministry of Forests, Lands, and Natural Resource Operations",
        "Structure Inventory - Detailed Report", "IDIR\\REPORTER", "CBR007",
        "Forest District: Prince George Natural Resource District",
        "Structure #: B1", "Deadman Creek", "Steel girder", "Timber curb",
        "Load Rating Review Required:", "Corporate Bridge Register (CBR)");
  }
}
