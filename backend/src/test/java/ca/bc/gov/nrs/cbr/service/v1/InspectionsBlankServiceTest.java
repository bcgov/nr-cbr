package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.AbutmentCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.DeckTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.SuperstructureTypeCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Part;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Question;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Scope;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Subsection;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Inspections – Blank Report, against the database: each structure's form as the lines its
 * template prints, in order, and the PDF made from them. The questions are the inspection form's,
 * stood in for here so each case sets its own.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({InspectionsBlankService.class, InspectionsBlankRows.class, RepairsSummaryNames.class,
    InspectionsDueCriteriaLine.class, StructureInventoryReportSupport.class, ReportRenderer.class})
class InspectionsBlankServiceTest {

  private static final List<CodeOptionResponse> RATINGS = List.of(
      new CodeOptionResponse("E", "Excellent"), new CodeOptionResponse("G", "Good"),
      new CodeOptionResponse("F", "Fair"), new CodeOptionResponse("P", "Poor"),
      new CodeOptionResponse("NA", "Not applicable"), new CodeOptionResponse("UNR", "Unrated"));

  @Autowired
  private InspectionsBlankService service;

  @Autowired
  private InspectionsBlankRows rows;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @MockitoBean
  private ConfigurationService codes;

  @MockitoBean
  private InspectionFormService forms;

  /** Each structure's form, as the stood-in form service answers; none set is an empty form. */
  private final Map<Long, InspectionFormResponse> formsById = new java.util.HashMap<>();

  private long nextStructure = 1;
  private long nextBridge = 500;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\REPORTER");
    when(forms.forms(anyList())).thenAnswer(invocation -> {
      Map<Long, InspectionFormResponse> answered = new java.util.HashMap<>();
      for (CrossingStructureEntity structure
          : invocation.<List<CrossingStructureEntity>>getArgument(0)) {
        Long id = structure.getCrossingStructureId();
        answered.put(id, formsById.getOrDefault(id, new InspectionFormResponse(
            String.valueOf(id), List.of(), List.of(), List.of())));
      }
      return answered;
    });
    when(codes.getMonitoringStatusCodes()).thenReturn(
        List.of(new CodeOptionResponse("SUG", "Suggested")));
    for (String entity : List.of("StructureMonitorItemEntity", "StructureRepairEntity",
        "ForestServiceBridgeEntity", "CrossingStructureEntity", "CrossingSiteEntity",
        "CbrRoadSectionEntity", "AbutmentCodeEntity", "DeckTypeCodeEntity",
        "SuperstructureTypeCodeEntity", "RepairStatusCodeEntity", "RepairPriorityCodeEntity",
        "MonitoringStatusCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    LocalDateTime past = LocalDateTime.now().minusYears(10);
    LocalDateTime future = LocalDateTime.now().plusYears(10);
    entityManager.persist(RepairStatusCodeEntity.builder().repairStatusCode("REQ")
        .description("Required").effectiveDate(past).expiryDate(future).build());
    entityManager.persist(RepairPriorityCodeEntity.builder().repairPriorityCode("H")
        .description("High").effectiveDate(past).expiryDate(future).build());
    entityManager.persist(MonitoringStatusCodeEntity.builder().monitoringStatusCode("SUG")
        .description("Suggested").build());
    entityManager.persist(AbutmentCodeEntity.builder().abutmentCode("CON")
        .description("Concrete").build());
    entityManager.persist(DeckTypeCodeEntity.builder().deckTypeCode("TMB")
        .description("Timber").build());
    for (String[] code : List.of(new String[] {"STL", "Steel girder"},
        new String[] {"LGST", "Log stringer"})) {
      entityManager.persist(SuperstructureTypeCodeEntity.builder().superstructureTypeCode(code[0])
          .description(code[1]).build());
    }
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId("62-001").crossingName("Deadman Creek")
        .userKm(new BigDecimal("12.75")).forestFileId("R00123").roadSectionId("01")
        .latitude(new BigDecimal("53.917777")).longitude(new BigDecimal("122.749999"))
        .utmZone(10).utmEasting(512345L).utmNorthing(5987654L).build());
  }

  private long structure(
      String name, UnaryOperator<CrossingStructureEntity.CrossingStructureEntityBuilder> with) {
    long id = nextStructure++;
    entityManager.persist(with.apply(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName(name).crossingSiteId("62-001")
        .structureTypeClassCode("PB").activeInd("Y").closeProximityInd("N")
        .portableStructureInd("N").currentLoadRating(new BigDecimal("45.4"))).build());
    return id;
  }

