package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredClvrtMaterialCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.EngineeredCulvertTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.HeadwallLocationCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OpnBtomClvrtSubstrctreCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CulvertDetailsRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

/** The Details tab's Culvert card edit, against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureCulvertService.class)
class StructureCulvertServiceTest {

  private static final LocalDateTime PAST = LocalDateTime.now().minusYears(5);
  private static final LocalDateTime FUTURE = LocalDateTime.now().plusYears(5);

  @Autowired
  private StructureCulvertService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    for (String entity : List.of("ForestServiceCulvertEntity", "CrossingStructureEntity",
        "EngineeredCulvertTypeCodeEntity", "EngineeredClvrtMaterialCodeEntity",
        "HeadwallLocationCodeEntity", "OpnBtomClvrtSubstrctreCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (String code : List.of("RND", "OPN", "BOX")) {
      entityManager.persist(EngineeredCulvertTypeCodeEntity.builder()
          .engineeredCulvertTypeCode(code).description(code).effectiveDate(PAST)
          .expiryDate(FUTURE).build());
    }
    for (String code : List.of("CSP", "OTH")) {
      entityManager.persist(EngineeredClvrtMaterialCodeEntity.builder()
          .engineeredClvrtMaterialCode(code).description(code).effectiveDate(PAST)
          .expiryDate(FUTURE).build());
    }
    entityManager.persist(HeadwallLocationCodeEntity.builder().headwallLocationCode("IN")
        .description("Inlet").effectiveDate(PAST).expiryDate(FUTURE).build());
    entityManager.persist(OpnBtomClvrtSubstrctreCodeEntity.builder()
        .opnBtomClvrtSubstrctreCode("CON").description("Concrete").effectiveDate(PAST)
        .expiryDate(FUTURE).build());
    givenCulvert(7L, "CUL", 70L);
    givenCulvert(8L, "WLC", 80L);
  }

  private void givenCulvert(long structureId, String typeClass, long culvertId) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(structureId).crossingStructureName("C" + structureId)
        .structureTypeClassCode(typeClass).activeInd("Y").closeProximityInd("N")
        .portableStructureInd("N").build());
    entityManager.persist(ForestServiceCulvertEntity.builder()
        .forestServiceCulvertId(culvertId).crossingStructureId(structureId).culvertNumber(1)
        .culvertLength(new BigDecimal("12.0")).engineeredCulvertTypeCode("RND")
        .engineeredCulvertMaterialCode("CSP").openingHeight(900L).openingWidth(900L).build());
  }

  private static CulvertDetailsRequest culvert(String type, String openBottom, String material,
      String materialComment) {
    return new CulvertDetailsRequest(false, 2, new BigDecimal("14.5"), new BigDecimal("-2.5"),
        type, material, materialComment, 600L, 700L, 1200L, 1500L, "IN", openBottom);
  }

  private Map<String, String> refused(long id, CulvertDetailsRequest request) {
    try {
      service.update(id, request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("the culvert was saved");
  }

  private ForestServiceCulvertEntity stored(long culvertId) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(ForestServiceCulvertEntity.class, culvertId);
  }

  @Test
  @DisplayName("saves the culvert and Portable, keeping material words only with Other")
  void saves() {
    service.update(7L, new CulvertDetailsRequest(true, 2, new BigDecimal("14.5"),
        new BigDecimal("-2.5"), "OPN", "CSP", "left over", 600L, 700L, 1200L, 1500L, "IN",
        "CON"));

    ForestServiceCulvertEntity culvert = stored(70L);
    assertThat(culvert.getCulvertNumber()).isEqualTo(2);
    assertThat(culvert.getCulvertLength()).isEqualByComparingTo("14.5");
    assertThat(culvert.getSlope()).isEqualByComparingTo("-2.5");
    assertThat(culvert.getEngineeredCulvertTypeCode()).isEqualTo("OPN");
    assertThat(culvert.getOpnBtomClvrtSubstrctreCode()).isEqualTo("CON");
    assertThat(culvert.getCulvertMaterialComment()).isNull();
    assertThat(entityManager.find(CrossingStructureEntity.class, 7L).getPortableStructureInd())
        .isEqualTo("Y");
  }

  @Test
  @DisplayName("needs an open bottom for an open-bottom culvert, and words for Other material")
  void openBottomAndOther() {
    assertThat(refused(7L, culvert("OPN", null, "OTH", " "))).containsExactly(
        Map.entry("openBottomCode",
            "Open Bottom Substructure is required for an open-bottom culvert."),
        Map.entry("materialComment", "Culvert Details is required."));
  }

  @Test
  @DisplayName("keeps fields required once set, and values to their columns")
  void tierAndColumns() {
    CulvertDetailsRequest request = new CulvertDetailsRequest(false, null,
        new BigDecimal("1.25"), new BigDecimal("1000"), null, null, null, -1L, null, null, null,
        null, null);

    assertThat(refused(7L, request)).containsExactly(
        Map.entry("culvertNumber", "Culvert Number is required."),
        Map.entry("lengthMetres", "Culvert Length can have at most 1 decimal place."),
        Map.entry("gradient", "Gradient must be from -999.9 to 999.9."),
        Map.entry("inletCoverDepthMm",
            "Inlet Cover Depth must be a whole number of millimetres from 0 to 9999999999."),
        Map.entry("openingHeightMm", "Opening Height is required."),
        Map.entry("openingWidthMm", "Opening Width is required."),
        Map.entry("culvertTypeCode", "Culvert Type is required."),
        Map.entry("materialCode", "Culvert Material is required."));
  }

  @Test
  @DisplayName("saves a Wood Log Culvert as BOX with its open bottom untouched, as legacy's page")
  void woodLog() {
    service.update(8L, culvert("RND", "CON", "CSP", null));

    ForestServiceCulvertEntity culvert = stored(80L);
    assertThat(culvert.getEngineeredCulvertTypeCode()).isEqualTo("BOX");
    assertThat(culvert.getOpnBtomClvrtSubstrctreCode()).isNull();
  }
}
