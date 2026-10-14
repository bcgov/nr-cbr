package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.SpanPierConflictException;
import ca.bc.gov.nrs.cbr.model.v1.AbutmentCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.DeckTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.RunningSurfaceCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureCurbTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.SuperstructureTypeCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.BridgeDetailsRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The Details tab's Bridge card edit, against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureBridgeService.class)
class StructureBridgeServiceTest {

  @Autowired
  private StructureBridgeService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    for (String entity : List.of("ForestServiceBridgeSpanEntity", "ForestServiceBridgeEntity",
        "CrossingStructureEntity", "SuperstructureTypeCodeEntity", "DeckTypeCodeEntity",
        "RunningSurfaceCodeEntity", "StructureCurbTypeCodeEntity", "AbutmentCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (String code : List.of("STL", "SBB", "RAIL", "OTH")) {
      entityManager.persist(SuperstructureTypeCodeEntity.builder().superstructureTypeCode(code)
          .description(code).build());
    }
    for (String code : List.of("TIM", "OTH")) {
      entityManager.persist(DeckTypeCodeEntity.builder().deckTypeCode(code).description(code)
          .build());
    }
    entityManager.persist(RunningSurfaceCodeEntity.builder().runningSurfaceCode("GRV")
        .description("Gravel").build());
    for (String code : List.of("TMB", "OTH")) {
      entityManager.persist(StructureCurbTypeCodeEntity.builder().structureCurbTypeCode(code)
          .description(code).build());
    }
    for (String code : List.of("CON", "CLT", "OTH")) {
      entityManager.persist(AbutmentCodeEntity.builder().abutmentCode(code).description(code)
          .build());
    }
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(7L).crossingStructureName("B7").structureTypeClassCode("TB")
        .activeInd("Y").closeProximityInd("N").portableStructureInd("N").build());
    entityManager.persist(ForestServiceBridgeEntity.builder()
        .forestServiceBridgeId(70L).crossingStructureId(7L).needleBeamInd("N")
        .totalBridgeLength(new BigDecimal("24.5")).deckWidth(new BigDecimal("4.27"))
        .superstructureTypeCode("STL").deckTypeCode("TIM").runningSurfaceCode("GRV")
        .leftAbutmentCode("CON").rightAbutmentCode("CON").build());
    entityManager.persist(ForestServiceBridgeSpanEntity.builder().forestServiceBridgeId(70L)
        .spanNumber(1).spanLength(new BigDecimal("12")).build());
    entityManager.persist(ForestServiceBridgeSpanEntity.builder().forestServiceBridgeId(70L)
        .spanNumber(2).spanLength(new BigDecimal("10")).build());
  }

  /** The stored bridge as a request, then changed by the test. */
  private static BridgeDetailsRequest bridge(String typeClass, Boolean portable, String length,
      String superstructure, String curb, String left) {
    return new BridgeDetailsRequest(typeClass, portable, true,
        length == null ? null : new BigDecimal(length), superstructure, null, "TIM", null,
        new BigDecimal("4.27"), "GRV", curb, null, left, "CON", null);
  }

  private Map<String, String> refused(BridgeDetailsRequest request) {
    try {
      service.update(7L, request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("the bridge was saved");
  }

  @Test
  @DisplayName("saves the bridge with its Type/Class and Portable, keeping a comment only with Other")
  void saves() {
    service.update(7L, new BridgeDetailsRequest("PB", false, true, new BigDecimal("30"), "OTH",
        " Glulam ", "TIM", "left over", new BigDecimal("5"), "GRV", "TMB", null, "CON", "CON",
        null));

    entityManager.flush();
    entityManager.clear();
    CrossingStructureEntity structure = entityManager.find(CrossingStructureEntity.class, 7L);
    ForestServiceBridgeEntity stored = entityManager.find(ForestServiceBridgeEntity.class, 70L);
    assertThat(structure.getStructureTypeClassCode()).isEqualTo("PB");
    assertThat(structure.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(stored.getNeedleBeamInd()).isEqualTo("Y");
    assertThat(stored.getTotalBridgeLength()).isEqualByComparingTo("30");
    assertThat(stored.getSuperstructureComment()).isEqualTo("Glulam");
    assertThat(stored.getDeckTypeComment()).isNull();
    assertThat(stored.getStructureCurbTypeCode()).isEqualTo("TMB");
  }

  @Test
  @DisplayName("holds a Durable Bridge to its rules: a curb type, no temporary materials")
  void durableRules() {
    assertThat(refused(bridge("PB", false, "24.5", "RAIL", null, "CLT"))).containsExactly(
        Map.entry("curbTypeCode", "Curb Type is required for a Durable Bridge."),
        Map.entry("superstructureTypeCode",
            "Superstructure: temporary materials are not permitted in a Durable Bridge."),
        Map.entry("leftAbutmentCode",
            "Left Abutment: temporary materials are not permitted in a Durable Bridge."));
  }

  @Test
  @DisplayName("allows only portable superstructures on a Portable structure")
  void portableRule() {
    assertThat(refused(bridge("TB", true, "24.5", "STL", null, "CON")))
        .containsOnlyKeys("superstructureTypeCode");

    service.update(7L, bridge("TB", true, "24.5", "SBB", null, "CON"));
  }

  @Test
  @DisplayName("keeps the bridge at least as long as its spans, and fields required once set")
  void lengthAndTier() {
    assertThat(refused(bridge("TB", false, "21", "STL", null, "CON"))).containsExactly(
        Map.entry("totalBridgeLength", "Bridge Length must be at least the spans' total of 22 metres."));
    assertThat(refused(bridge("TB", false, null, null, null, null))).containsExactly(
        Map.entry("totalBridgeLength", "Bridge Length is required."),
        Map.entry("superstructureTypeCode", "Superstructure is required."),
        Map.entry("leftAbutmentCode", "Left Abutment is required."));
  }

  @Test
  @DisplayName("needs details for an Other, and refuses a Type/Class that is not a bridge's")
  void otherAndTypeClass() {
    BridgeDetailsRequest request = new BridgeDetailsRequest("CUL", false, false,
        new BigDecimal("24.5"), "OTH", " ", "OTH", null, new BigDecimal("1.234"), "GRV", "OTH",
        null, "OTH", "CON", null);

    assertThat(refused(request)).containsExactly(
        Map.entry("typeClassCode",
            "Type/Class can only be changed between Durable Bridge and Non-Durable Bridge."),
        Map.entry("deckWidth", "Deck Width can have at most 2 decimal places."),
        Map.entry("superstructureComment", "Superstructure Details is required."),
        Map.entry("deckTypeComment", "Deck Type Details is required."),
        Map.entry("curbTypeComment", "Curb Type Description is required."),
        Map.entry("abutmentComment", "Abutment Details is required."));
  }

  @Test
  @DisplayName("refuses a structure with no bridge record")
  void noBridgeRecord() {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(8L).crossingStructureName("B8").structureTypeClassCode("TB")
        .activeInd("Y").closeProximityInd("N").portableStructureInd("N").build());
    BridgeDetailsRequest request = bridge("TB", false, "1", "STL", null, "CON");

    assertThatThrownBy(() -> service.update(8L, request))
        .isInstanceOf(SpanPierConflictException.class);
  }
}