  private void bridge(long structureId, String superstructure) {
    entityManager.persist(ForestServiceBridgeEntity.builder().forestServiceBridgeId(nextBridge++)
        .crossingStructureId(structureId).superstructureTypeCode(superstructure)
        .deckTypeCode("TMB").leftAbutmentCode("CON").rightAbutmentCode("CON").build());
  }

  private void form(long structureId, InspectionFormResponse form) {
    formsById.put(structureId, form);
  }

  private static Subsection group(String code, String name, Scope scope, Question... questions) {
    return new Subsection(code, name, scope, List.of(questions));
  }

  private static Question question(String code, String text) {
    return new Question(code, text, false, RATINGS);
  }

  private static List<Part> parts(int count) {
    return LongStream.rangeClosed(1, count).mapToObj(n -> new Part(String.valueOf(n), n)).toList();
  }

  /** A bridge's form: approaches and abutments once, deck and superstructure per span, piers. */
  private static InspectionFormResponse bridgeForm(String id, int spans, int piers) {
    return new InspectionFormResponse(id, List.of(
        group("APP", "Approaches", Scope.STRUCTURE, question("COND", "Condition")),
        group("ABUT", "Abutments", Scope.STRUCTURE, question("CRK", "Cracking")),
        group("DECK", "Deck", Scope.SPAN, question("DML", "Deck margin left")),
        group("SS", "Superstructure", Scope.SPAN, question("RUST", "Rust")),
        group("PIER", "Piers", Scope.PIER, question("SCR", "Scour"))),
        parts(spans), parts(piers));
  }

  private List<Map<String, String>> lines() {
    entityManager.flush();
    entityManager.clear();
    return rows.rows(InspectionsDueCriteria.builder().build());
  }

  private static List<String> outline(List<Map<String, String>> lines) {
    return lines.stream().map(line -> line.get("KIND")
        + (line.get("TEXT") == null ? "" : " " + line.get("TEXT"))).toList();
  }

  @Test
  @DisplayName("prints a bridge's form in legacy's order, each pier after the span before it")
  void bridgeOrder() {
    long id = structure("B1", s -> s);
    bridge(id, "STL");
    form(id, bridgeForm(String.valueOf(id), 3, 2));

    List<String> outline = outline(lines());

    assertThat(outline).containsExactly(
        "GENERAL",
        "SECTION LEFT/RIGHT APPROACHES (as seen facing downstream)", "COLUMNS", "QUESTION",
        "SECTION LEFT/RIGHT ABUTMENTS", "DETAIL LEFT: CON - Concrete",
        "DETAIL RIGHT: CON - Concrete", "COLUMNS", "QUESTION",
        "PART Span 1 of 3", "SECTION DECK", "DETAIL TMB - Timber", "COLUMNS", "QUESTION",
        "SECTION SUPERSTRUCTURE", "DETAIL STL - Steel girder", "COLUMNS", "QUESTION",
        "PART Pier 1 of 2", "SECTION PIER", "COLUMNS", "QUESTION",
        "PART Span 2 of 3", "SECTION DECK", "DETAIL TMB - Timber", "COLUMNS", "QUESTION",
        "SECTION SUPERSTRUCTURE", "DETAIL STL - Steel girder", "COLUMNS", "QUESTION",
        "PART Pier 2 of 2", "SECTION PIER", "COLUMNS", "QUESTION",
        "PART Span 3 of 3", "SECTION DECK", "DETAIL TMB - Timber", "COLUMNS", "QUESTION",
        "SECTION SUPERSTRUCTURE", "DETAIL STL - Steel girder", "COLUMNS", "QUESTION",
        "CHANNEL", "HAZARDS", "REPAIRS", "REPAIR_BLANK", "MONITORS", "MONITOR_BLANK",
        "PHOTO_LOG", "INSPECTOR", "PENG");
  }

  @Test
  @DisplayName("heads every line with its structure, and fills the general information")
  void generalInformation() {
    long id = structure("B1", s -> s);
    bridge(id, "STL");
    form(id, bridgeForm(String.valueOf(id), 1, 0));

    List<Map<String, String>> lines = lines();

    assertThat(lines).allSatisfy(line -> assertThat(line).contains(
        entry("STRUCTURE_KEY", String.valueOf(id)), entry("CROSSING_STRUCTURE_NAME", "B1"),
        entry("CROSSING_SITE_ID", "62-001"),
        entry("TITLE1", "Routine Condition Bridge Inspection Form"),
        entry("TITLE2", "(except for log stringer or timber stringer bridges)")));
    assertThat(lines.getFirst()).contains(
        entry("ROAD_SECT_NAME", "Bowron FSR"), entry("USER_KM", "12.75"),
        entry("FOREST_FILE_ID", "R00123"), entry("ROAD_SECTION_ID", "01"),
        entry("LATITUDE", "53° 55' 04.0\""), entry("LONGITUDE", "122° 45' 00.0\""),
        entry("UTM_ZONE", "10"), entry("UTM_EASTING", "512345"),
        entry("UTM_NORTHING", "5987654"));
    assertThat(lines.getLast()).contains(entry("KIND", "PENG"), entry("LOAD_RATING", "45"));
  }

