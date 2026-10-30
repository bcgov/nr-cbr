package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria.InspectionsConductedCriteriaBuilder;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedSort;
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
 * Inspections Conducted – Summary, against the database: which inspections, in what order, each
 * value as the template prints it, and the PDF made from them.
 *
 * <p>H2 stands in for Oracle, so this proves the predicates, the joins and the formatting, not
 * Oracle's behaviour.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({InspectionsConductedSummaryService.class, InspectionsConductedCriteriaLine.class,
    StructureInventoryReportSupport.class, ReportRenderer.class})
class InspectionsConductedSummaryServiceTest {

  @Autowired
  private InspectionsConductedSummaryService service;

  @Autowired
  private InspectionsConductedCriteriaLine criteriaLine;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @MockitoBean
  private ConfigurationService codes;

  private long nextStructure = 1;
  private long nextInspection = 100;
  private long nextStatus = 1000;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\REPORTER");
    when(codes.getInspectionTypeCodes()).thenReturn(List.of(
        new CodeOptionResponse("ROUT", "Routine"), new CodeOptionResponse("SPEC", "Special")));
    when(codes.getInspectionReportStatusCodes()).thenReturn(List.of(
        new CodeOptionResponse("SUB", "Submitted"), new CodeOptionResponse("ACC", "Accepted")));
    when(codes.getForestDistricts()).thenReturn(
        List.of(new OrgUnitResponse("18", "DPG", "Prince George Natural Resource District")));
    when(codes.getManagementAreas("18")).thenReturn(
        List.of(new OrgUnitResponse("26", "RV", "Robson Valley")));
    when(codes.getSiteTypeCodes()).thenReturn(
        List.of(new CodeOptionResponse("CRS", "Stream crossing")));
    for (String entity : List.of("InspectionReportStatusEntity", "StructureInspectionEntity",
        "ForestServiceBridgeEntity", "CrossingStructureEntity", "CrossingSiteEntity",
        "CbrRoadSectionEntity", "OrgUnitEntity", "InspectionReportStatusCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (String[] code : List.of(new String[] {"SUB", "Submitted"},
        new String[] {"ACC", "Accepted"})) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(code[0]).description(code[1]).build());
    }
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build());
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId("62-001").crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.5")).userKm(new BigDecimal("12.75"))
        .crossingSiteTypeCode("CRS").orgUnitNo(18L).forestFileId("R00123").roadSectionId("01")
        .build());
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

  /** An inspection with these report statuses, oldest first: the last is its current one. */
  private long inspection(
      long structureId, String type, LocalDate date,
      UnaryOperator<StructureInspectionEntity.StructureInspectionEntityBuilder> with,
      String... statuses) {
    long id = nextInspection++;
    entityManager.persist(with.apply(StructureInspectionEntity.builder().inspectionId(id)
        .crossingStructureId(structureId).strctreInspectionTypeCode(type).inspectionDate(date)
        .inspectorName("Jane Smith").pengInspectionReqInd("N")).build());
    for (String status : statuses) {
      entityManager.persist(InspectionReportStatusEntity.builder()
          .inspectionReportStatusId(nextStatus++).inspectionId(id)
          .inspectionReportStatusCode(status).entryUserid("IDIR\\X")
          .entryTimestamp(LocalDateTime.of(2025, 1, 1, 8, 0)).build());
    }
    return id;
  }

  private List<Map<String, String>> rows(UnaryOperator<InspectionsConductedCriteriaBuilder> with) {
    entityManager.flush();
    entityManager.clear();
    return service.rows(with.apply(InspectionsConductedCriteria.builder()).build());
  }

  private List<String> dates(UnaryOperator<InspectionsConductedCriteriaBuilder> with) {
    return rows(with).stream().map(row -> row.get("INSPECTION_DATE")).toList();
  }

  @Test
  @DisplayName("prints an inspection, its structure and its current status as the template does")
  void formatsAnInspection() {
    long id = structure("B1", s -> s);
    inspection(id, "ROUT", LocalDate.of(2025, 6, 12), i -> i, "SUB", "ACC");

    assertThat(rows(c -> c)).singleElement().satisfies(row -> assertThat(row).containsOnly(
        entry("CROSSING_SITE_ID", "62-001"),
        entry("CROSSING_STRUCTURE_NAME", "B1"),
        entry("INSPECTION_TYPE", "Routine"),
        entry("INSPECTION_DATE", "2025/06/12"),
        entry("INSPECTION_STATUS", "Accepted"),
        entry("ROAD_SECT_NAME", "Bowron FSR"),
        entry("POINT_OF_COMMENCEMENT_DISTANCE", "12.50"),
        entry("USER_KM", "12.75"),
        entry("CROSSING_NAME", "Deadman Creek")));
  }

  @Test
  @DisplayName("gives the structure of each inspection, in the report's order, for its KMZ")
  void structureIds() {
    long first = structure("B1", s -> s);
    long second = structure("B2", s -> s);
    inspection(first, "ROUT", LocalDate.of(2025, 6, 12), i -> i, "ACC");
    inspection(second, "ROUT", LocalDate.of(2025, 5, 1), i -> i, "ACC");
    inspection(first, "ROUT", LocalDate.of(2024, 6, 12), i -> i, "ACC");
    Map<String, Long> ids = Map.of("B1", first, "B2", second);

    List<Long> expected = rows(c -> c).stream()
        .map(row -> ids.get(row.get("CROSSING_STRUCTURE_NAME"))).toList();

    assertThat(service.structureIds(InspectionsConductedCriteria.builder().build()))
        .hasSize(3).containsExactlyElementsOf(expected);
  }

  @Test
  @DisplayName("matches the Report Status against the current status only, and needs one")
  void currentStatus() {
    long id = structure("B1", s -> s);
    inspection(id, "ROUT", LocalDate.of(2025, 1, 1), i -> i, "SUB", "ACC");
    inspection(id, "ROUT", LocalDate.of(2024, 1, 1), i -> i, "SUB");
    inspection(id, "ROUT", LocalDate.of(2023, 1, 1), i -> i);

    assertThat(dates(c -> c.inspectionReportStatusCode("SUB"))).containsExactly("2024/01/01");
    assertThat(dates(c -> c.inspectionReportStatusCode("ACC"))).containsExactly("2025/01/01");
    assertThat(dates(c -> c)).containsExactly("2025/01/01", "2024/01/01");
  }

  @Test
  @DisplayName("takes Inspection Date as whole months, either end optional")
  void monthRange() {
    long id = structure("B1", s -> s);
    for (LocalDate date : List.of(LocalDate.of(2024, 12, 31), LocalDate.of(2025, 1, 1),
        LocalDate.of(2025, 3, 31), LocalDate.of(2025, 4, 1))) {
      inspection(id, "ROUT", date, i -> i, "ACC");
    }

    assertThat(dates(c -> c.inspectionDateStart("2025/01").inspectionDateEnd("2025/3")))
        .containsExactly("2025/03/31", "2025/01/01");
    assertThat(dates(c -> c.inspectionDateStart("2025/04"))).containsExactly("2025/04/01");
    assertThat(dates(c -> c.inspectionDateEnd("2024/12"))).containsExactly("2024/12/31");
  }

  @Test
  @DisplayName("filters by inspector, professional inspection, close proximity and the site")
  void criteria() {
    long close = structure("B1", s -> s.closeProximityInd("Y"));
    inspection(close, "ROUT", LocalDate.of(2025, 1, 1), i -> i.pengInspectionReqInd("Y"), "ACC");
    long other = structure("B2", s -> s);
    inspection(other, "SPEC", LocalDate.of(2024, 1, 1), i -> i.inspectorName("Bob Jones"), "ACC");

    assertThat(dates(c -> c.inspectorName("smith"))).containsExactly("2025/01/01");
    assertThat(dates(c -> c.professionalInspection(true))).containsExactly("2025/01/01");
    assertThat(dates(c -> c.closeProximity(true))).containsExactly("2025/01/01");
    assertThat(dates(c -> c.inspectionTypeCode("SPEC"))).containsExactly("2024/01/01");
    assertThat(dates(c -> c.orgUnit("18").siteTypeCode("CRS").forestFileId("r001")))
        .hasSize(2);
  }

  @Test
  @DisplayName("leaves out archived structures and portable superstructures in storage")
  void exclusions() {
    long id = structure("B1", s -> s);
    inspection(id, "ROUT", LocalDate.of(2025, 1, 1), i -> i, "ACC");
    long archived = structure("B2", s -> s.activeInd("N"));
    inspection(archived, "ROUT", LocalDate.of(2024, 1, 1), i -> i, "ACC");
    long portable = structure("B3", s -> s);
    entityManager.persist(ForestServiceBridgeEntity.builder().forestServiceBridgeId(30L)
        .crossingStructureId(portable).portableSuperstructureStatusCode("S").build());
    inspection(portable, "ROUT", LocalDate.of(2023, 1, 1), i -> i, "ACC");

    assertThat(dates(c -> c)).containsExactly("2025/01/01");
  }

  @Test
  @DisplayName("keeps only each structure's latest routine inspection when asked")
  void mostRecentInspections() {
    long id = structure("B1", s -> s);
    inspection(id, "ROUT", LocalDate.of(2023, 1, 1), i -> i, "ACC");
    inspection(id, "ROUT", LocalDate.of(2025, 1, 1), i -> i, "ACC");
    inspection(id, "SPEC", LocalDate.of(2025, 6, 1), i -> i, "ACC");

    assertThat(dates(c -> c.mostRecentInspections(true))).containsExactly("2025/01/01");
  }

  @Test
  @DisplayName("sorts in legacy's direction for the column, then by Structure # and newest first")
  void order() {
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("A00001").roadSectionId("01").roadSectName("Zeballos FSR").build());
    entityManager.persist(CrossingSiteEntity.builder().crossingSiteId("10-001")
        .forestFileId("A00001").roadSectionId("01").userKm(BigDecimal.ONE).build());
    long b = structure("B", s -> s);
    long a = structure("A", s -> s.crossingSiteId("10-001"));
    inspection(b, "ROUT", LocalDate.of(2023, 1, 1), i -> i, "ACC");
    inspection(b, "ROUT", LocalDate.of(2025, 1, 1), i -> i, "ACC");
    inspection(a, "ROUT", LocalDate.of(2024, 1, 1), i -> i, "ACC");

    // Forest Service Road is descending, as legacy's is: Zeballos before Bowron.
    assertThat(dates(c -> c)).containsExactly("2024/01/01", "2025/01/01", "2023/01/01");
    assertThat(dates(c -> c.sortBy(InspectionsConductedSort.FOREST_FILE_ID)))
        .containsExactly("2025/01/01", "2023/01/01", "2024/01/01");
    assertThat(dates(c -> c.sortBy(InspectionsConductedSort.USER_KM)))
        .containsExactly("2024/01/01", "2025/01/01", "2023/01/01");
    assertThat(dates(c -> c.sortBy(InspectionsConductedSort.INSPECTION_DATE)))
        .containsExactly("2025/01/01", "2024/01/01", "2023/01/01");
  }

  @Test
  @DisplayName("writes legacy's criteria line, codes and areas by name")
  void criteriaLine() {
    assertThat(criteriaLine.of(InspectionsConductedCriteria.builder()
        .orgUnit("18").managementOrgUnit("26").inspectionReportStatusCode("ACC")
        .inspectionDateStart("2025/01").siteTypeCode("CRS").mostRecentInspections(true).build(),
        InspectionsConductedSort.FOREST_SERVICE_ROAD))
        .isEqualTo("Forest District: Prince George Natural Resource District"
            + " Inspection Report Status: Accepted Inspection Date: >= 2025/01"
            + " Management Area: Robson Valley Most Recent Inspections Only: Yes"
            + " Site Type: Stream crossing Sort: Forest Service Road");
  }

  @Test
  @DisplayName("produces the landscape PDF, a long road name wrapped rather than cut off")
  void producesThePdf() throws Exception {
    entityManager.persist(CbrRoadSectionEntity.builder().forestFileId("R00999").roadSectionId("01")
        .roadSectName("Upper Fraser Mainline North Rd")
        .build());
    entityManager.persist(CrossingSiteEntity.builder().crossingSiteId("62-009")
        .forestFileId("R00999").roadSectionId("01").build());
    long id = structure("B1", s -> s.crossingSiteId("62-009"));
    inspection(id, "ROUT", LocalDate.of(2025, 6, 12), i -> i, "ACC");
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(InspectionsConductedCriteria.builder().build(),
        ReportFormat.PDF);

    assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(file.filename())
        .matches("InspectionsConductedSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    PdfReader reader = new PdfReader(file.content());
    assertThat(reader.getPageSizeWithRotation(1).getWidth())
        .isGreaterThan(reader.getPageSizeWithRotation(1).getHeight());
    assertThat(new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\\s+", " ")).contains(
        "BC Ministry of Forests, Lands, and Natural Resource Operations",
        "Inspections Conducted Report", "IDIR\\REPORTER", "CBR002", "Sort: Forest Service Road",
        "Routine", "2025/06/12", "Accepted", "North Rd",
        "Corporate Bridge Register (CBR)", "Disclaimer:");
  }

  @Test
  @DisplayName("writes the PDF's nine columns as a CSV, values as the other CSVs write them")
  void writesTheCsv() {
    long id = structure("B1", s -> s);
    inspection(id, "ROUT", LocalDate.of(2025, 6, 12), i -> i, "ACC");
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(InspectionsConductedCriteria.builder().build(),
        ReportFormat.CSV);

    assertThat(file.filename())
        .matches("InspectionsConductedSummaryReport_\\d{4}-\\d{2}-\\d{2}\\.csv");
    assertThat(new String(file.content(), StandardCharsets.UTF_8))
        .startsWith("Site #,Structure #,Inspection Type,Inspection Date,Inspection Status,"
            + "Road Name,KM,User KM,Crossing Name\r\n");
    assertThat(service.csvRows(InspectionsConductedCriteria.builder().build())).containsExactly(
        List.of("62-001", "B1", "Routine", "2025-06-12", "Accepted", "Bowron FSR", "12.5",
            "12.75", "Deadman Creek"));
  }
}
