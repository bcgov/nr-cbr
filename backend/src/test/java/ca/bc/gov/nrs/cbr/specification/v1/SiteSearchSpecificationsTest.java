package ca.bc.gov.nrs.cbr.specification.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/**
 * The criteria-to-SQL translation, against a real database.
 *
 * <p>This is the test that matters most in the search path. The procedure being replaced builds its
 * {@code WHERE} clause as a string and binds values positionally, so its failure mode is not an
 * error but a wrong answer that looks right. A specification cannot make that particular mistake,
 * but it can still filter on the wrong column, use the wrong operator, or — the one that bites —
 * turn a left join into an inner one and silently drop rows.
 *
 * <p>H2 stands in for Oracle, so this proves the predicates and the joins, not Oracle's behaviour.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class SiteSearchSpecificationsTest {

  @Autowired
  private CrossingSiteRepository repository;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM CrossingSiteEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CbrRoadSectionEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM ClientPublicEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM OrgUnitEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingSiteStatusCodeEntity").executeUpdate();
  }

  /** A site with everything filled in. Individual tests override what they are about. */
  private CrossingSiteEntity.CrossingSiteEntityBuilder completeSite(String id) {
    return CrossingSiteEntity.builder()
        .crossingSiteId(id)
        .crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.50"))
        .userKm(new BigDecimal("13.00"))
        .crossingSiteStatusCode("ACT")
        .structureInspectionStatusCode("INS")
        .crossingSiteTypeCode("CRS")
        .specialAccessRqmtCode("HEL")
        .orgUnitNo(18L)
        .managementOrgUnitNo(26L)
        .roadSegmentId(1L)
        .forestFileId("R00123")
        .roadSectionId("01")
        .clientNumber("00001012")
        .clientLocnCode("00")
        .capitalRoadInd("N");
  }

  private void persist(CrossingSiteEntity site) {
    entityManager.persist(site);
  }

  private void givenStatus(String code, String description) {
    entityManager.persist(CrossingSiteStatusCodeEntity.builder()
        .crossingSiteStatusCode(code).description(description)
        .effectiveDate(LocalDateTime.now().minusYears(10))
        .expiryDate(LocalDateTime.now().plusYears(10))
        .updateTimestamp(LocalDateTime.now()).build());
  }

  /**
   * The rows a site points at. CROSSING_SITE has real foreign keys to ORG_UNIT and
   * CROSSING_SITE_STATUS_CODE, so a fixture inventing a code Oracle would reject is a fixture
   * testing something that cannot happen. The road section and client are views and have no such
   * constraint — a site may legitimately point at neither.
   */
  private void givenSupportingRows() {
    givenStatus("ACT", "Active");
    givenStatus("DEC", "Decommissioned");
    givenStatus("INACT", "Inactive");
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build());
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(21L).orgUnitCode("DKA").orgUnitName("Thompson Rivers").build());
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(26L).orgUnitCode("DRV").orgUnitName("Robson Valley").build());
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
    entityManager.persist(ClientPublicEntity.builder()
        .clientNumber("00001012").clientName("CANFOR CORPORATION").build());
  }

  private static SiteSearchCriteria empty() {
    return SiteSearchCriteria.builder().build();
  }


  /**
   * Detaches everything the fixtures persisted, so a search reads from the database.
   *
   * <p>Without it the query returns the very instances the test built, straight out of the
   * first-level cache — and those were constructed with their associations left null, because the
   * builder sets the scalar {@code ORG_UNIT_NO} rather than an {@code OrgUnitEntity}. Assertions on
   * a joined value would then see null and be testing the fixture rather than the mapping.
   */
  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private List<String> search(SiteSearchCriteria criteria) {
    flushAndClear();
    return repository
        .findAll(SiteSearchSpecifications.matching(criteria), PageRequest.of(0, 50))
        .map(CrossingSiteEntity::getCrossingSiteId)
        .getContent();
  }

  @Nested
  @DisplayName("with no criteria")
  class NoCriteria {

    @Test
    @DisplayName("returns every site — legacy runs unfiltered and has no scoping to narrow it")
    void returnsEverything() {
      givenSupportingRows();
      persist(completeSite("SITE-1").build());
      persist(completeSite("SITE-2").build());

      assertThat(search(empty())).containsExactlyInAnyOrder("SITE-1", "SITE-2");
    }

    @Test
    @DisplayName("includes sites whose joins are all empty")
    void includesSitesWithNoJoins() {
      // The join-type test. Every association here is a left join in legacy; a single inner join
      // would drop this row, and it would look like the filter working rather than a defect.
      persist(CrossingSiteEntity.builder()
          .crossingSiteId("ORPHAN").capitalRoadInd("N").build());

      assertThat(search(empty())).containsExactly("ORPHAN");
    }
  }

  @Nested
  @DisplayName("text criteria are a partial match, as legacy's Search.LIKE is")
  class TextCriteria {

    @Test
    @DisplayName("site id matches on a fragment")
    void siteIdIsPartial() {
      givenSupportingRows();
      persist(completeSite("12345-001").build());
      persist(completeSite("99999-001").build());

      assertThat(search(withSiteId("12345"))).containsExactly("12345-001");
    }

    @Test
    @DisplayName("crossing name matches on a fragment")
    void crossingNameIsPartial() {
      givenSupportingRows();
      persist(completeSite("SITE-1").crossingName("Deadman Creek").build());
      persist(completeSite("SITE-2").crossingName("Bowron River").build());

      assertThat(search(SiteSearchCriteria.builder().crossingName("man Cre").build()))
          .containsExactly("SITE-1");
    }

    private SiteSearchCriteria withSiteId(String siteId) {
      return SiteSearchCriteria.builder().siteId(siteId).build();
    }
  }

  @Nested
  @DisplayName("code criteria are an exact match")
  class CodeCriteria {

    @Test
    @DisplayName("status does not match a code that merely contains it")
    void statusIsExact() {
      givenSupportingRows();
      persist(completeSite("SITE-1").crossingSiteStatusCode("ACT").build());
      persist(completeSite("SITE-2").crossingSiteStatusCode("INACT").build());

      assertThat(search(SiteSearchCriteria.builder().siteStatusCode("ACT").build()))
          .containsExactly("SITE-1");
    }
  }

  @Nested
  @DisplayName("the kilometre bounds")
  class KilometreBounds {

    private SiteSearchCriteria kilometres(String from, String to) {
      return SiteSearchCriteria.builder().kiloStart(from).kiloEnd(to).build();
    }

    private SiteSearchCriteria userKilometres(String from, String to) {
      return SiteSearchCriteria.builder().userKmStart(from).userKmEnd(to).build();
    }

    @Test
    @DisplayName("a full range includes both ends")
    void rangeIsInclusive() {
      givenSupportingRows();
      persist(completeSite("AT-5").pointOfCommencementDistance(new BigDecimal("5.00")).build());
      persist(completeSite("AT-10").pointOfCommencementDistance(new BigDecimal("10.00")).build());
      persist(completeSite("AT-11").pointOfCommencementDistance(new BigDecimal("11.00")).build());

      assertThat(search(kilometres("5", "10"))).containsExactlyInAnyOrder("AT-5", "AT-10");
    }

    @Test
    @DisplayName("a lower bound alone includes its own value — legacy excludes it")
    void lowerBoundIsInclusive() {
      // The divergence. Legacy emits BETWEEN for a full range but ">" for a lone bound, so "from 5
      // to 10" finds a site at km 5 and "from 5" does not. Nothing depends on that asymmetry.
      givenSupportingRows();
      persist(completeSite("AT-5").pointOfCommencementDistance(new BigDecimal("5.00")).build());

      assertThat(search(kilometres("5", null))).containsExactly("AT-5");
    }

    @Test
    @DisplayName("an upper bound alone includes its own value")
    void upperBoundIsInclusive() {
      givenSupportingRows();
      persist(completeSite("AT-5").pointOfCommencementDistance(new BigDecimal("5.00")).build());

      assertThat(search(kilometres(null, "5"))).containsExactly("AT-5");
    }

    @Test
    @DisplayName("User Kilometres To works on its own — the legacy guard tests the wrong field")
    void userKilometresUpperBoundStandsAlone() {
      // Legacy guards this bound with `if (getKiloEnd() != null && ...)` while applying
      // getUserKmEnd(), so the box does nothing unless an unrelated one is filled.
      givenSupportingRows();
      persist(completeSite("LOW").userKm(new BigDecimal("5.00")).build());
      persist(completeSite("HIGH").userKm(new BigDecimal("50.00")).build());

      assertThat(search(userKilometres(null, "10"))).containsExactly("LOW");
    }

    @Test
    @DisplayName("filters the measured distance and the posted distance separately")
    void theTwoRangesAreDifferentColumns() {
      // POINT_OF_COMMENCEMENT_DISTANCE is the measured distance; USER_KM is the posted one, and
      // they routinely differ. Crossing the two would be invisible on any site where they agree.
      givenSupportingRows();
      persist(completeSite("SITE-1")
          .pointOfCommencementDistance(new BigDecimal("5.00"))
          .userKm(new BigDecimal("50.00")).build());

      assertThat(search(kilometres("4", "6"))).containsExactly("SITE-1");
      assertThat(search(userKilometres("4", "6"))).isEmpty();
    }
  }

  @Nested
  @DisplayName("the joined criteria")
  class JoinedCriteria {

    @Test
    @DisplayName("Forest Service Road matches through the road section view")
    void forestServiceRoad() {
      givenSupportingRows();
      persist(completeSite("ON-BOWRON").build());
      persist(completeSite("NO-ROAD").forestFileId("R99999").roadSectionId("99").build());

      assertThat(search(SiteSearchCriteria.builder().forestServiceRoad("Bowron").build()))
          .containsExactly("ON-BOWRON");
    }

    @Test
    @DisplayName("Designated Maintainer matches the client's name, not the client number")
    void designatedMaintainer() {
      givenSupportingRows();
      persist(completeSite("CANFOR").build());
      persist(completeSite("NO-CLIENT").clientNumber(null).build());

      assertThat(search(SiteSearchCriteria.builder().primaryUserName("CANFOR").build()))
          .containsExactly("CANFOR");
    }
  }

  @Nested
  @DisplayName("the two toggles")
  class Toggles {

    @Test
    @DisplayName("Capital Road filters one way only")
    void capitalRoadIsOneWay() {
      // Switched on means "capital roads only"; switched off means "do not filter", not "exclude
      // capital roads". Legacy adds the criterion only when the box is ticked.
      givenSupportingRows();
      persist(completeSite("CAPITAL").capitalRoadInd("Y").build());
      persist(completeSite("ORDINARY").capitalRoadInd("N").build());

      assertThat(search(SiteSearchCriteria.builder().capitalRoad(true).build()))
          .containsExactly("CAPITAL");
      assertThat(search(SiteSearchCriteria.builder().capitalRoad(false).build()))
          .containsExactlyInAnyOrder("CAPITAL", "ORDINARY");
    }

    @Test
    @DisplayName("Incomplete Data finds a site missing any required field")
    void incompleteFindsMissingFields() {
      givenSupportingRows();
      persist(completeSite("COMPLETE").build());
      persist(completeSite("NO-STATUS").crossingSiteStatusCode(null).build());
      persist(completeSite("NO-ORG").orgUnitNo(null).build());
      persist(completeSite("NO-TYPE").crossingSiteTypeCode(null).build());

      assertThat(search(incomplete()))
          .containsExactlyInAnyOrder("NO-STATUS", "NO-ORG", "NO-TYPE");
    }

    @Test
    @DisplayName("the road and location checks apply to crossings only")
    void incompleteIsTypeSensitive() {
      // The nested group is guarded on CROSSING_SITE_TYPE_CODE = 'CRS'. A site of another type
      // carries no road detail, so missing road detail does not make it incomplete — applying the
      // check to every type would report most of the inventory as incomplete.
      givenSupportingRows();
      persist(completeSite("CRS-NO-ROAD").crossingSiteTypeCode("CRS")
          .forestFileId(null).build());
      persist(completeSite("PRT-NO-ROAD").crossingSiteTypeCode("PRT")
          .forestFileId(null).build());

      assertThat(search(incomplete())).containsExactly("CRS-NO-ROAD");
    }

    private SiteSearchCriteria incomplete() {
      return SiteSearchCriteria.builder().incomplete(true).build();
    }
  }

  @Nested
  @DisplayName("combining criteria")
  class Combining {

    @Test
    @DisplayName("narrows with AND, never widens")
    void criteriaAreAnded() {
      givenSupportingRows();
      persist(completeSite("BOTH").crossingSiteStatusCode("ACT").orgUnitNo(18L).build());
      persist(completeSite("STATUS-ONLY").crossingSiteStatusCode("ACT").orgUnitNo(21L).build());
      persist(completeSite("ORG-ONLY").crossingSiteStatusCode("DEC").orgUnitNo(18L).build());

      SiteSearchCriteria criteria =
          SiteSearchCriteria.builder().siteStatusCode("ACT").orgUnit("18").build();

      assertThat(search(criteria)).containsExactly("BOTH");
    }

    @Test
    @DisplayName("a blank criterion is not a filter on empty")
    void blanksAreIgnored() {
      // "" and "   " reach the server from an untouched form field. Treating one as a value would
      // match nothing and look like a search that legitimately found no sites.
      givenSupportingRows();
      persist(completeSite("SITE-1").build());

      SiteSearchCriteria criteria = SiteSearchCriteria.builder()
          .siteId("").siteStatusCode("   ").forestFileId("").roadSectionId("")
          .structureInspectionStatusCode("").forestServiceRoad("").clientNumber("")
          .crossingName("").clientLocationCode("").orgUnit("").kiloStart("").kiloEnd("")
          .managementOrgUnit("").userKmStart("").userKmEnd("").specialAccessCode("")
          .siteTypeCode("").primaryUserName("  ").build();

      assertThat(search(criteria)).containsExactly("SITE-1");
    }
  }
}