  @Test
  @DisplayName("prints every answer to circle, Unrated left out, and notes the approach questions")
  void questions() {
    long id = structure("B1", s -> s);
    bridge(id, "STL");
    form(id, bridgeForm(String.valueOf(id), 1, 0));

    List<Map<String, String>> asked = lines().stream()
        .filter(line -> "QUESTION".equals(line.get("KIND"))).toList();

    assertThat(asked).first().satisfies(line -> assertThat(line).contains(
        entry("QUESTION", "Condition"), entry("CHOICES", "E   G   F   P   N/A"))
        .doesNotContainKey("APPROACH"));
    assertThat(asked).filteredOn(line -> "Deck margin left".equals(line.get("QUESTION")))
        .singleElement().satisfies(line -> assertThat(line).containsEntry("APPROACH", "Y"));
    assertThat(InspectionsBlankRows.choices(List.of(new CodeOptionResponse("ZERO", "0"),
        new CodeOptionResponse("ONE", "1"), new CodeOptionResponse("THREE", "3"))))
        .isEqualTo("0   1   3");
  }

  @Test
  @DisplayName("asks a stringer's own questions only in the stringer table")
  void stringers() {
    long id = structure("B1", s -> s);
    bridge(id, "LGST");
    form(id, new InspectionFormResponse(String.valueOf(id), List.of(
        group("STR", "Stringers", Scope.SPAN, question("SPL", "Splitting"),
            new Question("PEEL", "Peeled", true, List.of()))), parts(1), List.of()));

    List<Map<String, String>> lines = lines();

    assertThat(outline(lines)).containsSubsequence(
        "SECTION SUPERSTRUCTURE", "DETAIL LGST - Log stringer", "COLUMNS", "QUESTION",
        "STRINGERS", "CHANNEL");
    assertThat(lines).filteredOn(line -> "QUESTION".equals(line.get("KIND")))
        .extracting(line -> line.get("QUESTION")).containsExactly("Splitting");
    assertThat(lines.getFirst())
        .containsEntry("TITLE2", "(for log stringer or timber stringer bridges)");
  }

  @Test
  @DisplayName("gives a bridge with no spans its span groups once, without a span heading")
  void noSpans() {
    long id = structure("B1", s -> s);
    bridge(id, null);
    form(id, bridgeForm(String.valueOf(id), 0, 0));

    List<String> outline = outline(lines());

    assertThat(outline).noneMatch(line -> line.startsWith("PART"))
        .containsSubsequence("SECTION DECK", "SECTION SUPERSTRUCTURE", "CHANNEL");
  }

  @Test
  @DisplayName("lists the outstanding repairs and monitoring items in number order")
  void repairsAndMonitors() {
    long id = structure("B1", s -> s.structureTypeClassCode("CUL"));
    for (long number : new long[] {2, 1}) {
      entityManager.persist(StructureRepairEntity.builder().crossingStructureId(id)
          .repairNumber(number).repairStatusCode("REQ").repairPriorityCode("H")
          .estimate(1500L).description("Repair " + number).carriedForwardInd("N").build());
    }
    entityManager.persist(StructureMonitorItemEntity.builder().crossingStructureId(id)
        .monitorNumber(1L).monitoringStatusCode("SUG").description("Watch the scour").build());

    List<Map<String, String>> lines = lines();

    assertThat(lines).filteredOn(line -> "REPAIR".equals(line.get("KIND")))
        .extracting(line -> line.get("NUMBER") + " " + line.get("STATUS") + " "
            + line.get("PRIORITY") + " " + line.get("ESTIMATE") + " " + line.get("DESCRIPTION"))
        .containsExactly("1 Required High 1,500.00 Repair 1", "2 Required High 1,500.00 Repair 2");
    assertThat(lines).filteredOn(line -> "MONITOR".equals(line.get("KIND")))
        .singleElement().satisfies(line -> assertThat(line).contains(
            entry("STATUS", "Suggested"), entry("DESCRIPTION", "Watch the scour")));
    assertThat(lines.getFirst())
        .containsEntry("TITLE1", "Routine Condition Culvert Inspection Form")
        .doesNotContainKey("TITLE2");
  }

