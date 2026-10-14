package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.BuiltByCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureSourceCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailsRequest;
import jakarta.persistence.EntityManager;
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

/** The Details tab's Structure card edit, against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureDetailsService.class)
class StructureDetailsServiceTest {

  private static final int THIS_YEAR = LocalDate.now().getYear();

  @Autowired
  private StructureDetailsService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    for (String entity : List.of("CrossingStructureEntity", "BuiltByCodeEntity",
        "StructureSourceCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    LocalDateTime past = LocalDateTime.now().minusYears(1);
    LocalDateTime future = LocalDateTime.now().plusYears(1);
    entityManager.persist(BuiltByCodeEntity.builder().builtByCode("MOF")
        .description("Ministry of Forests").effectiveDate(past).expiryDate(future).build());
    for (String code : List.of("MOF", "IR")) {
      entityManager.persist(StructureSourceCodeEntity.builder().structureSourceCode(code)
          .description(code).effectiveDate(past).expiryDate(future).build());
    }
    givenStructure(7L, "MOF");
    givenStructure(8L, null);
  }

  private void givenStructure(long id, String source) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName("B" + id).structureTypeClassCode("TB")
        .activeInd("N").closeProximityInd("N").portableStructureInd("N").asBuiltInfoPresentInd("N")
        .structureSourceCode(source).build());
  }

  private static StructureDetailsRequest request(Integer fabricated, Integer built, Integer added,
      String source, Long installation) {
    return new StructureDetailsRequest("MOF", fabricated, built, added, source, 2050, true,
        installation, 0L);
  }

  private Map<String, String> refused(long id, StructureDetailsRequest request) {
    try {
      service.updateStructure(id, request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("the details were saved");
  }

  private CrossingStructureEntity stored(long id) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(CrossingStructureEntity.class, id);
  }

  @Test
  @DisplayName("saves the card's fields and who changed them, leaving an archived structure so")
  void saves() {
    service.updateStructure(7L, new StructureDetailsRequest(" MOF ", 1998, 2000, 2001, "MOF",
        2050, true, 125_000L, 0L));

    CrossingStructureEntity structure = stored(7L);
    assertThat(structure.getBuiltByCode()).isEqualTo("MOF");
    assertThat(structure.getYearFabricated()).isEqualTo(1998);
    assertThat(structure.getYearBuilt()).isEqualTo(2000);
    assertThat(structure.getInventoryAddedYear()).isEqualTo(2001);
    assertThat(structure.getOriginalReplacementYear()).isEqualTo(2050);
    assertThat(structure.getAsBuiltInfoPresentInd()).isEqualTo("Y");
    assertThat(structure.getInstallationCost()).isEqualTo(125_000L);
    assertThat(structure.getMaterialCost()).isZero();
    assertThat(structure.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(structure.getActiveInd()).isEqualTo("N");
  }

  @Test
  @DisplayName("saves a year legacy only warns about, such as 1850")
  void savesWhatLegacyOnlyWarnsAbout() {
    service.updateStructure(7L, request(null, 1850, null, "MOF", 600_000L));

    assertThat(stored(7L).getYearBuilt()).isEqualTo(1850);
  }

  @Test
  @DisplayName("refuses a fabrication year after this year or after the year installed")
  void refusesFabricationYears() {
    assertThat(refused(7L, request(THIS_YEAR + 1, null, null, "MOF", null)))
        .containsExactly(Map.entry("yearFabricated",
            "Year Superstructure Fabricated must not be after this year (" + THIS_YEAR + ")."));
    assertThat(refused(7L, request(2005, 2000, null, "MOF", null)))
        .containsExactly(Map.entry("yearFabricated",
            "Year Superstructure Fabricated must not be after Year Superstructure Installed."));
  }

  @Test
  @DisplayName("needs Source once the structure has one, and lets one without stay blank")
  void sourceOnceSet() {
    assertThat(refused(7L, request(null, null, null, " ", null)))
        .containsExactly(Map.entry("sourceCode", "Source is required."));

    service.updateStructure(8L, request(null, null, null, null, null));
    assertThat(stored(8L).getStructureSourceCode()).isNull();
  }

  @Test
  @DisplayName("needs Year Added to Inventory for the sources that call for it")
  void yearAddedForSomeSources() {
    assertThat(refused(7L, request(null, null, null, "IR", null))).containsExactly(
        Map.entry("inventoryAddedYear", "Year Added to Inventory is required for this Source."));
  }

  @Test
  @DisplayName("refuses unknown codes, years over 4 digits and costs the column cannot hold")
  void refusesWhatTheColumnsCannotHold() {
    StructureDetailsRequest request = new StructureDetailsRequest("NOPE", null, 20000, null,
        "NOPE", null, null, 1_000_000L, -1L);

    assertThat(refused(7L, request)).containsExactly(
        Map.entry("builtByCode", "Built For is not one of the listed values."),
        Map.entry("yearBuilt", "Year Superstructure Installed must be a 4-digit year, e.g. 2004."),
        Map.entry("sourceCode", "Source is not one of the listed values."),
        Map.entry("asBuiltInfoOnFile", "As Built Information on File is required."),
        Map.entry("installationCost", "Installation Cost must be from $0 to $999,999."),
        Map.entry("materialCost", "Material Cost must be from $0 to $999,999."));
  }

  @Test
  @DisplayName("needs every year written with four digits, where legacy took 200")
  void fourDigitYears() {
    StructureDetailsRequest request = new StructureDetailsRequest("MOF", 199, null, 200, "MOF",
        999, true, null, null);

    assertThat(refused(7L, request)).containsExactly(
        Map.entry("yearFabricated", "Year Superstructure Fabricated must be a 4-digit year, e.g. 2004."),
        Map.entry("inventoryAddedYear", "Year Added to Inventory must be a 4-digit year, e.g. 2004."),
        Map.entry("endOfDesignLifeYear", "End of Design Life must be a 4-digit year, e.g. 2004."));
  }

  private Map<String, String> replacementRefused(long id, ReplacementDetailsRequest request) {
    try {
      service.updateReplacement(id, request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("the replacement details were saved");
  }

  @Test
  @DisplayName("saves Replacement Details, what legacy only warns about included")
  void savesReplacement() {
    service.updateReplacement(7L,
        new ReplacementDetailsRequest(2060, 2055, 2050, 3_000_000L, " Steel bridge "));

    CrossingStructureEntity structure = stored(7L);
    assertThat(structure.getEstimatedClosureYear()).isEqualTo(2060);
    assertThat(structure.getLightVehicleReplacementYear()).isEqualTo(2055);
    assertThat(structure.getFullLogHaulReplacementYear()).isEqualTo(2050);
    assertThat(structure.getEstimatedReplacementCost()).isEqualTo(3_000_000L);
    assertThat(structure.getEstimatedReplacementCostCmt()).isEqualTo("Steel bridge");
    assertThat(structure.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
  }

  @Test
  @DisplayName("keeps load restriction before replacement, and both before closure")
  void replacementOrder() {
    assertThat(replacementRefused(7L, new ReplacementDetailsRequest(2050, 2055, 2060, null, null)))
        .containsExactly(
            Map.entry("replacementYear",
                "Estimated Replacement must not be after Estimated Closure."),
            Map.entry("loadRestrictionYear",
                "Estimated Load Restriction must not be after Estimated Replacement."));
  }

  @Test
  @DisplayName("needs Load Restriction once set, 4-digit years, and a cost the column holds")
  void replacementRules() {
    service.updateReplacement(7L, new ReplacementDetailsRequest(null, null, 2050, null, null));

    assertThat(replacementRefused(7L,
        new ReplacementDetailsRequest(205, null, null, 100_000_000L, null))).containsExactly(
            Map.entry("closureYear", "Estimated Closure must be a 4-digit year, e.g. 2004."),
            Map.entry("loadRestrictionYear", "Estimated Load Restriction is required."),
            Map.entry("cost", "Estimated Replacement Cost must be whole dollars from $0 to $99,999,999."));
  }

  @Test
  @DisplayName("refuses a structure that does not exist")
  void missingStructure() {
    StructureDetailsRequest request = request(null, null, null, null, null);

    assertThatThrownBy(() -> service.updateStructure(404L, request))
        .isInstanceOf(StructureNotFoundException.class);
  }
}
