package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventorySummaryService.Row;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySort;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria.StructureInventorySummaryCriteriaBuilder;
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
 * The Structure Inventory – Summary report, against the database: which structures, in what
 * order, each value as legacy's template printed it, and the PDF and CSV made from them.
 *
 * <p>H2 stands in for Oracle, so this proves the predicates, the batch lookups and the formatting,
 * not Oracle's behaviour.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({StructureInventorySummaryService.class, StructureInventoryReportSupport.class,
    ReportRenderer.class})
class StructureInventorySummaryServiceTest {

  @Autowired
  private StructureInventorySummaryService service;

  @Autowired
  private StructureInventoryReportSupport support;

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
    when(codes.getDeckTypeCodes()).thenReturn(List.of(new CodeOptionResponse("TMB", "Timber")));
    when(codes.getStructureTypeClassCodes()).thenReturn(
        List.of(new CodeOptionResponse("PB", "Permanent Bridge")));
    for (String entity : List.of("InspectionReportStatusEntity", "StructureInspectionEntity",
        "ForestServiceBridgeEntity", "ForestServiceCulvertEntity", "CrossingStructureEntity",
        "CrossingSiteEntity", "CbrRoadSectionEntity", "OrgUnitEntity",
        "CrossingSiteStatusCodeEntity", "InspectionReportStatusCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    // CROSSING_SITE has real foreign keys to its org unit and status.
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build());
    for (String status : List.of("SUB", "RVD")) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(status).description(status)
          .effectiveDate(LocalDateTime.now().minusYears(10))
          .expiryDate(LocalDateTime.now().plusYears(10))
          .updateTimestamp(LocalDateTime.now()).build());
    }
    for (String status : List.of("ACT", "DEC")) {
      entityManager.persist(CrossingSiteStatusCodeEntity.builder()
          .crossingSiteStatusCode(status).description(status)
          .effectiveDate(LocalDateTime.now().minusYears(10))
          .expiryDate(LocalDateTime.now().plusYears(10))
          .updateTimestamp(LocalDateTime.now()).build());
    }
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
  }

  private void site(String id, UnaryOperator<CrossingSiteEntity.CrossingSiteEntityBuilder> with) {
    entityManager.persist(with.apply(CrossingSiteEntity.builder()
        .crossingSiteId(id).crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.5")).userKm(new BigDecimal("13"))
        .crossingSiteStatusCode("ACT").crossingSiteTypeCode("CRS").orgUnitNo(18L)
        .forestFileId("R00123").roadSectionId("01").capitalRoadInd("N")).build());
  }

  private long structure(
      String name, String siteId, UnaryOperator<CrossingStructureEntity.CrossingStructureEntityBuilder> with) {
    long id = nextId++;
    entityManager.persist(with.apply(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName(name).crossingSiteId(siteId)
        .structureTypeClassCode("PB").activeInd("Y").closeProximityInd("N")
        .portableStructureInd("N")).build());
    return id;
  }

  private void bridge(long structureId, String length, String deckType) {
    entityManager.persist(ForestServiceBridgeEntity.builder()
        .forestServiceBridgeId(structureId * 10).crossingStructureId(structureId).needleBeamInd("N")
        .totalBridgeLength(length == null ? null : new BigDecimal(length)).deckTypeCode(deckType)
        .build());
  }

  private void culvert(long structureId, String length, long openingWidth) {
    entityManager.persist(ForestServiceCulvertEntity.builder()
        .forestServiceCulvertId(structureId * 10).crossingStructureId(structureId).culvertNumber(1)
        .culvertLength(new BigDecimal(length)).engineeredCulvertMaterialCode("CSP")
        .openingWidth(openingWidth).build());
  }

  /** A routine inspection on this date, with these statuses, oldest first. */
  private void inspection(long structureId, long inspectionId, LocalDate date, long... statusIds) {
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(inspectionId)
        .crossingStructureId(structureId).strctreInspectionTypeCode("ROUT").inspectionDate(date)
        .build());
    for (long statusId : statusIds) {
      entityManager.persist(InspectionReportStatusEntity.builder()
          .inspectionReportStatusId(statusId).inspectionId(inspectionId)
          .inspectionReportStatusCode(statusId % 2 == 0 ? "RVD" : "SUB").entryUserid("IDIR\\X")
          .entryTimestamp(LocalDateTime.of(2024, 1, 1, 0, 0)).build());
    }
  }

  private List<Row> rows(UnaryOperator<StructureInventorySummaryCriteriaBuilder> with) {
    entityManager.flush();
    entityManager.clear();
    return service.rows(with.apply(StructureInventorySummaryCriteria.builder()).build());
  }

  private List<String> names(UnaryOperator<StructureInventorySummaryCriteriaBuilder> with) {
    return rows(with).stream().map(Row::structureName).toList();
  }

  @Test
  @DisplayName("prints each value as legacy's template did")
  void formatsALine() {
    site("62-001", site -> site.capitalRoadInd("Y"));
    long id = structure("B1", "62-001", s -> s.portableStructureInd("Y")
        .currentLoadRating(new BigDecimal("45.5")).yearBuilt(1998).fullLogHaulReplacementYear(2031));
    bridge(id, "1234.56", "TMB");
    // The later date wins the date; the newest status row (6) wins the status, though it belongs
    // to the earlier inspection — legacy's rule.
    inspection(id, 100, LocalDate.of(2020, 6, 1), 1, 6);
    inspection(id, 101, LocalDate.of(2023, 5, 2), 3);

    assertThat(rows(c -> c)).containsExactly(new Row(
        "62-001", "B1", "R00123-01", "Bowron FSR", "Deadman Creek", "Yes", "12.50", "1,234.6", "P",
        "46", "2023/05/02", "1998", "RVD", "2031", "Y"));
  }

  @Test
  @DisplayName("shows a bridge's length, else the culvert's; abbreviates the type/class")
  void lengthsAndTypeClasses() {
    site("62-001", s -> s);
    long bridged = structure("B1", "62-001", s -> s);
    bridge(bridged, null, null);
    culvert(bridged, "9", 600);
    long culverted = structure("C1", "62-001", s -> s.structureTypeClassCode("CUL"));
    culvert(culverted, "12", 600);
    structure("W1", "62-001", s -> s.structureTypeClassCode("WLC"));
    structure("T1", "62-001", s -> s.structureTypeClassCode("TB"));

    assertThat(rows(c -> c.sortBy(StructureInventorySort.STRUCTURE_ID)))
        .extracting(Row::structureName, Row::totalLength, Row::typeClass)
        .containsExactly(
            tuple("B1", "", "P"),
            tuple("C1", "12.0", "MC"),
            tuple("T1", "", "T"),
            tuple("W1", "", "W"));
  }

  @Test
  @DisplayName("leaves Last Inspctn Date blank when the routine inspections are undated")
  void undatedInspection() {
    site("62-001", s -> s);
    long id = structure("B1", "62-001", s -> s);
    // MAX over undated rows still answers for the structure — with no date.
    inspection(id, 100, null, 1);

    assertThat(rows(c -> c)).extracting(Row::lastInspectionDate, Row::inspectionStatus)
        .containsExactly(tuple("", "SUB"));
  }

  @Test
  @DisplayName("lists active structures only, a structure without a site included")
  void activeOnly() {
    structure("B1", null, s -> s);
    structure("B2", null, s -> s.activeInd("N"));

    assertThat(rows(c -> c)).extracting(Row::structureName, Row::siteId, Row::projectFile)
        .containsExactly(tuple("B1", "", ""));
  }

  @Test
  @DisplayName("Downrated: below design, at 0, unrated or under review — on a live bridge or log culvert")
  void downrated() {
    site("62-001", s -> s);
    site("62-002", s -> s.crossingSiteStatusCode("DEC"));
    structure("below", "62-001", s -> s.designLoadRating(new BigDecimal("60"))
        .currentLoadRating(new BigDecimal("40")));
    structure("zero", "62-001", s -> s.currentLoadRating(BigDecimal.ZERO));
    structure("unrated", "62-001", s -> s);
    structure("review", "62-001", s -> s.currentLoadRating(new BigDecimal("60"))
        .loadRatingUnknownIndicator("Y"));
    structure("full", "62-001", s -> s.designLoadRating(new BigDecimal("60"))
        .currentLoadRating(new BigDecimal("60")));
    structure("closed", "62-002", s -> s);
    structure("culvert", "62-001", s -> s.structureTypeClassCode("CUL"));

    assertThat(names(c -> c.downrated(true).sortBy(StructureInventorySort.STRUCTURE_ID)))
        .containsExactly("below", "review", "unrated", "zero");
  }

  @Test
  @DisplayName("filters on the bridge's deck type and the culvert's opening width, ends included")
  void bridgeAndCulvertFilters() {
    site("62-001", s -> s);
    long timber = structure("timber", "62-001", s -> s);
    bridge(timber, "10", "TMB");
    long steel = structure("steel", "62-001", s -> s);
    bridge(steel, "10", "STL");
    long narrow = structure("narrow", "62-001", s -> s.structureTypeClassCode("CUL"));
    culvert(narrow, "8", 600);
    long wide = structure("wide", "62-001", s -> s.structureTypeClassCode("CUL"));
    culvert(wide, "8", 1200);

    assertThat(names(c -> c.deckTypeCode("TMB"))).containsExactly("timber");
    assertThat(names(c -> c.openingWidthMin("1200").sortBy(StructureInventorySort.STRUCTURE_ID)))
        .containsExactly("wide");
    assertThat(names(c -> c.openingWidthMax("600"))).containsExactly("narrow");
  }

  @Test
  @DisplayName("filters on the site: business area, capital road, kilometres")
  void siteFilters() {
    site("62-001", s -> s.businessAreaOrgUnitNo(1832L).capitalRoadInd("Y"));
    site("62-002", s -> s.pointOfCommencementDistance(new BigDecimal("40")));
    structure("bcts", "62-001", s -> s);
    structure("far", "62-002", s -> s);

    assertThat(names(c -> c.businessAreaOrgUnit("1832"))).containsExactly("bcts");
    assertThat(names(c -> c.capitalRoad(true))).containsExactly("bcts");
    assertThat(names(c -> c.kiloStart("40"))).containsExactly("far");
  }

  @Test
  @DisplayName("sorts by project, branch and user km by default, and by legacy's other orders")
  void sorts() {
    site("62-002", s -> s.forestFileId("R00200").userKm(new BigDecimal("1")));
    site("62-001", s -> s.forestFileId("R00100").userKm(new BigDecimal("5")));
    site("62-003", s -> s.forestFileId("R00100").userKm(new BigDecimal("2")));
    structure("Z", "62-002", s -> s);
    structure("Y", "62-001", s -> s);
    structure("X", "62-003", s -> s);

    assertThat(names(c -> c)).containsExactly("X", "Y", "Z");
    assertThat(names(c -> c.sortBy(StructureInventorySort.SITE_ID))).containsExactly("Y", "Z", "X");
    assertThat(names(c -> c.sortBy(StructureInventorySort.STRUCTURE_ID)))
        .containsExactly("X", "Y", "Z");
  }

  @Test
  @DisplayName("describes the criteria in legacy's words, codes as their descriptions")
  void selectedCriteria() {
    assertThat(support.selectedCriteria(StructureInventorySummaryCriteria.builder()
        .orgUnit("18").deckTypeCode("TMB").yearBuiltStart("1990").kiloStart("1").kiloEnd("20")
        .capitalRoad(true).downrated(true).build()))
        .isEqualTo("Forest District: Prince George Natural Resource District "
            + "Downrated Structure: Yes Kilometres: 1 - 20 Year Installed: >= 1990 "
            + "Deck Type: Timber Capital Road: Yes "
            + "Sort By: Project Number, Branch, User KM (Asc)");
  }

  @Test
  @DisplayName("writes the CSV as the same columns, a header row first, values as data")
  void csv() {
    site("62-001", s -> s);
    structure("B1, west", "62-001", s -> s);
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(StructureInventorySummaryCriteria.builder().build(),
        ReportFormat.CSV);

    assertThat(file.mediaType()).isEqualTo(new MediaType("text", "csv"));
    assertThat(file.filename()).matches("StructureInventorySummaryReport_\\d{4}-\\d{2}-\\d{2}\\.csv");
    assertThat(new String(file.content(), StandardCharsets.UTF_8).split("\r\n")).containsExactly(
        "Site #,Structure #,Project File ID # / Br.,Road Name,Crossing Name,Portable?,KM,"
            + "Total Length (metres),Structure Type/Class,Current Load Rating GVW (tonnes),"
            + "Last Inspection Date,Year Superstructure Installed,Inspection Status,"
            + "Estimated Load Restriction Date,Capital Road",
        "62-001,\"B1, west\",R00123-01,Bowron FSR,Deadman Creek,No,12.5,,Permanent Bridge,,,,,,"
            + "No");
  }

  @Test
  @DisplayName("writes CSV numbers plain and codes in full, where the PDF prints legacy's forms")
  void csvValuesAreData() {
    site("62-001", s -> s.pointOfCommencementDistance(new BigDecimal("12.50"))
        .capitalRoadInd("Y"));
    long bridge = structure("B1", "62-001", s -> s);
    bridge(bridge, "1234.56", null);
    long culvert = structure("C1", "62-001", s -> s.structureTypeClassCode("CUL"));
    culvert(culvert, "1200", 600);
    entityManager.flush();
    entityManager.clear();

    List<Row> data = service.rows(StructureInventorySummaryCriteria.builder()
        .sortBy(StructureInventorySort.STRUCTURE_ID).build(),
        StructureInventorySummaryService.Style.data(
            Map.of("PB", "Permanent Bridge", "CUL", "Culvert")));

    assertThat(data).extracting(Row::kilometres, Row::totalLength, Row::typeClass, Row::capitalRoad)
        .containsExactly(
            tuple("12.5", "1234.56", "Permanent Bridge", "Yes"),
            tuple("12.5", "1200", "Culvert", "Yes"));
    // The PDF keeps legacy's printed forms.
    assertThat(service.rows(StructureInventorySummaryCriteria.builder()
        .sortBy(StructureInventorySort.STRUCTURE_ID).build()))
        .extracting(Row::kilometres, Row::totalLength, Row::typeClass, Row::capitalRoad)
        .containsExactly(
            tuple("12.50", "1,234.6", "P", "Y"),
            tuple("12.50", "1,200.0", "MC", "Y"));
  }

  @Test
  @DisplayName("fills legacy's template: header, criteria and lines, in an embedded Arial")
  void pdf() throws Exception {
    site("62-001", s -> s);
    structure("B1", "62-001", s -> s);
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(
        StructureInventorySummaryCriteria.builder().orgUnit("18").build(), ReportFormat.PDF);

    assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(file.filename())
        .matches("StructureInventorySummaryReport_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    PdfReader reader = new PdfReader(file.content());
    String page = new PdfTextExtractor(reader).getTextFromPage(1);
    assertThat(page).contains(
        "BC Ministry of Forests, Lands, and Natural Resource Operations",
        "Structure Inventory - Summary Report", "IDIR\\REPORTER",
        "LOCAL", "CBR008", "Forest District: Prince George Natural Resource District",
        "62-001", "Bowron FSR", "Deadman Creek", "Corporate Bridge Register (CBR)");
  }
}