  @Test
  @DisplayName("produces a form per structure, each starting a page and numbering its own pages")
  void producesThePdf() throws Exception {
    long first = structure("A1", s -> s);
    bridge(first, "STL");
    long second = structure("B2", s -> s);
    bridge(second, "STL");
    form(first, bridgeForm(String.valueOf(first), 2, 1));
    form(second, bridgeForm(String.valueOf(second), 1, 0));
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(InspectionsDueCriteria.builder().build(), ReportFormat.PDF);

    assertThat(file.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(file.filename()).matches("InspectionsBlankReport_\\d{4}-\\d{2}-\\d{2}\\.pdf");
    PdfReader reader = new PdfReader(file.content());
    PdfTextExtractor text = new PdfTextExtractor(reader);
    String firstPage = text.getTextFromPage(1).replaceAll("\\s+", " ");
    assertThat(firstPage).contains("Routine Condition Bridge Inspection Form", "CBR010",
        "GENERAL INFORMATION", "Bowron FSR", "LEFT/RIGHT APPROACHES", "E G F P N/A");
    String last = text.getTextFromPage(reader.getNumberOfPages()).replaceAll("\\s+", " ");
    assertThat(last).contains("B2", "PROFESSIONAL ENGINEER TO COMPLETE THIS SECTION", "45");
    int firstOfSecond = 0;
    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
      String content = text.getTextFromPage(page).replaceAll("\\s+", "");
      if (content.contains("Structure#B2") && firstOfSecond == 0) {
        firstOfSecond = page;
      }
    }
    // The second structure's form starts a page of its own, headed with its own numbers.
    assertThat(firstOfSecond).isGreaterThan(1);
    assertThat(text.getTextFromPage(firstOfSecond).replaceAll("\\s+", ""))
        .contains("GENERALINFORMATION").doesNotContain("Structure#A1");
  }

  @Test
  @DisplayName("writes the CSV as a row per item on each form, in the PDF's order")
  void writesTheCsv() {
    long bridged = structure("B2", s -> s);
    bridge(bridged, "STL");
    form(bridged, bridgeForm(String.valueOf(bridged), 2, 1));
    structure("A1", s -> s.structureTypeClassCode("CUL").currentLoadRating(null));
    entityManager.flush();
    entityManager.clear();

    ReportFile file = service.generate(InspectionsDueCriteria.builder().build(), ReportFormat.CSV);

    assertThat(file.filename()).matches("InspectionsBlankReport_\\d{4}-\\d{2}-\\d{2}\\.csv");
    assertThat(new String(file.content(), java.nio.charset.StandardCharsets.UTF_8))
        .startsWith(String.join(",", InspectionsBlankRows.CSV_COLUMNS.stream()
            .map(heading -> heading.startsWith("#") ? "\"" + heading + "\"" : heading).toList()));
    List<String> site = List.of("62-001");
    List<String> where = List.of("Bowron FSR", "R00123", "01", "12.75", "53.917777",
        "122.749999", "10", "512345", "5987654");
    List<List<String>> csv = rows.csvRows(InspectionsDueCriteria.builder().build());
    // A form with no items still lists its structure.
    assertThat(csv.getFirst()).isEqualTo(row(List.of("A1"), site, List.of("CUL"), where,
        List.of("", "", "", "", "", "")));
    assertThat(csv.subList(1, csv.size()))
        .extracting(row -> row.subList(13, 18))
        .containsExactly(
            List.of("", "LEFT/RIGHT APPROACHES (as seen facing downstream)", "", "Condition",
                "E G F P N/A"),
            List.of("", "LEFT/RIGHT ABUTMENTS", "LEFT: CON - Concrete; RIGHT: CON - Concrete",
                "Cracking", "E G F P N/A"),
            List.of("Span 1 of 2", "DECK", "TMB - Timber",
                "Deck margin left (approaching structure)", "E G F P N/A"),
            List.of("Span 1 of 2", "SUPERSTRUCTURE", "STL - Steel girder", "Rust", "E G F P N/A"),
            List.of("Pier 1 of 1", "PIER", "", "Scour", "E G F P N/A"),
            List.of("Span 2 of 2", "DECK", "TMB - Timber",
                "Deck margin left (approaching structure)", "E G F P N/A"),
            List.of("Span 2 of 2", "SUPERSTRUCTURE", "STL - Steel girder", "Rust", "E G F P N/A"));
    assertThat(csv.get(1).subList(0, 13)).isEqualTo(row(List.of("B2"), site, List.of("PB"), where,
        List.of("45.4")));
  }

  @SafeVarargs
  private static List<String> row(List<String>... parts) {
    return java.util.Arrays.stream(parts).flatMap(List::stream).toList();
  }
}
