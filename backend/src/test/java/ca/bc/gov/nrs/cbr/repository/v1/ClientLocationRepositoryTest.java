package ca.bc.gov.nrs.cbr.repository.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.model.v1.ClientLocationEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * The Designated Maintainer lookup, against a real database.
 *
 * <p>What is worth proving here is the join, not the {@code LIKE}: the query reaches three tables
 * that have no mapped association between them, and its whole behaviour rests on starting at
 * {@code CROSSING_SITE} rather than at the client. A query that quietly stopped doing that would
 * still return plausible suggestions — just ones that lead nowhere.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class ClientLocationRepositoryTest {

  private static final Pageable LIMIT = PageRequest.ofSize(50);

  @Autowired
  private ClientLocationRepository repository;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM CrossingSiteEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM ClientLocationEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM ClientPublicEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM OrgUnitEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingSiteStatusCodeEntity").executeUpdate();
    givenSupportingRows();
  }

  /**
   * The rows a site points at. {@code CROSSING_SITE} has real foreign keys to {@code ORG_UNIT} and
   * {@code CROSSING_SITE_STATUS_CODE}; the client tables have none pointing this way, which is why
   * a site can name a client location that has since been removed.
   */
  private void givenSupportingRows() {
    entityManager.persist(CrossingSiteStatusCodeEntity.builder()
        .crossingSiteStatusCode("ACT").description("Active")
        .effectiveDate(LocalDateTime.now().minusYears(10))
        .expiryDate(LocalDateTime.now().plusYears(10))
        .updateTimestamp(LocalDateTime.now()).build());
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build());
  }

  private void givenClient(String number, String name) {
    entityManager.persist(
        ClientPublicEntity.builder().clientNumber(number).clientName(name).build());
  }

  private void givenLocation(String number, String code, String locationName, String city) {
    entityManager.persist(ClientLocationEntity.builder()
        .clientNumber(number).clientLocnCode(code)
        .clientLocnName(locationName).city(city).build());
  }

  /** A site naming a maintainer. The site's own columns are irrelevant beyond being valid. */
  private void givenSiteMaintainedBy(String siteId, String clientNumber, String locationCode) {
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId(siteId)
        .crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.50"))
        .crossingSiteStatusCode("ACT")
        .orgUnitNo(18L)
        .clientNumber(clientNumber)
        .clientLocnCode(locationCode)
        .capitalRoadInd("N")
        .build());
  }

  private List<ClientLookupResult> byText(String term) {
    entityManager.flush();
    entityManager.clear();
    return repository.findMaintainerClientsByText(term, LIMIT);
  }

  private List<ClientLookupResult> byNumber(String clientNumber) {
    entityManager.flush();
    entityManager.clear();
    return repository.findMaintainersByClientNumber(clientNumber, LIMIT);
  }

  @Test
  @DisplayName("suggests only clients some site actually names")
  void excludesClientsNoSiteUses() {
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "00", null, "Vancouver");
    givenClient("00002222", "CANFOR HOLDINGS LTD");
    givenLocation("00002222", "00", null, "Vancouver");
    // Only the first is designated on a site. The second is a perfectly good client that CBR has
    // never met, and suggesting it would offer a search that cannot match anything.
    givenSiteMaintainedBy("SITE-1", "00001012", "00");

    assertThat(byText("%CANFOR%"))
        .extracting(ClientLookupResult::clientNumber)
        .containsExactly("00001012");
  }

  @Test
  @DisplayName("excludes a location of a used client that no site names")
  void excludesUnusedLocationOfUsedClient() {
    // The discrimination is on the pair, not the client: CBR uses Canfor's Prince George office, so
    // Canfor is "in use" — but its Vancouver office still leads to no sites, and the Location
    // filter must not offer it. Asserted on the locations query, which is the only one that
    // projects a location at all since the client lookup was split from it.
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "00", null, "Vancouver");
    givenLocation("00001012", "01", "Northern Division", "Prince George");
    givenSiteMaintainedBy("SITE-1", "00001012", "01");
    entityManager.flush();

    assertThat(repository.findMaintainersByClientNumber("00001012", LIMIT))
        .extracting(ClientLookupResult::clientLocnCode)
        .containsExactly("01");
  }

  @Test
  @DisplayName("collapses the many sites sharing one maintainer into a single suggestion")
  void deduplicatesAcrossSites() {
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "00", null, "Vancouver");
    givenSiteMaintainedBy("SITE-1", "00001012", "00");
    givenSiteMaintainedBy("SITE-2", "00001012", "00");
    givenSiteMaintainedBy("SITE-3", "00001012", "00");

    assertThat(byText("%CANFOR%")).hasSize(1);
  }

  @Test
  @DisplayName("matches the client name whatever case the user typed")
  void matchesNameCaseInsensitively() {
    // The whole reason the query upper-cases both sides. CLIENT_NAME is stored upper-cased, so a
    // user typing "Canfor" matched nothing before this.
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "00", null, "Vancouver");
    givenSiteMaintainedBy("SITE-1", "00001012", "00");

    assertThat(byText("%CANFOR%")).hasSize(1);
    assertThat(byText("%CORPORATION%")).hasSize(1);
  }

  @Test
  @DisplayName("matches the division name and the city, which the suggestion also displays")
  void matchesLocationNameAndCity() {
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "01", "Northern Division", "Prince George");
    givenSiteMaintainedBy("SITE-1", "00001012", "01");

    // One row whichever column matched: a company with several matching offices is one choice.
    assertThat(byText("%NORTHERN%")).hasSize(1);
    assertThat(byText("%PRINCE GEORGE%")).hasSize(1);
    assertThat(byText("%CANFOR%")).hasSize(1);
    assertThat(byText("%NANAIMO%")).isEmpty();
  }

  @Test
  @DisplayName("matches a client number without dragging in numbers that merely contain it")
  void matchesClientNumberExactly() {
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "00", null, "Vancouver");
    givenSiteMaintainedBy("SITE-1", "00001012", "00");
    givenClient("00010120", "WEST FRASER MILLS LTD");
    givenLocation("00010120", "00", null, "Quesnel");
    givenSiteMaintainedBy("SITE-2", "00010120", "00");

    assertThat(byNumber("00001012"))
        .extracting(ClientLookupResult::clientName)
        .containsExactly("CANFOR CORPORATION");
  }

  @Test
  @DisplayName("orders by client name")
  void ordersByNameThenLocation() {
    // So that a truncated list loses the tail of the alphabet rather than an arbitrary slice.
    givenClient("00010120", "WEST FRASER MILLS LTD");
    givenLocation("00010120", "00", null, "Quesnel");
    givenSiteMaintainedBy("SITE-3", "00010120", "00");
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "01", "Northern Division", "Prince George");
    givenLocation("00001012", "00", null, "Vancouver");
    givenSiteMaintainedBy("SITE-1", "00001012", "01");
    givenSiteMaintainedBy("SITE-2", "00001012", "00");

    // "R" is in both client names — the point is the order they come back in, not the match.
    assertThat(byText("%R%"))
        .extracting(ClientLookupResult::clientName)
        .containsExactly("CANFOR CORPORATION", "WEST FRASER MILLS LTD");
  }

  @Test
  @DisplayName("stops at the limit it is given")
  void respectsTheLimit() {
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "00", null, "Vancouver");
    givenLocation("00001012", "01", "Northern Division", "Prince George");
    givenSiteMaintainedBy("SITE-1", "00001012", "00");
    givenSiteMaintainedBy("SITE-2", "00001012", "01");

    entityManager.flush();
    entityManager.clear();
    assertThat(repository.findMaintainerClientsByText("%CANFOR%", PageRequest.ofSize(1)))
        .hasSize(1);
  }

  @Test
  @DisplayName("lists a client once, however many of its locations maintain a site")
  void listsAClientOncePerName() {
    // The difference between the two families of query. The site form set both halves of the key
    // at once and wanted client-locations; Site Search filters on the client and on the location
    // separately, so a client maintaining sites at three locations is one choice there, not three.
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "00", "HEAD OFFICE", "VANCOUVER");
    givenLocation("00001012", "01", "PRINCE GEORGE", "PRINCE GEORGE");
    givenLocation("00001012", "02", "CHETWYND", "CHETWYND");
    givenSiteMaintainedBy("SITE-1", "00001012", "00");
    givenSiteMaintainedBy("SITE-2", "00001012", "01");
    givenSiteMaintainedBy("SITE-3", "00001012", "02");
    entityManager.flush();

    List<ClientLookupResult> found =
        repository.findMaintainerClientsByText("%CANFOR%", PageRequest.ofSize(10));

    assertThat(found).hasSize(1);
    assertThat(found.getFirst().clientNumber()).isEqualTo("00001012");
    assertThat(found.getFirst().clientName()).isEqualTo("CANFOR CORPORATION");
    // The location columns are absent by design — the browser's label guards for it.
    assertThat(found.getFirst().clientLocnCode()).isNull();
  }

  @Test
  @DisplayName("offers only clients that actually maintain something")
  void ignoresAClientThatMaintainsNothing() {
    givenClient("00009999", "CANFOR HOLDINGS");
    givenLocation("00009999", "00", "HEAD OFFICE", "VANCOUVER");
    entityManager.flush();

    assertThat(repository.findMaintainerClientsByText("%CANFOR%", PageRequest.ofSize(10))).isEmpty();
  }

  @ParameterizedTest(name = "typing \"{0}\" finds client 00001012")
  @ValueSource(strings = {"00001012", "1012", "0101", "000", "101"})
  @DisplayName("finds a maintainer client from any run of digits in its number")
  void findsAClientByAnyFragmentOfItsNumber(String typed) {
    // The bug this replaces: the number was zero-padded and matched exactly, so "1012" found this
    // client — the padding rebuilt the whole number — and "0101" did not, because it became
    // 00000101. Both are runs of digits out of 00001012.
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "00", "HEAD OFFICE", "VANCOUVER");
    givenSiteMaintainedBy("SITE-1", "00001012", "00");
    entityManager.flush();

    assertThat(repository.findMaintainerClientsByNumber("%" + typed + "%", PageRequest.ofSize(10)))
        .singleElement()
        .extracting(ClientLookupResult::clientName)
        .isEqualTo("CANFOR CORPORATION");
  }

  @Test
  @DisplayName("does not find a client whose number merely resembles the digits typed")
  void doesNotMatchADifferentNumber() {
    givenClient("00001012", "CANFOR CORPORATION");
    givenLocation("00001012", "00", "HEAD OFFICE", "VANCOUVER");
    givenSiteMaintainedBy("SITE-1", "00001012", "00");
    entityManager.flush();

    assertThat(repository.findMaintainerClientsByNumber("%9999%", PageRequest.ofSize(10))).isEmpty();
  }
}
