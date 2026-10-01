package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.SiteValidationException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.SiteCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.SiteCreatedResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
 * Creating a site: what the server stores, and everything it refuses.
 *
 * <p>Against the database rather than mocks, and with the real {@link SiteValidator}. The rules and
 * the columns have to agree — a value the validator lets through and Oracle will not hold is
 * exactly the bug this catches, and a mocked repository cannot tell you about it.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({SiteService.class, SiteValidator.class})
class SiteCreateTest {

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
    entityManager.createQuery("DELETE FROM CrossingSiteEntity").executeUpdate();
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\JSMITH");
    SiteTestFixtures.givenCodeTables(entityManager);
  }

  /**
   * A site that passes every rule, as something a case can change one field of.
   *
   * <p>Mutable, and deliberately not a record copy: {@code SiteCreateRequest} has twenty-two
   * components, so "the valid request but with a different longitude" written positionally is
   * twenty-one chances to put a value in the wrong slot — and the compiler cannot tell two adjacent
   * {@code String} fields apart.
   */
  private static final class Draft {
    String siteId = "BOWRON-001";
    String crossingName = "Deadman Creek";
    BigDecimal pointOfCommencementDistance = new BigDecimal("12.50");
    BigDecimal userKm = new BigDecimal("13.00");
    String crossingSiteStatusCode = "ACT";
    String structureInspectionStatusCode = "INS";
    String crossingSiteTypeCode = "CRS";
    String specialAccessRqmtCode = "HEL";
    Long orgUnitNo = 18L;
    Long managementOrgUnitNo = 26L;
    Long businessAreaOrgUnitNo = 31L;
    String forestFileId = "R00123";
    String roadSectionId = "01";
    String clientNumber = null;
    String clientLocnCode = null;
    boolean capitalRoad = true;
    BigDecimal longitude = new BigDecimal("-122.504306");
    BigDecimal latitude = new BigDecimal("53.916667");
    Integer utmZone = 10;
    Long utmEasting = 532000L;
    Long utmNorthing = 5975000L;
    String pointOfAccessDescription = "Helicopter required to reach the cove.";

    SiteCreateRequest build() {
      return new SiteCreateRequest(siteId, crossingName, pointOfCommencementDistance, userKm,
          crossingSiteStatusCode, structureInspectionStatusCode, crossingSiteTypeCode,
          specialAccessRqmtCode, orgUnitNo, managementOrgUnitNo, businessAreaOrgUnitNo,
          forestFileId, roadSectionId, clientNumber, clientLocnCode, capitalRoad, longitude,
          latitude, utmZone, utmEasting, utmNorthing, pointOfAccessDescription);
    }
  }

  /** The valid request, changed by `change` and built. */
  private static SiteCreateRequest site(Consumer<Draft> change) {
    Draft draft = new Draft();
    change.accept(draft);
    return draft.build();
  }

  private static SiteCreateRequest valid() {
    return new Draft().build();
  }

  /** The field errors a request is refused with, or a failure if it is accepted. */
  private Map<String, String> refusedWith(Consumer<Draft> change) {
    try {
      service.create(site(change));
    } catch (SiteValidationException refused) {
      return refused.getFieldErrors();
    }
    throw new AssertionError("Expected the site to be refused, but it was stored.");
  }

  @Nested
  @DisplayName("a site that passes")
  class Accepted {

    @Test
    @DisplayName("is stored, and answered with the number it was stored under")
    void storesTheRow() {
      // The answer is the key and nothing else — the caller holds what it sent, and the screen it
      // opens next reads the site for itself. The row is checked here instead.
      assertThat(service.create(valid()).siteId()).isEqualTo("BOWRON-001");
      entityManager.flush();
      entityManager.clear();

      CrossingSiteEntity stored = sites.findById("BOWRON-001").orElseThrow();
      assertThat(stored.getCrossingName()).isEqualTo("Deadman Creek");
      assertThat(stored.getOrgUnitNo()).isEqualTo(18L);
      assertThat(stored.getLongitude()).isEqualByComparingTo("-122.504306");
      assertThat(stored.getCapitalRoadInd()).isEqualTo("Y");
    }

    @Test
    @DisplayName("upper-cases the number, as INSERT_SITE does with UPPER(P_SITE_ID)")
    void upperCasesTheSiteId() {
      // Legacy folds it in the procedure, not only in the browser — so the key is upper case
      // whatever client sent it. A mixed-case row would be a site the search could not find.
      assertThat(service.create(site(draft -> draft.siteId = "bowron-002")).siteId())
          .isEqualTo("BOWRON-002");
      assertThat(sites.existsById("BOWRON-002")).isTrue();
    }

    @Test
    @DisplayName("stamps all four audit columns, which are NOT NULL and have no trigger")
    void stampsTheAuditColumns() {
      service.create(valid());
      entityManager.flush();
      entityManager.clear();

      CrossingSiteEntity stored = sites.findById("BOWRON-001").orElseThrow();
      assertThat(stored.getEntryUserid()).isEqualTo("IDIR\\JSMITH");
      assertThat(stored.getUpdateUserid()).isEqualTo("IDIR\\JSMITH");
      assertThat(stored.getEntryTimestamp()).isNotNull();
      // Entry and update are the same instant on a create, which is what `Site.save` writes.
      assertThat(stored.getUpdateTimestamp()).isEqualTo(stored.getEntryTimestamp());
    }

    @Test
    @DisplayName("stores an empty optional box as null rather than as the empty string")
    void storesBlanksAsNull() {
      service.create(site(draft -> draft.clientNumber = "   "));
      entityManager.flush();
      entityManager.clear();

      assertThat(sites.findById("BOWRON-001").orElseThrow().getClientNumber()).isNull();
    }

    @Test
    @DisplayName("stores the section's first segment, as the hidden select posts back")
    void derivesTheRoadSegment() {
      // Nobody picks this. Legacy's Road Segment control is a <select> in two visibility:hidden
      // cells, and with no blank option the browser posts whichever the ordering put first.
      service.create(valid());
      entityManager.flush();
      entityManager.clear();

      assertThat(sites.findById("BOWRON-001").orElseThrow().getRoadSegmentId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("excuses a storage site its crossing name and kilometre mark")
    void excusesAStorageSite() {
      SiteCreatedResponse created = service.create(site(draft -> {
        draft.siteId = "STORE-001";
        draft.crossingSiteTypeCode = "STRG";
        draft.structureInspectionStatusCode = "DNI";
        draft.crossingName = null;
        draft.pointOfCommencementDistance = null;
      }));

      assertThat(created.siteId()).isEqualTo("STORE-001");
    }

    @Test
    @DisplayName("excuses a recreation site its forest district and Br.")
    void excusesARecreationSite() {
      SiteCreatedResponse created = service.create(site(draft -> {
        draft.siteId = "REC-001";
        draft.crossingSiteTypeCode = "REC";
        draft.structureInspectionStatusCode = "DNI";
        draft.orgUnitNo = null;
        draft.managementOrgUnitNo = null;
        draft.roadSectionId = null;
      }));

      assertThat(created.siteId()).isEqualTo("REC-001");
    }

    @Test
    @DisplayName("leaves a recreation site's segment null, having no road to take one from")
    void storesNoSegmentForARecreationSite() {
      // Its Project File ID# names a recreation project, not a road file — so looking a segment up
      // from it would be asking the wrong table a question about the wrong number.
      service.create(site(draft -> {
        draft.siteId = "REC-002";
        draft.crossingSiteTypeCode = "REC";
        draft.structureInspectionStatusCode = "DNI";
        draft.orgUnitNo = null;
        draft.managementOrgUnitNo = null;
        draft.roadSectionId = null;
      }));
      entityManager.flush();
      entityManager.clear();

      assertThat(sites.findById("REC-002").orElseThrow().getRoadSegmentId()).isNull();
    }
  }

  @Nested
  @DisplayName("coordinates legacy stores")
  class LegacyCoordinates {

    @Test
    @DisplayName("stores 90 degrees plus minutes, which legacy never adds up")
    void storesMinutesPastNinety() {
      // 90° 25′ 42.2″. Legacy errors only on degrees above 90; minutes and seconds out of range are
      // warnings, and nothing checks the three together. So it stores 90.428389 — and so does CBR.
      service.create(site(draft -> draft.latitude = new BigDecimal("90.428389")));
      entityManager.flush();
      entityManager.clear();

      assertThat(sites.findById("BOWRON-001").orElseThrow().getLatitude())
          .isEqualByComparingTo("90.428389");
    }

    @Test
    @DisplayName("stores 180 degrees plus minutes of longitude, likewise")
    void storesMinutesPastOneEighty() {
      service.create(site(draft -> draft.longitude = new BigDecimal("-180.5")));
      entityManager.flush();
      entityManager.clear();

      assertThat(sites.findById("BOWRON-001").orElseThrow().getLongitude())
          .isEqualByComparingTo("-180.5");
    }
  }

  @Nested
  @DisplayName("a site that is refused")
  class Refused {

    @Test
    @DisplayName("names every field at fault at once, not just the first")
    void reportsEverythingAtOnce() {
      // Pressing Save six times to be told six things is being told the same thing six times.
      Map<String, String> errors = refusedWith(draft -> {
        draft.siteId = "";
        draft.crossingSiteStatusCode = "";
        draft.crossingSiteTypeCode = "";
        draft.structureInspectionStatusCode = "";
        draft.forestFileId = "";
        draft.roadSectionId = "";
        draft.crossingName = null;
        draft.pointOfCommencementDistance = null;
        draft.orgUnitNo = null;
        draft.longitude = null;
        draft.latitude = null;
      });

      assertThat(errors).containsKeys(
          "siteId",
          "crossingSiteStatusCode",
          "crossingSiteTypeCode",
          "structureInspectionStatusCode",
          "forestFileId",
          "crossingName",
          "pointOfCommencementDistance",
          "roadSectionId",
          "orgUnitNo",
          "longitude",
          "latitude");
    }

    @Test
    @DisplayName("refuses a number already in use")
    void refusesADuplicateSiteId() {
      service.create(valid());
      entityManager.flush();

      assertThat(refusedWith(draft -> { }))
          .containsEntry("siteId", "A site with this number already exists.");
    }

    @Test
    @DisplayName("catches a duplicate whatever case it arrives in")
    void refusesADuplicateInAnotherCase() {
      // The stored key is upper case, so a lower-case retry has to be folded before it is checked
      // — otherwise the answer is a constraint violation instead of a message.
      service.create(valid());
      entityManager.flush();

      assertThat(refusedWith(draft -> draft.siteId = "bowron-001")).containsKey("siteId");
    }

    @Test
    @DisplayName("refuses a positive longitude rather than silently flipping its sign")
    void refusesAPositiveLongitude() {
      // A positive longitude is a real place. Guessing that the caller meant its mirror image would
      // store a site on the wrong side of the world without telling anyone.
      assertThat(refusedWith(draft -> draft.longitude = new BigDecimal("122.5")))
          .containsKey("longitude");
    }

    @Test
    @DisplayName("refuses degrees outside the notation, as legacy does")
    void refusesImpossibleCoordinates() {
      assertThat(refusedWith(draft -> draft.longitude = new BigDecimal("-181")))
          .containsKey("longitude");
      assertThat(refusedWith(draft -> draft.latitude = new BigDecimal("91")))
          .containsKey("latitude");
    }

    @Test
    @DisplayName("refuses a kilometre value the NUMBER(8,2) column could not hold")
    void refusesAnUnstorableKilometre() {
      // ORA-01438 at insert time is a stack trace where the user deserves a sentence, and a value
      // Oracle merely rounds is a number they did not type being stored without being told.
      assertThat(refusedWith(draft -> draft.pointOfCommencementDistance = new BigDecimal("12.345")))
          .containsKey("pointOfCommencementDistance");
      assertThat(
          refusedWith(draft -> draft.pointOfCommencementDistance = new BigDecimal("1234567.00")))
          .containsKey("pointOfCommencementDistance");
    }

    @Test
    @DisplayName("refuses a code no table carries")
    void refusesAnUnknownCode() {
      assertThat(refusedWith(draft -> draft.crossingSiteStatusCode = "NOPE"))
          .containsKey("crossingSiteStatusCode");
    }

    @Test
    @DisplayName("refuses an org unit that does not exist")
    void refusesAnUnknownOrgUnit() {
      assertThat(refusedWith(draft -> draft.orgUnitNo = 999L)).containsKey("orgUnitNo");
    }

    @Test
    @DisplayName("refuses free text longer than its column")
    void refusesOverlongText() {
      assertThat(refusedWith(draft -> draft.crossingName = "x".repeat(256)))
          .containsKey("crossingName");
    }

    @Test
    @DisplayName("refuses an Active Crossing marked Do Not Inspect, on the Inspection Status box")
    void refusesAnUninspectedActiveCrossing() {
      // Legacy stamps fieldName="structureInspectionStatusCode" on this rule, so the form can mark
      // the box rather than print a paragraph.
      assertThat(refusedWith(draft -> draft.structureInspectionStatusCode = "DNI"))
          .containsKey("structureInspectionStatusCode");
    }

    @Test
    @DisplayName("refuses a Storage site marked Inspect")
    void refusesAnInspectedStorageSite() {
      assertThat(refusedWith(draft -> draft.crossingSiteTypeCode = "STRG"))
          .containsKey("structureInspectionStatusCode");
    }

    @Test
    @DisplayName("refuses a Project File ID# and Br. that name no road")
    void refusesAnUnresolvableRoad() {
      // Legacy states this as a rule about the hidden segment field, but what it tests is that the
      // section exists. The message lands on Project File ID#, which is the box the user can act on.
      assertThat(refusedWith(draft -> draft.roadSectionId = "99"))
          .containsEntry("forestFileId", "No road matches this Project File ID# and Br.");
    }

    @Test
    @DisplayName("stores nothing when it refuses")
    void storesNothingOnFailure() {
      refusedWith(draft -> draft.crossingSiteStatusCode = "NOPE");
      entityManager.flush();

      assertThat(sites.existsById("BOWRON-001")).isFalse();
    }
  }
}
