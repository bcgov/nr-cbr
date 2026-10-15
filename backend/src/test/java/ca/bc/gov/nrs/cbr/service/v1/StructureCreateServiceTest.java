package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.model.v1.AbutmentCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.DeckTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredClvrtMaterialCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredCulvertTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.LoadPostingSignWrrntdCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.PierTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RunningSurfaceCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCurbTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureLoadRatingEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureSourceCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.SuperstructureTypeCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.BridgeDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.CulvertDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionScheduleRequest;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.PierRequest;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.SpanRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailsRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Add Structure, against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({StructureCreateService.class, StructureCreateValidator.class,
    StructureInformationService.class, StructureDetailsService.class,
    StructureBridgeService.class, StructureCulvertService.class,
    StructureLoadRatingsService.class, LoadRatingService.class})
class StructureCreateServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(1);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(1);
  private static final LocalDate NEXT_YEAR = LocalDate.now().plusYears(1);

  @Autowired
  private StructureCreateService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @MockitoBean
  private StructureDetailService structureDetail;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\ADDER");
    when(loggedUser.canDestroy()).thenReturn(true);
    // The site's codes and district — the test schema keeps the foreign keys.
    for (String status : List.of("ACT", "PP", "DAC")) {
      entityManager.persist(CrossingSiteStatusCodeEntity.builder()
          .crossingSiteStatusCode(status).description(status).build());
    }
    entityManager.persist(StructureInspectionStatusCodeEntity.builder()
        .structureInspectionStatusCode("OK").description("Inspected").build());
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(1L).orgUnitCode("DCC").orgUnitName("Cariboo-Chilcotin").build());
    entityManager.persist(CbrOrgUnitEntity.builder()
        .orgUnitNo(1L).orgUnitCode("DCC").orgUnitName("Cariboo-Chilcotin District").build());
    givenSite("62-145", site -> { });
    givenSite("62-PP", site -> site.crossingSiteStatusCode("PP"));
    givenSite("62-DAC", site -> site.crossingSiteStatusCode("DAC"));
    givenSite("62-INC", site -> site.crossingName(null));
    entityManager.persist(StructureSourceCodeEntity.builder().structureSourceCode("MOF")
        .description("MOF").effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(LoadPostingSignWrrntdCodeEntity.builder()
        .loadPostingSignWrrntdCode("UNK").description("Unknown").effectiveDate(PAST)
        .expiryDate(FUTURE).build());
    entityManager.persist(SuperstructureTypeCodeEntity.builder().superstructureTypeCode("STL")
        .description("Steel").build());
    entityManager.persist(DeckTypeCodeEntity.builder().deckTypeCode("TIM").description("Timber")
        .build());
    entityManager.persist(RunningSurfaceCodeEntity.builder().runningSurfaceCode("GRV")
        .description("Gravel").build());
    entityManager.persist(StructureCurbTypeCodeEntity.builder().structureCurbTypeCode("TMB")
        .description("Timber").build());
    entityManager.persist(AbutmentCodeEntity.builder().abutmentCode("CON").description("Concrete")
        .build());
    entityManager.persist(PierTypeCodeEntity.builder().pierTypeCode("CRIB")
        .description("Timber crib").effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(EngineeredCulvertTypeCodeEntity.builder()
        .engineeredCulvertTypeCode("RND").description("Round").effectiveDate(PAST)
        .expiryDate(FUTURE).build());
    entityManager.persist(EngineeredClvrtMaterialCodeEntity.builder()
        .engineeredClvrtMaterialCode("CSP").description("CSP").effectiveDate(PAST)
        .expiryDate(FUTURE).build());
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(500L).crossingStructureName("B500").crossingSiteId("62-145")
        .structureTypeClassCode("PB").activeInd("N").closeProximityInd("N")
        .portableStructureInd("N").asBuiltInfoPresentInd("N").build());
  }

  private void givenSite(String id, Consumer<CrossingSiteEntity.CrossingSiteEntityBuilder> change) {
    CrossingSiteEntity.CrossingSiteEntityBuilder site = CrossingSiteEntity.builder()
        .crossingSiteId(id).capitalRoadInd("N").crossingSiteTypeCode("XNG")
        .crossingSiteStatusCode("ACT").structureInspectionStatusCode("OK").orgUnitNo(1L)
        .crossingName("Riske Cr").pointOfCommencementDistance(new BigDecimal("0.24"));
    change.accept(site);
    entityManager.persist(site.build());
  }

  private static StructureDetailsRequest details() {
    return new StructureDetailsRequest(null, null, 2010, null, "MOF", null, false, null, null);
  }

  private static BridgeDetailsRequest bridge() {
    return new BridgeDetailsRequest("ignored", false, false, new BigDecimal("24"), "STL", null,
        "TIM", null, new BigDecimal("4.3"), "GRV", "TMB", null, "CON", "CON", null);
  }

  private static InspectionScheduleRequest schedule() {
    return new InspectionScheduleRequest(false, null, null, NEXT_YEAR, 3);
  }

  private static LoadRatingDetailsRequest loadRating() {
    return new LoadRatingDetailsRequest("UNK", true, null, null, null);
  }

  private static StructureCreateRequest newBridge(String name, String site, List<SpanRequest> spans,
      List<PierRequest> piers) {
    return new StructureCreateRequest(name, site, "PB", details(), bridge(), null, spans, piers,
        loadRating(), null, schedule(), "  First comment.  ");
  }

  private Map<String, String> refused(StructureCreateRequest request) {
    try {
      service.create(request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("the structure was added");
  }

  private <T> List<T> all(Class<T> type) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.createQuery("FROM " + type.getSimpleName(), type).getResultList();
  }

  @Test
  @DisplayName("adds a bridge with all its spans and piers, a comment and the NS rating")
  void addsBridge() {
    String id = service.create(newBridge(" b600 ", "62-145",
        List.of(new SpanRequest(1L, new BigDecimal("12")), new SpanRequest(2L,
            new BigDecimal("12"))),
        List.of(new PierRequest(1L, "CRIB")))).id();

    entityManager.flush();
    entityManager.clear();
    CrossingStructureEntity structure =
        entityManager.find(CrossingStructureEntity.class, Long.parseLong(id));
    assertThat(structure.getCrossingStructureName()).isEqualTo("B600");
    assertThat(structure.getCrossingSiteId()).isEqualTo("62-145");
    assertThat(structure.getActiveInd()).isEqualTo("Y");
    assertThat(structure.getStructureSourceCode()).isEqualTo("MOF");
    assertThat(structure.getRoutineInspectionFrequency()).isEqualTo("3");
    assertThat(structure.getNextPlannedInspectionDate()).isEqualTo(NEXT_YEAR);
    assertThat(structure.getCurrentLoadRating()).isEqualByComparingTo("0");
    assertThat(structure.getLoadRatingUnknownIndicator()).isEqualTo("N");
    assertThat(structure.getEntryUserid()).isEqualTo("IDIR\\ADDER");
    assertThat(structure.getEntryTimestamp()).isNotNull();

    ForestServiceBridgeEntity bridge = all(ForestServiceBridgeEntity.class).get(0);
    assertThat(bridge.getCrossingStructureId()).isEqualTo(Long.parseLong(id));
    assertThat(bridge.getSuperstructureTypeCode()).isEqualTo("STL");
    // Every span and pier, where legacy kept only the first of each.
    assertThat(all(ForestServiceBridgeSpanEntity.class)).hasSize(2);
    assertThat(all(ForestServiceBridgePierEntity.class)).hasSize(1);
    assertThat(all(StructureCommentEntity.class)).singleElement()
        .extracting(StructureCommentEntity::getStructureComment).isEqualTo("First comment.");
    assertThat(all(StructureLoadRatingEntity.class)).singleElement()
        .satisfies(rating -> {
          assertThat(rating.getReasonCode()).isEqualTo("NS");
          assertThat(rating.getLoadRating()).isEqualByComparingTo("0");
        });
  }

  @Test
  @DisplayName("adds a Wood Log Culvert as BOX, with no culvert type asked for")
  void addsWoodLogCulvert() {
    CulvertDetailsRequest culvert = new CulvertDetailsRequest(false, 1, new BigDecimal("12"),
        null, null, "CSP", null, null, null, 600L, 900L, null, null);
    service.create(new StructureCreateRequest("C700", "62-145", "WLC", details(), null, culvert,
        null, null, loadRating(), null, schedule(), null));

    assertThat(all(ForestServiceCulvertEntity.class)).singleElement()
        .extracting(ForestServiceCulvertEntity::getEngineeredCulvertTypeCode).isEqualTo("BOX");
  }

  @Test
  @DisplayName("requires what the structure page would list as outstanding")
  void requiresCompleteness() {
    StructureCreateRequest request = new StructureCreateRequest(null, null, "PB",
        new StructureDetailsRequest(null, null, null, null, null, null, false, null, null),
        new BridgeDetailsRequest(null, false, false, null, null, null, null, null, null, null,
            "TMB", null, null, null, null),
        null, List.of(), List.of(), loadRating(), null,
        new InspectionScheduleRequest(false, null, null, null, 3), null);

    assertThat(refused(request)).containsOnlyKeys("structureName", "siteId",
        "details.sourceCode", "bridge.totalBridgeLength", "bridge.deckWidth",
        "bridge.superstructureTypeCode", "bridge.deckTypeCode", "bridge.runningSurfaceCode",
        "bridge.leftAbutmentCode", "bridge.rightAbutmentCode", "spans",
        "schedule.nextRoutineDate");
    assertThat(all(CrossingStructureEntity.class)).hasSize(1);
  }

  @Test
  @DisplayName("refuses a taken name, a Proposed, closed or incomplete site, and too few spans")
  void refuses() {
    List<SpanRequest> oneSpan = List.of(new SpanRequest(1L, new BigDecimal("12")));
    assertThat(refused(newBridge("b500", "62-PP", oneSpan, List.of(new PierRequest(1L, "CRIB")))))
        .contains(
            Map.entry("structureName", "Structure B500 already exists."),
            Map.entry("siteId",
                "Site 62-PP is Proposed. A structure cannot be moved to a Proposed site."),
            Map.entry("spans", "Number of spans must be greater than number of piers."));
    assertThat(refused(newBridge("B601", "62-DAC", oneSpan, List.of())))
        .containsEntry("siteId",
            "Site 62-DAC is closed (deactivated). A structure cannot be added to it.");
    assertThat(refused(newBridge("B601", "62-INC", oneSpan, List.of())))
        .containsEntry("siteId",
            "Site 62-INC is incomplete. Complete it on its Site Detail page first.");
  }

  @Test
  @DisplayName("takes Replacement Details from a P.Eng only, Load Restriction optional")
  void replacementIsPeng() {
    ReplacementDetailsRequest replacement = new ReplacementDetailsRequest(2060, null, null, null,
        null);
    StructureCreateRequest request = new StructureCreateRequest("B602", "62-145", "PB",
        details(), bridge(), null, List.of(new SpanRequest(1L, new BigDecimal("12"))),
        List.of(), loadRating(), replacement, schedule(), null);

    assertThat(refused(request))
        .containsEntry("replacement", "Only a P.Eng can enter Replacement Details.");

    when(loggedUser.isPeng()).thenReturn(true);
    String id = service.create(request).id();
    entityManager.flush();
    entityManager.clear();
    assertThat(entityManager.find(CrossingStructureEntity.class, Long.parseLong(id))
        .getEstimatedClosureYear()).isEqualTo(2060);
  }
}
