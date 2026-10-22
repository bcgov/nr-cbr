package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.AbutmentCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.SuperstructureTypeCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria.InspectionsDueCriteriaBuilder;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueSort;
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
 * Inspections Due – Summary and – Detailed, against the database: which structures, in what
 * order, each value as the templates print it, and the PDFs made from them.
 *
 * <p>H2 stands in for Oracle, so this proves the predicates, the joins and the formatting, not
 * Oracle's behaviour.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({InspectionsDueSummaryService.class, InspectionsDueDetailedService.class,
    InspectionsDueRows.class,
    InspectionsDueCriteriaLine.class, StructureInventoryReportSupport.class, ReportRenderer.class})
class InspectionsDueSummaryServiceTest {

  @Autowired
  private InspectionsDueSummaryService service;

  @Autowired
  private InspectionsDueDetailedService detailedService;

  @Autowired
  private InspectionsDueRows rows;

  @Autowired
  private InspectionsDueCriteriaLine criteriaLine;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @MockitoBean
  private ConfigurationService codes;

  private long nextStructure = 1;
  private long nextBridge = 500;
  private long nextCulvert = 700;
  private long nextInspection = 100;
  private long nextStatus = 1000;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\REPORTER");
    when(codes.getSpecialEquipmentCodes()).thenReturn(
        List.of(new CodeOptionResponse("BOAT", "Boat")));
    when(codes.getSpecialAccessCodes()).thenReturn(
        List.of(new CodeOptionResponse("4X4", "Four-wheel drive")));
    when(codes.getForestDistricts()).thenReturn(
        List.of(new OrgUnitResponse("18", "DPG", "Prince George Natural Resource District")));
    when(codes.getStructureTypeClassCodes()).thenReturn(List.of(
        new CodeOptionResponse("PB", "Permanent Bridge"),
        new CodeOptionResponse("CUL", "Major Culvert")));
    for (String entity : List.of("StructureCommentEntity", "InspectionReportStatusEntity",
        "StructureInspectionEntity", "ForestServiceBridgePierEntity",
        "ForestServiceBridgeSpanEntity", "ForestServiceBridgeEntity",
        "ForestServiceCulvertEntity", "CrossingStructureEntity", "CrossingSiteEntity",
        "CbrRoadSectionEntity", "OrgUnitEntity", "InspectionReportStatusCodeEntity",
        "AbutmentCodeEntity", "SuperstructureTypeCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (String[] code : List.of(new String[] {"SUB", "Submitted"},
        new String[] {"RVD", "Reviewed"}, new String[] {"ACC", "Accepted"})) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(code[0]).description(code[1]).build());
    }
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build());
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(9L).orgUnitCode("DMH").orgUnitName("100 Mile House").build());
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
    site("62-001", 18L, s -> s);
  }

  private void site(
      String id, Long district, UnaryOperator<CrossingSiteEntity.CrossingSiteEntityBuilder> with) {
    entityManager.persist(with.apply(CrossingSiteEntity.builder()
        .crossingSiteId(id).crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.5")).userKm(new BigDecimal("1234.75"))
        .orgUnitNo(district).forestFileId("R00123").roadSectionId("01")
        .specialAccessRqmtCode("4X4")).build());
  }

  private long structure(
      String name, UnaryOperator<CrossingStructureEntity.CrossingStructureEntityBuilder> with) {
    long id = nextStructure++;
    entityManager.persist(with.apply(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName(name).crossingSiteId("62-001")
        .structureTypeClassCode("PB").activeInd("Y").closeProximityInd("N")
        .portableStructureInd("N").nextPlannedInspectionDate(LocalDate.of(2026, 7, 15))
        .specialEquipmentRqmtCode("BOAT")).build());
    return id;
  }

  private void bridge(long structureId, String length, int piers) {
    long bridgeId = nextBridge++;
    entityManager.persist(ForestServiceBridgeEntity.builder().forestServiceBridgeId(bridgeId)
        .crossingStructureId(structureId).totalBridgeLength(new BigDecimal(length)).build());
    for (long pier = 1; pier <= piers; pier++) {
      entityManager.persist(ForestServiceBridgePierEntity.builder()
          .forestServiceBridgeId(bridgeId).pierNumber(pier).entryUserid("IDIR\\X")
          .entryTimestamp(LocalDateTime.now()).updateUserid("IDIR\\X")
          .updateTimestamp(LocalDateTime.now()).build());
    }
  }

  /** A bridge as the Detailed prints it: its superstructure, abutments and spans. */
  private void detailedBridge(long structureId, String left, String right, int spans) {
    long bridgeId = nextBridge++;
    entityManager.persist(ForestServiceBridgeEntity.builder().forestServiceBridgeId(bridgeId)
        .crossingStructureId(structureId).totalBridgeLength(new BigDecimal("1024.56"))
        .superstructureTypeCode("STL").leftAbutmentCode(left).rightAbutmentCode(right).build());
    for (int span = 1; span <= spans; span++) {
      entityManager.persist(ForestServiceBridgeSpanEntity.builder().forestServiceBridgeId(bridgeId)
          .spanNumber(span).spanLength(BigDecimal.TEN).entryUserid("IDIR\\X")
          .entryTimestamp(LocalDateTime.now()).updateUserid("IDIR\\X")
          .updateTimestamp(LocalDateTime.now()).build());
    }
  }

  private void codes() {
    for (String[] code : List.of(new String[] {"CON", "Concrete"},
        new String[] {"TCR", "Timber crib"})) {
      entityManager.persist(AbutmentCodeEntity.builder().abutmentCode(code[0])
          .description(code[1]).build());
    }
    entityManager.persist(SuperstructureTypeCodeEntity.builder().superstructureTypeCode("STL")
        .description("Steel girder").build());
  }

  private List<Map<String, String>> detailed(UnaryOperator<InspectionsDueCriteriaBuilder> with) {
    entityManager.flush();
    entityManager.clear();
    return rows.detailed(with.apply(InspectionsDueCriteria.builder()).build());
  }

  private void culvert(long structureId, String length) {
    entityManager.persist(ForestServiceCulvertEntity.builder()
        .forestServiceCulvertId(nextCulvert++).crossingStructureId(structureId)
        .culvertLength(new BigDecimal(length)).build());
  }

  private void inspection(long structureId, String type, LocalDate date, String... statuses) {
    long id = nextInspection++;
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(id)
        .crossingStructureId(structureId).strctreInspectionTypeCode(type).inspectionDate(date)
        .inspectorName("Jane Smith").pengInspectionReqInd("N").build());
    for (String status : statuses) {
      entityManager.persist(InspectionReportStatusEntity.builder()
          .inspectionReportStatusId(nextStatus++).inspectionId(id)
          .inspectionReportStatusCode(status).entryUserid("IDIR\\X")
          .entryTimestamp(LocalDateTime.of(2025, 1, 1, 8, 0)).build());
    }
  }

  private void comment(long structureId, String planned, LocalDateTime updated, String text) {
    entityManager.persist(StructureCommentEntity.builder().crossingStructureId(structureId)
        .plannedInspectionCmtInd(planned).structureComment(text).entryUserid("IDIR\\PLANNER")
        .entryTimestamp(updated).updateUserid("IDIR\\PLANNER").updateTimestamp(updated).build());
  }

  private List<Map<String, String>> rows(UnaryOperator<InspectionsDueCriteriaBuilder> with) {
    entityManager.flush();
    entityManager.clear();
    return rows.summary(with.apply(InspectionsDueCriteria.builder()).build());
  }

  private List<String> names(UnaryOperator<InspectionsDueCriteriaBuilder> with) {
    return rows(with).stream().map(row -> row.get("CROSSING_STRUCTURE_NAME")).toList();
  }

  @Test
  @DisplayName("prints a structure, its site, district and requirements as the template does")
  void formatsAStructure() {
    long id = structure("B1", s -> s);
    bridge(id, "24.56", 2);
    inspection(id, "ROUT", LocalDate.of(2024, 6, 12), "SUB", "ACC");

    assertThat(rows(c -> c)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("CROSSING_STRUCTURE_NAME", "B1"),
        entry("CROSSING_SITE_ID", "62-001"),
        entry("ROAD_SECT_NAME", "Bowron FSR"),
        entry("POINT_OF_COMMENCEMENT_DISTANCE", "12.50"),
        entry("USER_KM", "1,234.75"),
        entry("CROSSING_NAME", "Deadman Creek"),
        entry("LAST_INSPECTION_DATE", "2024/06/12"),
        entry("NEXT_PLANNED_INSPECTION_DATE", "2026/07/15"),
        entry("FOREST_FILE_ID", "R00123"),
        entry("ROAD_SECTION_ID", "01"),
        entry("TOTAL_LENGTH", "24.6"),
        entry("STRUCTURE_TYPE_CLASS_CODE", "PB"),
        entry("PIER_COUNT", "2"),
        entry("FOREST_DISTRICT", "Prince George"),
        entry("FOREST_DISTRICT_KEY", "18"),
        entry("SPECIAL_EQUIPMENT_RQMT_CODE", "Boat"),
        entry("SPECIAL_ACCESS_RQMT_CODE", "Four-wheel drive")));
  }

  @Test
  @DisplayName("lists a structure once per bridge and culvert record, as legacy's left joins did")
  void aRowPerRecord() {
    structure("A0", s -> s);
    long twoCulverts = structure("A1", s -> s.structureTypeClassCode("CUL"));
    culvert(twoCulverts, "18");
    culvert(twoCulverts, "21.25");
    long both = structure("A2", s -> s);
    bridge(both, "30", 1);
    culvert(both, "9");

    List<Map<String, String>> found = rows(c -> c);

    assertThat(found).extracting(row -> row.get("CROSSING_STRUCTURE_NAME") + " "
            + row.get("TOTAL_LENGTH") + " " + row.get("PIER_COUNT"))
        .containsExactly("A0 null 0", "A1 18.0 0", "A1 21.2 0", "A2 30.0 1");
    // Legacy's patterns round half-even, as here: 21.25 prints 21.2.
    assertThat(found.getFirst()).containsEntry("LAST_INSPECTION_DATE", "")
        .containsEntry("PLN_INSP_CMT", null);
  }

  @Test
  @DisplayName("takes the last routine inspection ever reviewed or accepted")
  void lastInspection() {
    long id = structure("B1", s -> s);
    inspection(id, "ROUT", LocalDate.of(2023, 5, 1), "SUB", "RVD", "SUB");
    inspection(id, "ROUT", LocalDate.of(2024, 5, 1), "SUB");
    inspection(id, "SPEC", LocalDate.of(2025, 5, 1), "ACC");

    assertThat(rows(c -> c).getFirst()).containsEntry("LAST_INSPECTION_DATE", "2023/05/01");
  }

  @Test
  @DisplayName("joins the planned-inspection comments newest first, each dated yyyy-MM-dd")
  void plannedComments() {
    long id = structure("B1", s -> s);
    comment(id, "Y", LocalDateTime.of(2024, 3, 1, 9, 0), "Bring waders");
    comment(id, "Y", LocalDateTime.of(2025, 4, 2, 9, 0), "Gate locked");
    comment(id, "N", LocalDateTime.of(2025, 5, 3, 9, 0), "A general comment");

    assertThat(rows(c -> c).getFirst()).containsEntry("PLN_INSP_CMT",
        "IDIR\\PLANNER on 2025-04-02: Gate locked\nIDIR\\PLANNER on 2024-03-01: Bring waders");
  }

  @Test
  @DisplayName("matches the next planned date inclusively, or its absence, setting the dates aside")
  void nextPlannedDate() {
    structure("JUN30", s -> s.nextPlannedInspectionDate(LocalDate.of(2026, 6, 30)));
    structure("JUL01", s -> s.nextPlannedInspectionDate(LocalDate.of(2026, 7, 1)));
    structure("NONE", s -> s.nextPlannedInspectionDate(null));

    assertThat(names(c -> c.nextPlannedInspectionDateStart("2026/06/30")))
        .containsExactlyInAnyOrder("JUN30", "JUL01");
    assertThat(names(c -> c.nextPlannedInspectionDateEnd("2026/6/30"))).containsExactly("JUN30");
    assertThat(names(c -> c.nextPlannedInspectionDateStart("2026/07/01")
        .nextPlannedInspectionDateEnd("2026/07/01"))).containsExactly("JUL01");
    assertThat(names(c -> c.noNextPlannedInspectionDate(true)
        .nextPlannedInspectionDateStart("2026/07/01"))).containsExactly("NONE");
  }

  @Test
  @DisplayName("filters on the structure and its site as legacy, active structures only")
  void criteria() {
    structure("B1", s -> s);
    structure("NOEQUIP", s -> s.specialEquipmentRqmtCode(null).closeProximityInd("Y"));
    structure("TB", s -> s.structureTypeClassCode("TB"));
    structure("GONE", s -> s.activeInd("N"));
    site("62-002", 9L, s -> s.structureInspectionStatusCode("OVR").specialAccessRqmtCode(null));
    structure("OVERDUE", s -> s.crossingSiteId("62-002"));

    assertThat(names(c -> c)).containsExactlyInAnyOrder("B1", "NOEQUIP", "TB", "OVERDUE");
    assertThat(names(c -> c.specialEquipmentCode("NONE"))).containsExactly("NOEQUIP");
    assertThat(names(c -> c.specialEquipmentCode("BOAT")))
        .containsExactlyInAnyOrder("B1", "TB", "OVERDUE");
    assertThat(names(c -> c.closeProximity("Y"))).containsExactly("NOEQUIP");
    assertThat(names(c -> c.closeProximity("N")))
        .containsExactlyInAnyOrder("B1", "TB", "OVERDUE");
    assertThat(names(c -> c.structureTypeClassCode("TB"))).containsExactly("TB");
    assertThat(names(c -> c.structureInspectionStatusCode("OVR"))).containsExactly("OVERDUE");
    assertThat(names(c -> c.orgUnit("9"))).containsExactly("OVERDUE");
    assertThat(names(c -> c.specialAccessCode("4X4")))
        .containsExactlyInAnyOrder("B1", "NOEQUIP", "TB");
  }

  @Test
  @DisplayName("orders by district number, then the sort chosen, branch, km and user km")
  void order() {
    site("62-002", 9L, s -> s.forestFileId("R00999"));
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00999").roadSectionId("01").roadSectName("Alpha FSR").build());
    site("62-003", 18L, s -> s.forestFileId("R00999").pointOfCommencementDistance(BigDecimal.ONE));
    structure("ON-BOWRON", s -> s);
    structure("IN-100-MILE", s -> s.crossingSiteId("62-002"));
    structure("ON-ALPHA", s -> s.crossingSiteId("62-003"));

    // 100 Mile House is district 9, Prince George 18: the number, not the name, as legacy.
    assertThat(names(c -> c)).containsExactly("IN-100-MILE", "ON-ALPHA", "ON-BOWRON");
    assertThat(names(c -> c.sortBy(InspectionsDueSort.FOREST_FILE_ID)))
        .containsExactly("IN-100-MILE", "ON-BOWRON", "ON-ALPHA");
  }

  @Test
  @DisplayName("prints the criteria in legacy's order and wording, codes by name")
  void criteriaLine() {
    InspectionsDueCriteria criteria = InspectionsDueCriteria.builder()
        .forestFileId("R00123").orgUnit("18").nextPlannedInspectionDateStart("2026/01/01")
        .specialEquipmentCode("NONE").closeProximity("N").structureTypeClassCode("PB").build();

    assertThat(criteriaLine.of(criteria, InspectionsDueSort.FOREST_SERVICE_ROAD)).isEqualTo(
        "Project File ID: R00123 Forest District: Prince George Natural Resource District"
            + " Next Planned Inspection Date: >= 2026/01/01 Special Equipment Requirements: None"
            + " Close Proximity Inspection Required: No Type/Class: Permanent Bridge"
            + " Sort: Forest Service Road");
    assertThat(criteriaLine.of(InspectionsDueCriteria.builder().structureName("B1")
        .noNextPlannedInspectionDate(true).build(), null))
        .isEqualTo("Structure #: B1 No Next Planned Inspection Date");
  }

  @Test
  @DisplayName("produces the landscape PDF, a heading per district, long comments in full")
  void producesThePdf() throws Exception {
    long id = structure("B1", s -> s);
    bridge(id, "24.56", 0);
    String longComment = "Approach washed out; bring the long ladder and the chainsaw. "
        .repeat(4) + "ENDOFCOMMENT";
    comment(id, "Y", LocalDateTime.of(2025, 4, 2, 9, 0), longComment);
    site("62-002", 9L, s -> s);
    structure("C1", s -> s.crossingSiteId("62-002").structureTypeClassCode("CUL"));
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(InspectionsDueCriteria.builder().build(), ReportFormat.PDF);

    assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(file.filename()).matches("InspectionsDueSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    PdfReader reader = new PdfReader(file.content());
    assertThat(reader.getPageSizeWithRotation(1).getWidth())
        .isGreaterThan(reader.getPageSizeWithRotation(1).getHeight());
    StringBuilder all = new StringBuilder();
    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
      all.append(new PdfTextExtractor(reader).getTextFromPage(page)).append('\n');
    }
    assertThat(all.toString().replaceAll("\\s+", " ")).contains(
        "BC Ministry of Forests, Lands, and Natural Resource Operations",
        "Inspections Due - Summary", "IDIR\\REPORTER", "CBR004", "Sort: Forest Service Road",
        "Forest District: 100 Mile House", "Forest District: Prince George",
        "C1", "MC", "B1", "2026/07/15", "ENDOFCOMMENT",
        "Permanent Bridge Total: 1", "Major Culvert Total: 1",
        "Corporate Bridge Register (CBR)");
  }

  @Test
  @DisplayName("prints the Detailed's box: a bridge's own fields, DMS coordinates, abutments")
  void detailedBridgeRow() {
    codes();
    site("62-002", 18L, s -> s.latitude(new BigDecimal("53.917777")).longitude(
        new BigDecimal("122.749999")));
    long id = structure("B1", s -> s.crossingSiteId("62-002").yearBuilt(1998));
    detailedBridge(id, "CON", "TCR", 3);

    assertThat(detailed(c -> c)).singleElement().satisfies(row -> assertThat(row).contains(
        entry("CROSSING_STRUCTURE_NAME", "B1"),
        entry("STRUCTURE_TYPE", "Permanent Bridge"),
        entry("YEAR_BUILT", "1998"),
        entry("POINT_OF_COMMENCEMENT_DISTANCE", "12.50"),
        entry("USER_KM", "1,234.75"),
        entry("TOTAL_LENGTH", "1,024.6"),
        entry("SUPERSTRUCTURE", "Steel girder"),
        entry("ABUTMENTS", "Concrete/Timber crib"),
        entry("SPAN_COUNT", "3"),
        entry("LATITUDE", "53° 55' 04.0\""),
        entry("LONGITUDE", "122° 45' 00.0\""),
        entry("ROW_KEY", "0")));
  }

  @Test
  @DisplayName("leaves a culvert's bridge fields blank, and skips a structure with no type/class")
  void detailedCulvertRow() {
    long id = structure("C1", s -> s.structureTypeClassCode("CUL").yearBuilt(2001));
    culvert(id, "18");
    structure("UNTYPED", s -> s.structureTypeClassCode(null));

    assertThat(detailed(c -> c)).singleElement().satisfies(row -> assertThat(row)
        .contains(entry("STRUCTURE_TYPE", "Major Culvert"), entry("TOTAL_LENGTH", "18.0"))
        .doesNotContainKeys("BRIDGE", "YEAR_BUILT", "SUPERSTRUCTURE", "ABUTMENTS", "SPAN_COUNT"));
  }

  @Test
  @DisplayName("prints both abutments once when they are alike, or the one there is")
  void abutments() {
    assertThat(InspectionsDueRows.abutments("Concrete", "Concrete")).isEqualTo("Concrete");
    assertThat(InspectionsDueRows.abutments(null, "Timber crib")).isEqualTo("Timber crib");
    assertThat(InspectionsDueRows.abutments("Concrete", null)).isEqualTo("Concrete");
  }

  @Test
  @DisplayName("orders the Detailed by management area within the district")
  void detailedOrder() {
    site("62-002", 18L, s -> s.managementOrgUnitNo(2L));
    site("62-003", 18L, s -> s.managementOrgUnitNo(1L));
    structure("AREA2", s -> s.crossingSiteId("62-002"));
    structure("AREA1", s -> s.crossingSiteId("62-003"));

    assertThat(detailed(c -> c)).extracting(row -> row.get("CROSSING_STRUCTURE_NAME"))
        .containsExactly("AREA1", "AREA2");
  }

  @Test
  @DisplayName("produces the portrait Detailed PDF, a box per structure under its district")
  void producesTheDetailedPdf() throws Exception {
    codes();
    long id = structure("B1", s -> s.yearBuilt(1998));
    detailedBridge(id, "CON", "CON", 2);
    comment(id, "Y", LocalDateTime.of(2025, 4, 2, 9, 0), "Gate locked");
    site("62-002", 9L, s -> s);
    structure("C1", s -> s.crossingSiteId("62-002").structureTypeClassCode("CUL"));
    entityManager.flush();
    entityManager.clear();

    ReportFile file = detailedService.generate(
        InspectionsDueCriteria.builder().build(), ReportFormat.PDF);

    assertThat(file.filename()).matches("InspectionsDueDetailedReport_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    PdfReader reader = new PdfReader(file.content());
    assertThat(reader.getPageSizeWithRotation(1).getHeight())
        .isGreaterThan(reader.getPageSizeWithRotation(1).getWidth());
    StringBuilder all = new StringBuilder();
    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
      all.append(new PdfTextExtractor(reader).getTextFromPage(page)).append('\n');
    }
    assertThat(all.toString().replaceAll("\\s+", " ")).contains(
        "Inspections Due - Detailed Report", "CBR003", "Sort: Forest Service Road",
        "Forest District: 100 Mile House", "Forest District: Prince George",
        "Structure #: B1", "Structure #: C1", "Year Superstructure Installed:", "1998",
        "Abutments (Left/Right):", "Concrete", "# Spans:", "Steel girder",
        "IDIR\\PLANNER on 2025-04-02: Gate locked",
        "Permanent Bridge Total: 1", "Major Culvert Total: 1",
        "Corporate Bridge Register (CBR)");
  }

  @Test
  @DisplayName("writes the Summary's CSV: the district, then the PDF's columns, values as the other CSVs")
  void summaryCsv() {
    long id = structure("B1", s -> s);
    bridge(id, "24.56", 2);
    inspection(id, "ROUT", LocalDate.of(2024, 6, 12), "ACC");
    comment(id, "Y", LocalDateTime.of(2025, 4, 2, 9, 0), "Gate locked,\nbring key");
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(InspectionsDueCriteria.builder().build(), ReportFormat.CSV);

    assertThat(file.filename()).matches("InspectionsDueSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.csv");
    assertThat(new String(file.content(), java.nio.charset.StandardCharsets.UTF_8))
        .startsWith("Forest District,Site #,Structure #,Last Inspection Date,");
    assertThat(rows.summaryCsv(InspectionsDueCriteria.builder().build())).containsExactly(List.of(
        "Prince George", "62-001", "B1", "2024-06-12", "2026-07-15", "PB", "R00123", "01",
        "24.56", "2", "Bowron FSR", "12.5", "1234.75", "Deadman Creek", "Boat",
        "Four-wheel drive", "IDIR\\PLANNER on 2025-04-02: Gate locked,\nbring key"));
  }

  @Test
  @DisplayName("writes the Detailed's CSV: decimal coordinates, a culvert's bridge fields blank")
  void detailedCsv() {
    codes();
    site("62-002", 18L, s -> s.latitude(new BigDecimal("53.917777"))
        .longitude(new BigDecimal("122.749999")));
    long bridged = structure("B1", s -> s.crossingSiteId("62-002").yearBuilt(1998));
    detailedBridge(bridged, "CON", "TCR", 3);
    long culverted = structure("C1", s -> s.structureTypeClassCode("CUL").yearBuilt(2001));
    culvert(culverted, "18");
    entityManager.flush();
    entityManager.clear();

    ReportFile file = detailedService.generate(
        InspectionsDueCriteria.builder().build(), ReportFormat.CSV);

    assertThat(file.filename()).matches("InspectionsDueDetailedReport_\\d{4}-\\d{2}-\\d{2}\\.csv");
    assertThat(rows.detailedCsv(InspectionsDueCriteria.builder().build())).containsExactly(
        List.of("Prince George", "B1", "62-002", "1998", "PB", "12.5", "1234.75", "",
            "2026-07-15", "R00123", "01", "Bowron FSR", "Deadman Creek", "1024.56",
            "Four-wheel drive", "Boat", "122.749999", "53.917777", "Steel girder", "3",
            "Concrete/Timber crib", ""),
        List.of("Prince George", "C1", "62-001", "", "CUL", "12.5", "1234.75", "", "2026-07-15",
            "R00123", "01", "Bowron FSR", "Deadman Creek", "18", "Four-wheel drive", "Boat", "",
            "", "", "", "", ""));
  }
}
