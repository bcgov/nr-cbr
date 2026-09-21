package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.cbr.exception.SiteInUseException;
import ca.bc.gov.nrs.cbr.exception.SiteNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CloseProximityInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CloseProximityInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteRepository;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
  private EntityManager entityManager;

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM CloseProximityInspectionEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingStructureEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CrossingSiteEntity").executeUpdate();
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
}
