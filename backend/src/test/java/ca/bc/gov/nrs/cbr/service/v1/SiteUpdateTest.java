package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.SiteNotFoundException;
import ca.bc.gov.nrs.cbr.exception.SiteValidationException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.security.CbrRoles;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.SiteUpdateRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Editing a site — {@link SiteService#update}.
 *
 * <p>Against the database, as the create is: the rules are foreign keys and a derived road segment,
 * and a mocked repository would only assert what it was told.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({SiteService.class, SiteValidator.class})
class SiteUpdateTest {

  private static final String SITE = "BOWRON-001";
  private static final LocalDateTime ENTERED = LocalDateTime.of(2020, 1, 15, 9, 30);

  @Autowired
  private SiteService service;

  @Autowired
  private CrossingSiteRepository sites;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    entityManager.createQuery("DELETE FROM CrossingStructureEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingSiteEntity").executeUpdate();
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    SiteTestFixtures.givenCodeTables(entityManager);
    givenStoredSite(site -> { });
  }

  /** A complete crossing as stored, including the fields LRMOPS writes and no screen sets. */
  private void givenStoredSite(Consumer<CrossingSiteEntity.CrossingSiteEntityBuilder> change) {
    CrossingSiteEntity.CrossingSiteEntityBuilder site = CrossingSiteEntity.builder()
        .crossingSiteId(SITE)
        .crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.50"))
        .userKm(new BigDecimal("13.00"))
        .crossingSiteStatusCode("ACT")
        .structureInspectionStatusCode("INS")
        .crossingSiteTypeCode("CRS")
        .orgUnitNo(18L)
        .businessAreaOrgUnitNo(31L)
        .forestFileId("R00123")
        .roadSectionId("01")
        .roadSegmentId(77L)
        .clientNumber("00001012")
        .clientLocnCode("01")
        .capitalRoadInd("Y")
        .longitude(new BigDecimal("-122.504306"))
        .latitude(new BigDecimal("53.916667"))
        .ntsMapSheetNumber("92P/10")
        .entryUserid("IDIR\\ORIGINAL")
        .entryTimestamp(ENTERED)
        .updateUserid("IDIR\\ORIGINAL")
        .updateTimestamp(ENTERED);
    change.accept(site);
    entityManager.persist(site.build());
    entityManager.flush();
    entityManager.clear();
  }

  private void givenStructure(long id, String activeInd) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingSiteId(SITE).activeInd(activeInd).build());
    entityManager.flush();
  }

  private void asLevel2() {
    when(loggedUser.isAtLeast(CbrRoles.LEVEL_2)).thenReturn(true);
  }

  private void asLevel1() {
    when(loggedUser.isAtLeast(CbrRoles.LEVEL_2)).thenReturn(false);
  }

  /** The stored site as a request, changed by {@code change} — what Site Detail sends. */
  private static final class Edit {
    String crossingName = "Deadman Creek";
    BigDecimal pointOfCommencementDistance = new BigDecimal("12.50");
    String crossingSiteStatusCode = "ACT";
    String structureInspectionStatusCode = "INS";
    String crossingSiteTypeCode = "CRS";
    String specialAccessRqmtCode = null;
    Long orgUnitNo = 18L;
    Long managementOrgUnitNo = null;
    String forestFileId = "R00123";
    String roadSectionId = "01";
    BigDecimal longitude = new BigDecimal("-122.504306");
    BigDecimal latitude = new BigDecimal("53.916667");
    Integer utmZone = null;
    Long utmEasting = null;
    Long utmNorthing = null;
    String pointOfAccessDescription = null;

    SiteUpdateRequest build() {
      return new SiteUpdateRequest(crossingName, pointOfCommencementDistance,
          crossingSiteStatusCode, structureInspectionStatusCode, crossingSiteTypeCode,
          specialAccessRqmtCode, orgUnitNo, managementOrgUnitNo, forestFileId, roadSectionId,
          longitude, latitude, utmZone, utmEasting, utmNorthing, pointOfAccessDescription);
    }
  }

  private void save(Consumer<Edit> change) {
    Edit edit = new Edit();
    change.accept(edit);
    service.update(SITE, edit.build());
    entityManager.flush();
    entityManager.clear();
  }

  private Map<String, String> refusedWith(Consumer<Edit> change) {
    try {
      save(change);
    } catch (SiteValidationException refused) {
      return refused.getFieldErrors();
    }
    throw new AssertionError("Expected the edit to be refused, but it was stored.");
  }

  private CrossingSiteEntity stored() {
    return sites.findById(SITE).orElseThrow();
  }

  @Nested
  @DisplayName("by Level 2")
  class ByLevel2 {

    @BeforeEach
    void role() {
      asLevel2();
    }

    @Test
    @DisplayName("stores what was changed")
    void storesTheEdit() {
      save(edit -> {
        edit.crossingName = "Deadman Creek Bridge";
        edit.specialAccessRqmtCode = "HEL";
        edit.pointOfAccessDescription = "Gate key at the district office.";
      });

      CrossingSiteEntity site = stored();
      assertThat(site.getCrossingName()).isEqualTo("Deadman Creek Bridge");
      assertThat(site.getSpecialAccessRqmtCode()).isEqualTo("HEL");
      assertThat(site.getPointOfAccessDesc()).isEqualTo("Gate key at the district office.");
    }

    @Test
    @DisplayName("stamps the update pair and leaves the entry pair as it was")
    void stampsTheAudit() {
      save(edit -> edit.crossingName = "Deadman Creek Bridge");

      CrossingSiteEntity site = stored();
      assertThat(site.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
      assertThat(site.getUpdateTimestamp()).isAfter(ENTERED);
      assertThat(site.getEntryUserid()).isEqualTo("IDIR\\ORIGINAL");
      assertThat(site.getEntryTimestamp()).isEqualTo(ENTERED);
    }

    @Test
    @DisplayName("keeps the fields LRMOPS writes, which the request cannot carry")
    void keepsTheLockedFields() {
      save(edit -> edit.crossingName = "Deadman Creek Bridge");

      CrossingSiteEntity site = stored();
      assertThat(site.getClientNumber()).isEqualTo("00001012");
      assertThat(site.getClientLocnCode()).isEqualTo("01");
      assertThat(site.getUserKm()).isEqualByComparingTo("13.00");
      assertThat(site.getBusinessAreaOrgUnitNo()).isEqualTo(31L);
      assertThat(site.getCapitalRoadInd()).isEqualTo("Y");
      assertThat(site.getNtsMapSheetNumber()).isEqualTo("92P/10");
    }

    @Test
    @DisplayName("derives the road segment again, as the section's lowest")
    void rederivesTheSegment() {
      // Stored as 77; the section's segments are 42 and 77, and legacy's hidden select re-posts
      // the first on every save.
      save(edit -> { });

      assertThat(stored().getRoadSegmentId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("refuses a required field left blank, as Add Site does")
    void refusesABlankRequiredField() {
      assertThat(refusedWith(edit -> edit.crossingName = " "))
          .containsEntry("crossingName", "Crossing Name is required.");
    }

    @Test
    @DisplayName("refuses a pair whose section has no road segment")
    void refusesARoadWithNoSegment() {
      assertThat(refusedWith(edit -> edit.roadSectionId = "99"))
          .containsEntry("forestFileId", "No road matches this Project File ID# and Br.");
    }

    @Test
    @DisplayName("refuses Proposed while structures stand on the site")
    void refusesProposedWithStructures() {
      givenStructure(1L, "Y");

      assertThat(refusedWith(edit -> {
        edit.crossingSiteStatusCode = "PP";
        edit.structureInspectionStatusCode = "DNI";
      })).containsKey("crossingSiteStatusCode");
    }

    @Test
    @DisplayName("allows Proposed once the only structures are archived")
    void allowsProposedWithArchivedStructures() {
      givenStructure(1L, "N");

      save(edit -> {
        edit.crossingSiteStatusCode = "PP";
        edit.structureInspectionStatusCode = "DNI";
      });

      assertThat(stored().getCrossingSiteStatusCode()).isEqualTo("PP");
    }

    @Test
    @DisplayName("stores nothing when it refuses")
    void storesNothingOnFailure() {
      refusedWith(edit -> {
        edit.specialAccessRqmtCode = "HEL";
        edit.crossingName = "";
      });

      assertThat(stored().getSpecialAccessRqmtCode()).isNull();
    }
  }

  @Nested
  @DisplayName("by Level 1")
  class ByLevel1 {

    @BeforeEach
    void role() {
      asLevel1();
    }

    @Test
    @DisplayName("changes Site Details and nothing else, whatever else the request carries")
    void changesOnlySiteDetails() {
      save(edit -> {
        edit.pointOfAccessDescription = "Gate key at the district office.";
        edit.crossingName = "Renamed by someone who may not";
        edit.crossingSiteStatusCode = "DAC";
      });

      CrossingSiteEntity site = stored();
      assertThat(site.getPointOfAccessDesc()).isEqualTo("Gate key at the district office.");
      assertThat(site.getCrossingName()).isEqualTo("Deadman Creek");
      assertThat(site.getCrossingSiteStatusCode()).isEqualTo("ACT");
      assertThat(site.getRoadSegmentId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("stamps the update pair and leaves the entry pair as it was")
    void stampsTheAudit() {
      // A Level 1 save keeps every column but one, so the audit pair is easy to keep by mistake.
      save(edit -> edit.pointOfAccessDescription = "Gate key at the district office.");

      CrossingSiteEntity site = stored();
      assertThat(site.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
      assertThat(site.getUpdateTimestamp()).isAfter(ENTERED);
      assertThat(site.getEntryUserid()).isEqualTo("IDIR\\ORIGINAL");
      assertThat(site.getEntryTimestamp()).isEqualTo(ENTERED);
    }

    @Test
    @DisplayName("saves Site Details on a site with gaps only Level 2 can fill")
    void ignoresTheRestOfTheSite() {
      // The rest of the site is not theirs to change, so it is not theirs to be refused over.
      entityManager.createQuery("DELETE FROM CrossingSiteEntity").executeUpdate();
      givenStoredSite(site -> site.crossingName(null).latitude(null).roadSectionId("99"));

      save(edit -> edit.pointOfAccessDescription = "Gate key at the district office.");

      assertThat(stored().getPointOfAccessDesc()).isEqualTo("Gate key at the district office.");
    }

    @Test
    @DisplayName("still refuses Site Details longer than the column")
    void refusesOverlongSiteDetails() {
      assertThat(refusedWith(edit -> edit.pointOfAccessDescription = "x".repeat(256)))
          .containsOnlyKeys("pointOfAccessDescription");
    }
  }

  @Test
  @DisplayName("answers 404 for a site that does not exist")
  void refusesAnUnknownSite() {
    asLevel2();

    assertThatThrownBy(() -> service.update("NO-SUCH-SITE", new Edit().build()))
        .isInstanceOf(SiteNotFoundException.class);
  }
}
