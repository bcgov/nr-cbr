package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchResult;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Paging, ordering and the mapping to a results row.
 *
 * <p>Against the database rather than a mocked repository, because the two things worth proving here
 * — that the legacy sort survives, and that a site with empty joins still maps — are properties of
 * the generated SQL, not of this class's arithmetic.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(SiteSearchService.class)
class SiteSearchServiceTest {

  @Autowired
  private SiteSearchService service;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM CrossingSiteEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CbrRoadSectionEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM OrgUnitEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingSiteStatusCodeEntity").executeUpdate();
  }

  private void givenDistrict(long orgUnitNo, String code, String name) {
    entityManager.persist(
        OrgUnitEntity.builder().orgUnitNo(orgUnitNo).orgUnitCode(code).orgUnitName(name).build());
  }

  private void givenSite(String id, Long orgUnitNo, String roadSectionId, String km) {
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId(id)
        .orgUnitNo(orgUnitNo)
        .forestFileId("R00123")
        .roadSectionId(roadSectionId)
        .pointOfCommencementDistance(km == null ? null : new BigDecimal(km))
        .capitalRoadInd("N")
        .build());
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

  private PagedResponse<SiteSearchResult> searchAll(int pageNumber, int pageSize) {
    flushAndClear();
    return service.search(SiteSearchCriteria.builder().build(), pageNumber, pageSize);
  }

  @Test
  @DisplayName("orders by district code, then road, then section, then kilometre — as legacy does")
  void ordersAsLegacyDoes() {
    givenDistrict(18L, "DPG", "Prince George");
    givenDistrict(21L, "DKA", "Thompson Rivers");
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
    givenSite("DPG-01-20", 18L, "01", "20.0");
    givenSite("DPG-01-10", 18L, "01", "10.0");
    givenSite("DKA-01-99", 21L, "01", "99.0");

    assertThat(searchAll(0, 20).content())
        .extracting(SiteSearchResult::id)
        .containsExactly("DKA-01-99", "DPG-01-10", "DPG-01-20");
  }

  @Test
  @DisplayName("keeps sites with no org unit, which the sort joins must not drop")
  void sortDoesNotDropUnjoinedSites() {
    // The trap this design exists to avoid: two of the four sort keys live on joined tables, and a
    // Pageable's Sort resolves them with an inner join — which would remove exactly the sites the
    // "Incomplete Data?" filter is meant to surface. The ordering is applied inside the
    // specification so the join type can be stated.
    givenDistrict(18L, "DPG", "Prince George");
    givenSite("HAS-ORG", 18L, "01", "1.0");
    givenSite("NO-ORG", null, null, null);

    assertThat(searchAll(0, 20).content())
        .extracting(SiteSearchResult::id)
        .containsExactlyInAnyOrder("HAS-ORG", "NO-ORG");
  }

  @Test
  @DisplayName("maps a site with no joins at all rather than failing the page")
  void mapsSitesWithEmptyJoins() {
    givenSite("ORPHAN", null, null, null);

    assertThat(searchAll(0, 20).content()).singleElement().satisfies(result -> {
      assertThat(result.id()).isEqualTo("ORPHAN");
      assertThat(result.orgUnitCode()).isNull();
      assertThat(result.crossingSiteStatusDescription()).isNull();
      assertThat(result.forestServiceRoad()).isNull();
    });
  }

  @Test
  @DisplayName("maps the joined values a results row shows")
  void mapsJoinedValues() {
    givenDistrict(18L, "DPG", "Prince George");
    entityManager.persist(CrossingSiteStatusCodeEntity.builder()
        .crossingSiteStatusCode("ACT").description("Active")
        .effectiveDate(LocalDateTime.now().minusYears(1))
        .expiryDate(LocalDateTime.now().plusYears(1))
        .updateTimestamp(LocalDateTime.now()).build());
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId("SITE-1").orgUnitNo(18L).crossingSiteStatusCode("ACT")
        .forestFileId("R00123").roadSectionId("01").crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.50")).capitalRoadInd("N").build());

    assertThat(searchAll(0, 20).content()).singleElement().satisfies(result -> {
      assertThat(result.orgUnitCode()).isEqualTo("DPG");
      assertThat(result.orgUnitName()).isEqualTo("Prince George");
      assertThat(result.forestServiceRoad()).isEqualTo("Bowron FSR");
      // Code and description both travel: the description is the label, the code decides the
      // colour of the pill it sits in.
      assertThat(result.crossingSiteStatusCode()).isEqualTo("ACT");
      assertThat(result.crossingSiteStatusDescription()).isEqualTo("Active");
      // Keeps the column's own scale: "12.50", not "12.5" and not "1.25E+1".
      assertThat(result.pointOfCommencementDistance()).isEqualTo("12.50");
    });
  }

  @Test
  @DisplayName("reports the true total, not the size of the page")
  void totalIsTheWholeResultSet() {
    givenDistrict(18L, "DPG", "Prince George");
    for (int i = 0; i < 5; i++) {
      givenSite("SITE-" + i, 18L, "0" + i, String.valueOf(i));
    }

    PagedResponse<SiteSearchResult> page = searchAll(0, 2);

    assertThat(page.content()).hasSize(2);
    assertThat(page.totalElements()).isEqualTo(5);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.pageNumber()).isZero();
  }

  @Test
  @DisplayName("caps the page size, so one request cannot ask for the province")
  void capsPageSize() {
    assertThat(searchAll(0, 100_000).pageSize()).isEqualTo(200);
  }

  @Test
  @DisplayName("falls back to a sane page size and page rather than failing on nonsense")
  void toleratesNonsensePaging() {
    // These arrive as query parameters. A negative page is a 500 from PageRequest otherwise.
    assertThat(searchAll(-1, 0).pageSize()).isEqualTo(20);
    assertThat(searchAll(-1, 0).pageNumber()).isZero();
  }
}
