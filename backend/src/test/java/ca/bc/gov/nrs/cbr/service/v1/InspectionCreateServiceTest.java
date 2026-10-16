package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionAnswerCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionAnswerOrderEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionAnswerXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionCriteriaEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionSubsectionXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionSubsectionCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionSubsectionTypeXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StrctreInspectionTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest.Item;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Scope;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Subsection;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.OutstandingItem;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Section;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Add Inspection — its questions and its save — against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({InspectionCreateService.class, InspectionCreateValidator.class,
    InspectionFormService.class})
class InspectionCreateServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(5);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(5);
  private static final LocalDate TODAY = LocalDate.now();

  @Autowired
  private InspectionCreateService service;

  @Autowired
  private InspectionFormService forms;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  /** The structure page's read, for whether the structure is complete. */
  @MockitoBean
  private StructureDetailService structureDetails;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\INSPECTOR");
    when(loggedUser.canEdit()).thenReturn(true);
    complete(true);
    givenSiteAndBridge();
    givenQuestions();
    for (String code : List.of("ROUT", "UNP")) {
      entityManager.persist(StrctreInspectionTypeCodeEntity.builder()
          .strctreInspectionTypeCode(code).description(code).effectiveDate(PAST)
          .expiryDate(FUTURE).updateTimestamp(PAST).build());
    }
    for (String code : List.of("PRO", "SUB", "OFL", "RVD")) {
      entityManager.persist(InspectionReportStatusCodeEntity.builder()
          .inspectionReportStatusCode(code).description(code).effectiveDate(PAST)
          .expiryDate(FUTURE).updateTimestamp(PAST).build());
    }
  }

  private void complete(boolean complete) {
    StructureDetailResponse detail = mock(StructureDetailResponse.class);
    when(detail.outstanding()).thenReturn(complete
        ? List.of()
        : List.of(new OutstandingItem(Section.DETAILS, "Structure", "Source")));
    when(structureDetails.findById(7L)).thenReturn(detail);
  }

  private void givenSiteAndBridge() {
    entityManager.persist(CrossingSiteStatusCodeEntity.builder().crossingSiteStatusCode("ACT")
        .description("Active").build());
    entityManager.persist(StructureInspectionStatusCodeEntity.builder()
        .structureInspectionStatusCode("OK").description("Inspected").build());
    entityManager.persist(OrgUnitEntity.builder().orgUnitNo(1L).orgUnitCode("DCC")
        .orgUnitName("Cariboo-Chilcotin").build());
    entityManager.persist(CbrOrgUnitEntity.builder().orgUnitNo(1L).orgUnitCode("DCC")
        .orgUnitName("Cariboo-Chilcotin District").build());
    entityManager.persist(CrossingSiteEntity.builder().crossingSiteId("62-001").capitalRoadInd("N")
        .crossingSiteTypeCode("XNG").crossingSiteStatusCode("ACT")
        .structureInspectionStatusCode("OK").orgUnitNo(1L).crossingName("Riske Cr")
        .pointOfCommencementDistance(new BigDecimal("0.24")).build());
    entityManager.persist(CrossingStructureEntity.builder().crossingStructureId(7L)
        .crossingStructureName("B7").crossingSiteId("62-001").structureTypeClassCode("PB")
        .activeInd("Y").closeProximityInd("N").portableStructureInd("N")
        .asBuiltInfoPresentInd("N").build());
    entityManager.persist(ForestServiceBridgeEntity.builder().forestServiceBridgeId(70L)
        .crossingStructureId(7L).needleBeamInd("N").superstructureTypeCode("STL")
        .deckTypeCode("TIM").build());
    for (long number : List.of(1L, 2L)) {
      entityManager.persist(ForestServiceBridgeSpanEntity.builder().forestServiceBridgeId(70L)
          .spanNumber((int) number).spanLength(new BigDecimal("10")).build());
    }
    entityManager.persist(ForestServiceBridgePierEntity.builder().forestServiceBridgeId(70L)
        .pierNumber(1L).pierTypeCode("CRIB").build());
  }

  /** Groups APP, DECK, SS, STR, PIER for a Durable Bridge, with questions and answers. */
  private void givenQuestions() {
    int year = 2000;
    for (String group : List.of("APP", "DECK", "SS", "STR", "PIER")) {
      entityManager.persist(InspectionSubsectionCodeEntity.builder().subsectionCode(group)
          .description(group + " group").effectiveDate(LocalDateTime.of(year++, 1, 1, 0, 0))
          .expiryDate(FUTURE).build());
      entityManager.persist(InspectionSubsectionTypeXrefEntity.builder().subsectionCode(group)
          .typeClassCode("PB").build());
    }
    question("APPQ", "APP", 1, PAST, FUTURE);
    question("EXPQ", "APP", 2, PAST, PAST.plusYears(1));
    question("TTQ", "APP", 3, PAST, FUTURE);
    question("STLQ", "APP", 4, PAST, FUTURE);
    question("DECKQ", "DECK", 1, PAST, FUTURE);
    question("SSQ", "SS", 1, PAST, FUTURE);
    question("PEEL", "STR", 1, PAST, FUTURE);
    question("PIERQ", "PIER", 1, PAST, FUTURE);
    criteria(1L, "TTQ", "TT");
    criteria(2L, "STLQ", "STL");
    int order = 1;
    for (String answer : List.of("E", "G", "P", "UNR")) {
      entityManager.persist(InspectionAnswerCodeEntity.builder().answerCode(answer)
          .description(answer + " answer").effectiveDate(PAST).expiryDate(FUTURE).build());
      entityManager.persist(InspectionAnswerOrderEntity.builder().answerCode(answer)
          .orderBy(order++).build());
    }
    // Listed out of order, to show the answer order is the table's.
    for (String answer : List.of("UNR", "G", "E", "P")) {
      entityManager.persist(InspectionQuestionAnswerXrefEntity.builder().questionCode("APPQ")
          .answerCode(answer).build());
      entityManager.persist(InspectionQuestionAnswerXrefEntity.builder().questionCode("DECKQ")
          .answerCode(answer).build());
    }
  }

  private void question(String code, String group, int order, LocalDateTime from,
      LocalDateTime to) {
    entityManager.persist(InspectionQuestionCodeEntity.builder().questionCode(code)
        .description(code + " question").effectiveDate(from).expiryDate(to).build());
    entityManager.persist(InspectionQuestionSubsectionXrefEntity.builder().questionCode(code)
        .subsectionCode(group).orderBy(order).build());
  }

  private void criteria(long id, String question, String superstructure) {
    entityManager.persist(InspectionQuestionCriteriaEntity.builder().criteriaId(id)
        .questionCode(question).superstructureTypeCode(superstructure).build());
  }

  private InspectionFormResponse form() {
    entityManager.flush();
    return forms.form(7L);
  }

  private static InspectionCreateRequest request(String type, LocalDate date, List<Item> items) {
    return new InspectionCreateRequest(7L, type, "PRO", date, " Jo Smith ", null, 1200L, false,
        null, new BigDecimal("1.5"), null, null, true, false, false, false, false, null, items);
  }

  private static InspectionCreateRequest bare(String status, LocalDate date, String inspector,
      String company) {
    return new InspectionCreateRequest(7L, "UNP", status, date, inspector, company, null, null,
        null, null, null, null, null, null, null, null, null, null, List.of());
  }

  private Map<String, String> refused(InspectionCreateRequest request) {
    entityManager.flush();
    try {
      service.create(request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("the inspection was added");
  }

  private <T> List<T> all(Class<T> type) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.createQuery("FROM " + type.getSimpleName(), type).getResultList();
  }

  @Test
  @DisplayName("asks a bridge's groups and questions as legacy picks them")
  void picksQuestions() {
    InspectionFormResponse form = form();

    // Superstructure, not Stringers, for a steel superstructure; Piers because it has one.
    assertThat(form.subsections()).extracting(Subsection::code)
        .containsExactly("APP", "DECK", "SS", "PIER");
    Subsection approaches = form.subsections().get(0);
    // Not the expired question, nor the one whose criteria name another superstructure.
    assertThat(approaches.questions()).extracting("code").containsExactly("APPQ", "STLQ");
    assertThat(approaches.questions().get(0).answers()).extracting("code")
        .containsExactly("E", "G", "P", "UNR");
    assertThat(approaches.questions().get(1).answers()).isEmpty();
    assertThat(form.subsections()).extracting(Subsection::scope)
        .containsExactly(Scope.STRUCTURE, Scope.SPAN, Scope.SPAN, Scope.PIER);
    assertThat(form.spans()).extracting("number").containsExactly(1L, 2L);
    assertThat(form.piers()).extracting("number").containsExactly(1L);
  }

  @Test
  @DisplayName("adds the inspection In Progress, with its answers, at the structure's site")
  void addsInspection() {
    String spanId = form().spans().get(0).id();
    String id = service.create(request("ROUT", TODAY.minusDays(1), List.of(
        new Item("APP", "APPQ", null, null, null, "G", " Fine "),
        new Item("DECK", "DECKQ", Long.valueOf(spanId), null, null, "P", "Rotten plank"),
        new Item("APP", "STLQ", null, null, null, "", "")))).id();

    StructureInspectionEntity inspection = all(StructureInspectionEntity.class).get(0);
    assertThat(inspection.getInspectionId()).isEqualTo(Long.parseLong(id));
    assertThat(inspection.getSiteAtTimeOfInspection()).isEqualTo("62-001");
    assertThat(inspection.getStrctreInspectionTypeCode()).isEqualTo("ROUT");
    assertThat(inspection.getInspectorName()).isEqualTo("Jo Smith");
    assertThat(inspection.getHighWaterHazardInd()).isEqualTo("Y");
    assertThat(inspection.getPengInspectionReqInd()).isEqualTo("N");
    assertThat(inspection.getEntryUserid()).isEqualTo("IDIR\\INSPECTOR");
    assertThat(all(InspectionReportStatusEntity.class)).singleElement()
        .extracting(InspectionReportStatusEntity::getInspectionReportStatusCode).isEqualTo("PRO");
    // The blank one is not a row: the answer column is required.
    assertThat(all(StructureInspectionItemEntity.class))
        .extracting(StructureInspectionItemEntity::getQuestionCode,
            StructureInspectionItemEntity::getAnswerValue,
            StructureInspectionItemEntity::getItemComment)
        .containsExactlyInAnyOrder(
            tuple("APPQ", "G", "Fine"),
            tuple("DECKQ", "P", "Rotten plank"));
  }

  @Test
  @DisplayName("may wait for the date, inspector and company while In Progress")
  void datesOptional() {
    service.create(bare("PRO", null, null, null));

    assertThat(all(StructureInspectionEntity.class)).singleElement()
        .extracting(StructureInspectionEntity::getInspectionDate).isNull();
  }

  @Test
  @DisplayName("may be added Submitted, when the date, inspector and company are required")
  void submitted() {
    assertThat(refused(bare("SUB", null, " ", null))).containsOnly(
        Map.entry("inspectionDate", "Date of Inspection is required."),
        Map.entry("inspectorName", "Inspector Name is required."),
        Map.entry("inspectorCompanyName", "Inspection Company Name is required."));

    service.create(bare("sub", TODAY, "Jo Smith", "Bridges Ltd"));

    assertThat(all(InspectionReportStatusEntity.class)).singleElement()
        .extracting(InspectionReportStatusEntity::getInspectionReportStatusCode).isEqualTo("SUB");
  }

  @Test
  @DisplayName("leaves Reviewed and Rejected to the saved inspection, and needs a status")
  void notReviewedOnAdd() {
    String later = "An inspection is added In Progress or Submitted; it is reviewed once saved.";
    assertThat(refused(bare("RVD", TODAY, "Jo", "Co"))).containsOnly(Map.entry("statusCode", later));
    assertThat(refused(bare("REJ", TODAY, "Jo", "Co"))).containsOnly(Map.entry("statusCode", later));
    assertThat(refused(bare(" ", null, null, null))).containsOnly(
        Map.entry("statusCode", "Inspection Report Status is required."));
  }

  @Test
  @DisplayName("carries the structure's outstanding repairs forward, as legacy")
  void carriesRepairsForward() {
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(50L)
        .crossingStructureId(7L).inspectionDate(TODAY.minusYears(2)).build());
    repair(1L, "REQ", "N");
    repair(2L, "COM", "N");
    repair(3L, "SUG", "Y");

    String id = service.create(request("UNP", TODAY, List.of())).id();

    List<StructureRepairEntity> repairs = all(StructureRepairEntity.class);
    assertThat(repairs).filteredOn(repair -> Long.parseLong(id) == repair.getInspectionId())
        .singleElement()
        .satisfies(copy -> {
          assertThat(copy.getRepairNumber()).isEqualTo(4L);
          assertThat(copy.getRepairStatusCode()).isEqualTo("CF");
          assertThat(copy.getCarriedForwardInd()).isEqualTo("N");
          assertThat(copy.getDescription()).isEqualTo("Repair 1");
        });
    assertThat(repairs).filteredOn(repair -> repair.getRepairNumber() == 1L).singleElement()
        .extracting(StructureRepairEntity::getCarriedForwardInd).isEqualTo("Y");
  }

  private void repair(long number, String status, String carried) {
    entityManager.persist(StructureRepairEntity.builder().inspectionId(50L)
        .crossingStructureId(7L).repairNumber(number).repairStatusCode(status)
        .description("Repair " + number).carriedForwardInd(carried).build());
  }

  @Test
  @DisplayName("keeps legacy's answer rules: Poor needs a comment, a comment needs an answer")
  void answerRules() {
    String spanId = form().spans().get(0).id();
    Map<String, String> errors = refused(request("UNP", TODAY, List.of(
        new Item("APP", "APPQ", null, null, null, "P", null),
        new Item("APP", "STLQ", null, null, null, null, "Seen"),
        new Item("APP", "EXPQ", null, null, null, "G", null),
        new Item("DECK", "DECKQ", null, null, null, "G", null),
        new Item("DECK", "DECKQ", Long.valueOf(spanId), null, null, "X", null))));

    assertThat(errors).containsOnly(
        Map.entry("items.APP.APPQ", "(APP group) APPQ question requires a comment."),
        Map.entry("items.APP.STLQ", "(APP group) STLQ question requires an answer."),
        Map.entry("items.APP.EXPQ", "This question is not asked of this structure."),
        Map.entry("items.DECK.DECKQ", "Choose one of the bridge's spans for this question."),
        Map.entry("items.DECK.DECKQ.span-" + spanId,
            "(DECK group) DECKQ question is not one of the listed answers."));
  }

  @Test
  @DisplayName("allows one inspection a day, offline ones aside, and none in the future")
  void dateRules() {
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(50L)
        .crossingStructureId(7L).inspectionDate(TODAY.minusDays(3)).build());
    entityManager.persist(InspectionReportStatusEntity.builder().inspectionReportStatusId(500L)
        .inspectionId(50L).inspectionReportStatusCode("PRO").build());
    entityManager.persist(StructureInspectionEntity.builder().inspectionId(51L)
        .crossingStructureId(7L).inspectionDate(TODAY.minusDays(4)).build());
    entityManager.persist(InspectionReportStatusEntity.builder().inspectionReportStatusId(510L)
        .inspectionId(51L).inspectionReportStatusCode("OFL").build());

    assertThat(refused(request("UNP", TODAY.minusDays(3), List.of()))).containsOnly(
        Map.entry("inspectionDate",
            "Inspection with this date already exists. Please choose another Inspection date."));
    assertThat(refused(request("UNP", TODAY.plusDays(1), List.of()))).containsOnly(
        Map.entry("inspectionDate", "Date of Inspection must not be after today."));
    service.create(request("UNP", TODAY.minusDays(4), List.of()));
  }

  @Test
  @DisplayName("leaves Routine to Level 1, and refuses an incomplete structure")
  void whoAndWhich() {
    when(loggedUser.canEdit()).thenReturn(false);
    assertThat(refused(request("ROUT", TODAY, List.of()))).containsOnly(
        Map.entry("typeCode", "Only Level 1 and up can add a Routine inspection."));

    complete(false);
    assertThat(refused(request("UNP", TODAY, List.of()))).containsOnly(Map.entry("structureId",
        "Structure B7 is incomplete. Complete it on its structure page before it can be inspected."));
  }
}
