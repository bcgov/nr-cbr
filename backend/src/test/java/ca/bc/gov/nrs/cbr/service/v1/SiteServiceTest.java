package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.cbr.exception.SiteInUseException;
import ca.bc.gov.nrs.cbr.exception.SiteNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CloseProximityInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientLocationEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CloseProximityInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.struct.v1.SiteDetailResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deleting a site, and the four ways it can refuse.
 *
 * <p>Against the database rather than mocks: the delete is irreversible and the guards are the whole
 * feature, so what matters is that the row is actually gone when it should be and actually still
 * there when it should not be — which a mocked repository cannot tell you.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(SiteService.class)
class SiteServiceTest {

  @Autowired
  private SiteService service;

  @Autowired
  private CrossingSiteRepository sites;

  @Autowired
  private CrossingStructureRepository structures;

  @Autowired
  private CloseProximityInspectionRepository inspections;

  @Autowired
  private ClientLocationRepository clientLocations;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM CloseProximityInspectionEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingStructureEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingSiteEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM ClientLocationEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM ClientPublicEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM OrgUnitEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingSiteStatusCodeEntity").executeUpdate();
  }

  private void givenSite(String siteId) {
    entityManager.persist(
        CrossingSiteEntity.builder().crossingSiteId(siteId).capitalRoadInd("N").build());
  }

  private void givenStructure(long id, String siteId, String activeInd) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingSiteId(siteId).activeInd(activeInd).build());
  }

  private void givenCloseProximityInspection(long id, String siteId) {
    entityManager.persist(CloseProximityInspectionEntity.builder()
        .closeProximityInspectionId(id).crossingSiteId(siteId).build());
  }

  /**
   * The rows a complete site points at. CROSSING_SITE has real foreign keys to ORG_UNIT and
   * CROSSING_SITE_STATUS_CODE, so a fixture inventing either is a fixture testing what cannot
   * happen.
   */
  private void givenSupportingRows() {
    entityManager.persist(CrossingSiteStatusCodeEntity.builder()
        .crossingSiteStatusCode("ACT").description("Active")
        .effectiveDate(LocalDateTime.now().minusYears(10))
        .expiryDate(LocalDateTime.now().plusYears(10))
        .updateTimestamp(LocalDateTime.now()).build());
    for (long orgUnitNo : new long[] {18L, 26L, 31L}) {
      entityManager.persist(OrgUnitEntity.builder()
          .orgUnitNo(orgUnitNo).orgUnitCode("U" + orgUnitNo).orgUnitName("Unit " + orgUnitNo)
          .build());
    }
  }

  /** A site with the detail screen's fields filled in. */
  private void givenCompleteSite(String siteId) {
    givenSupportingRows();
    entityManager.persist(CrossingSiteEntity.builder()
        .crossingSiteId(siteId)
        .crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.50"))
        .userKm(new BigDecimal("13.00"))
        .crossingSiteStatusCode("ACT")
        .structureInspectionStatusCode("INS")
        .crossingSiteTypeCode("CRS")
        .specialAccessRqmtCode("HEL")
        .orgUnitNo(18L)
        .managementOrgUnitNo(26L)
        .businessAreaOrgUnitNo(31L)
        .forestFileId("R00123")
        .roadSectionId("01")
        .clientNumber("00001012")
        .clientLocnCode("01")
        .capitalRoadInd("Y")
        .longitude(new BigDecimal("-122.504306"))
        .latitude(new BigDecimal("53.916667"))
        .utmZone(10)
        .utmEasting(532000L)
        .utmNorthing(5975000L)
        .pointOfAccessDesc("Helicopter required to reach the cove.")
        .ntsMapSheetNumber("92P/10")
        .trimMapSheetNumber("093G025")
        .build());
  }

  private void givenMaintainer(String number, String code, String name, String city) {
    entityManager.persist(
        ClientPublicEntity.builder().clientNumber(number).clientName(name).build());
    entityManager.persist(ClientLocationEntity.builder()
        .clientNumber(number).clientLocnCode(code).city(city).build());
  }

  @Test
  @DisplayName("deletes a site that nothing depends on")
  void deletesAnUnreferencedSite() {
    givenSite("SITE-1");

    service.delete("SITE-1");

    assertThat(sites.existsById("SITE-1")).isFalse();
  }

  @Test
  @DisplayName("404s for a site that is already gone")
  void rejectsAnUnknownSite() {
    // A live case, not a theoretical one: the delete is reached from a results table that may have
    // been on screen a while, and someone else may have deleted the same site in between.
    assertThatThrownBy(() -> service.delete("NOT-THERE"))
        .isInstanceOf(SiteNotFoundException.class)
        .satisfies(thrown -> assertThat(((ResponseStatusException) thrown).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  @DisplayName("refuses a site with active structures, and leaves it standing")
  void refusesActiveStructures() {
    givenSite("SITE-1");
    givenStructure(1L, "SITE-1", "Y");

    assertThatThrownBy(() -> service.delete("SITE-1"))
        .isInstanceOf(SiteInUseException.class)
        .hasMessageContaining("associated structure(s)");

    assertThat(sites.existsById("SITE-1")).isTrue();
  }

  @Test
  @DisplayName("refuses a site whose only structures are archived, and says so")
  void refusesArchivedStructures() {
    // Archiving is a change of state, not a deletion — the inspection history hangs off the
    // archived row. The user cannot see these from the search results, so a message that did not
    // distinguish them would leave a site with no visible structures refusing to delete for no
    // stated reason.
    givenSite("SITE-1");
    givenStructure(1L, "SITE-1", "N");

    assertThatThrownBy(() -> service.delete("SITE-1"))
        .isInstanceOf(SiteInUseException.class)
        .hasMessageContaining("archived structure(s)");

    assertThat(sites.existsById("SITE-1")).isTrue();
  }

  @Test
  @DisplayName("refuses a site with a close proximity inspection — which legacy does not check")
  void refusesCloseProximityInspections() {
    // The legacy gap. SiteSearchAction.delete tests the structures and stops, so in legacy this
    // site passes every guard and fails in the database with ORA-02292.
    givenSite("SITE-1");
    givenCloseProximityInspection(1L, "SITE-1");

    assertThatThrownBy(() -> service.delete("SITE-1"))
        .isInstanceOf(SiteInUseException.class)
        .hasMessageContaining("close proximity inspection(s)");

    assertThat(sites.existsById("SITE-1")).isTrue();
  }

  @Test
  @DisplayName("a 409 is a conflict, not a bad request")
  void refusalIsAConflict() {
    // The request is well formed and the caller is entitled to make it; the state of the data is
    // what refuses. Told 400, a user would go looking for something wrong with what they typed.
    givenSite("SITE-1");
    givenStructure(1L, "SITE-1", "Y");

    assertThatThrownBy(() -> service.delete("SITE-1"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
            ResponseStatusException.class))
        .extracting(ResponseStatusException::getStatusCode)
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("counts only the site being deleted, not its neighbours")
  void countsAreScopedToTheSite() {
    // countBy... on the wrong column, or a missing site-id filter, would refuse every delete as
    // soon as any structure existed anywhere — and look like a working guard.
    givenSite("SITE-1");
    givenSite("SITE-2");
    givenStructure(1L, "SITE-2", "Y");
    givenCloseProximityInspection(1L, "SITE-2");

    service.delete("SITE-1");

    assertThat(sites.existsById("SITE-1")).isFalse();
    assertThat(sites.existsById("SITE-2")).isTrue();
    assertThat(structures.count()).isEqualTo(1);
    assertThat(inspections.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("leaves the children of other sites alone")
  void doesNotTouchOtherRows() {
    givenSite("SITE-1");
    // SITE-2 has to exist: CRS_CS_FK is a real foreign key in Oracle, and it became one here too
    // once CrossingStructureEntity mapped the site as an association for Inspection Search.
    givenSite("SITE-2");
    givenStructure(1L, "SITE-2", "Y");

    service.delete("SITE-1");

    assertThat(structures.findById(1L)).isPresent();
  }

  @Nested
  @DisplayName("reading one site")
  class FindById {

    @Test
    @DisplayName("returns the stored values, codes and org units included")
    void returnsTheRow() {
      // Codes as codes and org units as numbers: the screen holds every code table it needs and is
      // the only thing that knows how they should read.
      givenCompleteSite("SITE-1");
      entityManager.flush();
      entityManager.clear();

      SiteDetailResponse site = service.findById("SITE-1");

      assertThat(site.siteId()).isEqualTo("SITE-1");
      assertThat(site.crossingName()).isEqualTo("Deadman Creek");
      assertThat(site.crossingSiteStatusCode()).isEqualTo("ACT");
      assertThat(site.orgUnitNo()).isEqualTo(18L);
      assertThat(site.businessAreaOrgUnitNo()).isEqualTo(31L);
      assertThat(site.ntsMapSheetNumber()).isEqualTo("92P/10");
      assertThat(site.pointOfAccessDescription()).isEqualTo("Helicopter required to reach the cove.");
    }

    @Test
    @DisplayName("keeps the coordinates as the columns hold them, longitude negative")
    void returnsDecimalDegrees() {
      // The form enters degrees/minutes/seconds unsigned and converts at that edge, which is the
      // split legacy makes too. Signing it here would be a second opinion on the same value.
      givenCompleteSite("SITE-1");
      entityManager.flush();
      entityManager.clear();

      SiteDetailResponse site = service.findById("SITE-1");

      assertThat(site.longitude()).isEqualByComparingTo("-122.504306");
      assertThat(site.latitude()).isEqualByComparingTo("53.916667");
      assertThat(site.utmZone()).isEqualTo(10);
    }

    @Test
    @DisplayName("turns the capital road indicator into a boolean")
    void decodesCapitalRoad() {
      // 'Y'/'N' is how Oracle stores a flag, not something a screen should be asked to interpret.
      givenCompleteSite("SITE-1");
      givenSite("SITE-2");
      entityManager.flush();
      entityManager.clear();

      assertThat(service.findById("SITE-1").capitalRoad()).isTrue();
      assertThat(service.findById("SITE-2").capitalRoad()).isFalse();
    }

    @Test
    @DisplayName("names the maintainer, which is a second table the screen would not otherwise read")
    void resolvesTheMaintainer() {
      // Legacy fetches the same thing with its own getClientDetails() call on page load.
      givenCompleteSite("SITE-1");
      givenMaintainer("00001012", "01", "CANFOR CORPORATION", "Prince George");
      entityManager.flush();
      entityManager.clear();

      assertThat(service.findById("SITE-1").maintainerLabel())
          .isEqualTo("CANFOR CORPORATION \u00b7 Prince George \u00b7 00001012-01");
    }

    @Test
    @DisplayName("says nothing rather than an empty label when the pair resolves to no client")
    void toleratesAMissingClient() {
      // A site may name a client location that has since been removed. Nothing constrains it —
      // CROSSING_SITE's foreign key is to CLIENT_LOCATION, which the view does not police.
      givenCompleteSite("SITE-1");
      entityManager.flush();
      entityManager.clear();

      assertThat(service.findById("SITE-1").maintainerLabel()).isNull();
    }

    @Test
    @DisplayName("says nothing when the site names no maintainer at all")
    void toleratesNoMaintainer() {
      givenSite("SITE-2");
      entityManager.flush();
      entityManager.clear();

      assertThat(service.findById("SITE-2").maintainerLabel()).isNull();
    }

    @Test
    @DisplayName("refuses a site that is not there")
    void refusesAMissingSite() {
      // The link here comes from a results table that may have been open for some time, so a site
      // deleted in the meantime is an ordinary case rather than a broken link.
      assertThatThrownBy(() -> service.findById("NOPE"))
          .isInstanceOf(SiteNotFoundException.class)
          .extracting(error -> ((ResponseStatusException) error).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
  }
}
