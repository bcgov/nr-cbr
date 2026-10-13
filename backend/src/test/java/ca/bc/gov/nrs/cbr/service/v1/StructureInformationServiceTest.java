package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CbrOrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureNameHistEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionStatusCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.SitePreviewResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInformationRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

/** The Information tab's Edit — Structure # and Site # — against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({StructureInformationService.class, StructureDetailService.class, LoadRatingService.class})
class StructureInformationServiceTest {

  @Autowired
  private StructureInformationService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    when(loggedUser.canDestroy()).thenReturn(true);
    for (String entity : List.of("CrossingStructureNameHistEntity", "CrossingStructureEntity",
        "CrossingSiteEntity", "CbrOrgUnitEntity", "OrgUnitEntity", "CrossingSiteStatusCodeEntity",
        "StructureInspectionStatusCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    // The site's codes and district — the test schema keeps the foreign keys.
    for (String status : List.of("ACT", "PP")) {
      entityManager.persist(CrossingSiteStatusCodeEntity.builder()
          .crossingSiteStatusCode(status).description(status).build());
    }
    entityManager.persist(StructureInspectionStatusCodeEntity.builder()
        .structureInspectionStatusCode("OK").description("Inspected").build());
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(1L).orgUnitCode("DCC").orgUnitName("Cariboo-Chilcotin").build());
    entityManager.persist(CbrOrgUnitEntity.builder()
        .orgUnitNo(1L).orgUnitCode("DCC").orgUnitName("Cariboo-Chilcotin District").build());
    givenSite("62-001", site -> { });
    givenStructure(7L, "B100", "62-001", true);
  }

  /** A complete crossing site, unless the caller changes it. */
  private void givenSite(String id, Consumer<CrossingSiteEntity.CrossingSiteEntityBuilder> change) {
    CrossingSiteEntity.CrossingSiteEntityBuilder site = CrossingSiteEntity.builder()
        .crossingSiteId(id).capitalRoadInd("N").crossingSiteTypeCode("XNG")
        .crossingSiteStatusCode("ACT").structureInspectionStatusCode("OK").orgUnitNo(1L)
        .crossingName("Riske Cr").pointOfCommencementDistance(new BigDecimal("0.24"));
    change.accept(site);
    entityManager.persist(site.build());
  }

  private void givenStructure(long id, String name, String siteId, boolean active) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName(name).crossingSiteId(siteId)
        .structureTypeClassCode("TB").activeInd(active ? "Y" : "N").closeProximityInd("N")
        .portableStructureInd("N").build());
  }

  private CrossingStructureEntity stored() {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(CrossingStructureEntity.class, 7L);
  }

  private Map<String, String> refused(String name, String siteId) {
    StructureInformationRequest request = new StructureInformationRequest(name, siteId);
    try {
      service.update(7L, request);
    } catch (FieldValidationException refusal) {
      return refusal.getFieldErrors();
    }
    throw new AssertionError("the information was saved");
  }

  @Test
  @DisplayName("renames the structure, recording the old name, who and when")
  void renames() {
    service.update(7L, new StructureInformationRequest(" B200 ", "62-001"));

    CrossingStructureEntity structure = stored();
    assertThat(structure.getCrossingStructureName()).isEqualTo("B200");
    assertThat(structure.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(structure.getActiveInd()).isEqualTo("Y");
    List<CrossingStructureNameHistEntity> history = entityManager
        .createQuery("SELECT h FROM CrossingStructureNameHistEntity h",
            CrossingStructureNameHistEntity.class)
        .getResultList();
    assertThat(history).singleElement().satisfies(row -> {
      assertThat(row.getOldCrossingStructureName()).isEqualTo("B100");
      assertThat(row.getCrossingStructureId()).isEqualTo(7L);
      assertThat(row.getChangeUserid()).isEqualTo("IDIR\\EDITOR");
      assertThat(row.getChangeTimestamp().getNano()).isZero();
    });
  }

  @Test
  @DisplayName("needs a name of at most 14 characters that no structure has, archived ones too")
  void refusesNames() {
    givenStructure(8L, "B200", "62-001", false);

    assertThat(refused(" ", "62-001"))
        .containsExactly(Map.entry("structureName", "Structure # is required."));
    assertThat(refused("B123456789012345", "62-001"))
        .containsExactly(Map.entry("structureName", "Structure # can be at most 14 characters."));
    assertThat(refused("B200", "62-001"))
        .containsExactly(Map.entry("structureName", "Structure B200 already exists."));
  }

  @Test
  @DisplayName("lets only Level 2 rename, as legacy's page offered the box to Level 2 only")
  void renameNeedsLevel2() {
    when(loggedUser.canDestroy()).thenReturn(false);

    assertThat(refused("B200", "62-001")).containsExactly(
        Map.entry("structureName", "Structure # can be changed only at Level 2 and up."));
  }

  @Test
  @DisplayName("moves the structure to another site at Level 1, its name unchanged")
  void movesAtLevel1() {
    when(loggedUser.canDestroy()).thenReturn(false);
    givenSite("62-145", site -> { });

    service.update(7L, new StructureInformationRequest("B100", "62-145"));

    assertThat(stored().getCrossingSiteId()).isEqualTo("62-145");
    assertThat(entityManager.createQuery("SELECT COUNT(h) FROM CrossingStructureNameHistEntity h",
        Long.class).getSingleResult()).isZero();
  }

  @Test
  @DisplayName("refuses a site that does not exist, is incomplete or is Proposed, or no site")
  void refusesSites() {
    givenSite("62-200", site -> site.crossingName(null));
    givenSite("62-300", site -> site.crossingSiteStatusCode("PP"));

    assertThat(refused("B100", "62-999"))
        .containsExactly(Map.entry("siteId", "Site 62-999 does not exist."));
    assertThat(refused("B100", "62-200")).containsExactly(Map.entry("siteId",
        "Site 62-200 is incomplete. Complete it on its Site Detail page first."));
    assertThat(refused("B100", "62-300")).containsExactly(Map.entry("siteId",
        "Site 62-300 is Proposed. A structure cannot be moved to a Proposed site."));
    assertThat(refused("B100", " "))
        .containsExactly(Map.entry("siteId", "Site # is required."));
  }

  @Test
  @DisplayName("takes a storage site with no crossing name or kilometres, as legacy")
  void storageSiteIsComplete() {
    givenSite("ST-1", site -> site.crossingSiteTypeCode("STRG").crossingName(null)
        .pointOfCommencementDistance(null));

    service.update(7L, new StructureInformationRequest("B100", "ST-1"));

    assertThat(stored().getCrossingSiteId()).isEqualTo("ST-1");
  }

  @Test
  @DisplayName("leaves an archived structure archived")
  void staysArchived() {
    givenStructure(9L, "B900", "62-001", false);

    service.update(9L, new StructureInformationRequest("B901", "62-001"));

    entityManager.flush();
    entityManager.clear();
    assertThat(entityManager.find(CrossingStructureEntity.class, 9L).getActiveInd()).isEqualTo("N");
  }

  @Test
  @DisplayName("previews a site with what would stop the move, or nothing")
  void previews() {
    givenSite("62-300", site -> site.crossingSiteStatusCode("PP"));

    SitePreviewResponse fine = service.preview("62-001");
    assertThat(fine.site().crossingName()).isEqualTo("Riske Cr");
    assertThat(fine.problem()).isNull();
    assertThat(service.preview("62-300").problem()).contains("is Proposed");
    assertThat(service.preview("62-999"))
        .isEqualTo(new SitePreviewResponse(null, "Site 62-999 does not exist."));
  }

  @Test
  @DisplayName("refuses a structure that does not exist")
  void missingStructure() {
    StructureInformationRequest request = new StructureInformationRequest("B1", "62-001");

    assertThatThrownBy(() -> service.update(404L, request))
        .isInstanceOf(StructureNotFoundException.class);
  }
}
