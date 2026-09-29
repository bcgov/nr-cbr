package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.cbr.exception.RoadSectionNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSegmentEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestFileClientEntity;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult;
import jakarta.persistence.EntityManager;
import java.util.List;
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
 * The road-section lookup behind "Forest Service Road".
 *
 * <p>Against the database, because the whole of this is a keyed read on a two-column key and what
 * is worth proving is that the key is the right pair — which a mocked repository would assert of
 * itself.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(RoadSectionService.class)
class RoadSectionServiceTest {

  @Autowired
  private RoadSectionService service;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void clear() {
    entityManager.createQuery("DELETE FROM CbrRoadSegmentEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM CbrRoadSectionEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM ForestFileClientEntity").executeUpdate();
    entityManager.createQuery("DELETE FROM ClientPublicEntity").executeUpdate();
  }

  private void givenRoadSection(String forestFileId, String roadSectionId, String name) {
    givenRoadSection(forestFileId, roadSectionId, name, "B40");
  }

  /** A section with one segment — a road a site can be put on. */
  private void givenRoadSection(
      String forestFileId, String roadSectionId, String name, String tenureType) {
    givenSectionWithoutSegment(forestFileId, roadSectionId, name, tenureType);
    entityManager.persist(CbrRoadSegmentEntity.builder()
        .forestFileId(forestFileId).roadSectionId(roadSectionId).roadSegmentId(1L).build());
    entityManager.flush();
    entityManager.clear();
  }

  private void givenSectionWithoutSegment(
      String forestFileId, String roadSectionId, String name, String tenureType) {
    entityManager.persist(CbrRoadSectionEntity.builder()
        .forestFileId(forestFileId).roadSectionId(roadSectionId).roadSectName(name)
        .fileTypeCode(tenureType).build());
    entityManager.flush();
    entityManager.clear();
  }

  private void givenFileHeldBy(long skey, String forestFileId, String number, String name) {
    entityManager.persist(
        ClientPublicEntity.builder().clientNumber(number).clientName(name).build());
    entityManager.persist(ForestFileClientEntity.builder()
        .forestFileClientSkey(skey).forestFileId(forestFileId).clientNumber(number).build());
    entityManager.flush();
    entityManager.clear();
  }

  private List<RoadSearchResult> search(RoadSearchCriteria criteria) {
    return page(criteria, 0, 200).content();
  }

  private PagedResponse<RoadSearchResult> page(
      RoadSearchCriteria criteria, int pageNumber, int pageSize) {
    entityManager.flush();
    entityManager.clear();
    return service.search(criteria, pageNumber, pageSize);
  }

  @Test
  @DisplayName("answers with the section's name")
  void findsTheSection() {
    givenRoadSection("R00123", "01", "Bowron FSR");

    assertThat(service.find("R00123", "01").forestServiceRoad()).isEqualTo("Bowron FSR");
  }

  @Test
  @DisplayName("is keyed on both halves, not on the file alone")
  void needsBothHalves() {
    // Project File ID# alone is a road file; a file has many sections and they are different roads.
    givenRoadSection("R00123", "01", "Bowron FSR");
    givenRoadSection("R00123", "02", "Deadman Spur");

    assertThat(service.find("R00123", "02").forestServiceRoad()).isEqualTo("Deadman Spur");
  }

  @Test
  @DisplayName("ignores the spaces around what was typed")
  void trimsTheKey() {
    givenRoadSection("R00123", "01", "Bowron FSR");

    assertThat(service.find("  R00123  ", " 01 ").forestServiceRoad()).isEqualTo("Bowron FSR");
  }

  @Test
  @DisplayName("answers a section with no name of its own without inventing one")
  void toleratesAnUnnamedSection() {
    // The view carries whatever is upstream, and upstream allows a blank name.
    givenRoadSection("R00123", "01", null);

    assertThat(service.find("R00123", "01").forestServiceRoad()).isNull();
  }

  @Test
  @DisplayName("refuses a pair the view does not carry, with a 404")
  void refusesAnUnknownPair() {
    // The ordinary case: the form asks on every keystroke, so most of what it asks about is a pair
    // half typed. It is also the only answer where the materialized view is stubbed.
    assertThatThrownBy(() -> service.find("R00123", "01"))
        .isInstanceOf(RoadSectionNotFoundException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("refuses a section with no road segment, as legacy and the save both do")
  void refusesASectionWithNoSegment() {
    // R08317 / I in TEST: the section view names it, the segment view has nothing for it. Legacy
    // shows no road name and calls the pair invalid; the save refuses it for want of a segment.
    // Answering with the name here showed a road the save would then turn down.
    givenSectionWithoutSegment("R08317", "I", "AM 4", "B40");

    assertThatThrownBy(() -> service.find("R08317", "I"))
        .isInstanceOf(RoadSectionNotFoundException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("refuses a half-empty pair without going to the database")
  void refusesAnIncompletePair() {
    assertThatThrownBy(() -> service.find("R00123", ""))
        .isInstanceOf(RoadSectionNotFoundException.class);
    assertThatThrownBy(() -> service.find("", "01"))
        .isInstanceOf(RoadSectionNotFoundException.class);
    assertThatThrownBy(() -> service.find(null, null))
        .isInstanceOf(RoadSectionNotFoundException.class);
  }

  @Nested
  @DisplayName("the lookup dialog's search")
  class Search {

    @Test
    @DisplayName("returns everything when nothing was asked")
    void blankReturnsAll() {
      // Legacy's dialog submits empty and shows the first page of everything; a blank criterion is
      // dropped rather than matched against the empty string.
      givenRoadSection("R00123", "01", "Bowron FSR");
      givenRoadSection("R00999", "02", "Deadman Spur");

      assertThat(search(RoadSearchCriteria.builder().build())).hasSize(2);
    }

    @Test
    @DisplayName("matches a road name on a fragment, whatever case was typed")
    void matchesTheRoadName() {
      givenRoadSection("R00123", "01", "Bowron FSR");
      givenRoadSection("R00999", "02", "Deadman Spur");

      assertThat(search(RoadSearchCriteria.builder().forestServiceRoad("bowron").build()))
          .extracting(RoadSearchResult::forestFileId)
          .containsExactly("R00123");
    }

    @Test
    @DisplayName("matches the file, the section and the tenure type")
    void matchesTheOtherRoadCriteria() {
      givenRoadSection("R00123", "01", "Bowron FSR", "B40");
      givenRoadSection("R00999", "02", "Deadman Spur", "B01");

      assertThat(search(RoadSearchCriteria.builder().forestFileId("00123").build())).hasSize(1);
      assertThat(search(RoadSearchCriteria.builder().roadSectionId("02").build())).hasSize(1);
      assertThat(search(RoadSearchCriteria.builder().tenureType("b01").build()))
          .extracting(RoadSearchResult::forestServiceRoad)
          .containsExactly("Deadman Spur");
    }

    @Test
    @DisplayName("names whoever holds the file")
    void namesTheFileHolder() {
      givenRoadSection("R00123", "01", "Bowron FSR");
      givenFileHeldBy(1L, "R00123", "00001012", "CANFOR CORPORATION");

      assertThat(search(RoadSearchCriteria.builder().build()))
          .singleElement()
          .satisfies(road -> {
            assertThat(road.clientName()).isEqualTo("CANFOR CORPORATION");
            assertThat(road.clientNumber()).isEqualTo("00001012");
          });
    }

    @Test
    @DisplayName("matches on the client too")
    void matchesTheClient() {
      givenRoadSection("R00123", "01", "Bowron FSR");
      givenFileHeldBy(1L, "R00123", "00001012", "CANFOR CORPORATION");
      givenRoadSection("R00999", "02", "Deadman Spur");

      assertThat(search(RoadSearchCriteria.builder().clientName("canfor").build()))
          .extracting(RoadSearchResult::forestFileId)
          .containsExactly("R00123");
      assertThat(search(RoadSearchCriteria.builder().clientNumber("00001012").build()))
          .hasSize(1);
    }

    @Test
    @DisplayName("keeps a road whose file nobody holds")
    void keepsAnUnheldRoad() {
      // The whole point of the outer joins. A road is still a road without a tenure holder, and an
      // inner join would hide the very section the user came here to find.
      givenRoadSection("R00123", "01", "Bowron FSR");

      assertThat(search(RoadSearchCriteria.builder().build()))
          .singleElement()
          .satisfies(road -> assertThat(road.clientName()).isNull());
    }

    @Test
    @DisplayName("keeps a road with no name of its own when no name was asked for")
    void keepsAnUnnamedRoad() {
      // Null rather than "%%" for a blank criterion: a wildcard pair would exclude every row whose
      // column is null, so an unnamed road would vanish from a search that named no road.
      givenRoadSection("R00123", "01", null);

      assertThat(search(RoadSearchCriteria.builder().build())).hasSize(1);
    }
  }

  @Nested
  @DisplayName("paging")
  class Paging {

    @Test
    @DisplayName("answers one page, in order, with the true total")
    void answersOnePage() {
      for (int i = 1; i <= 7; i++) {
        givenRoadSection("R0010" + i, "01", "Road " + i);
      }

      PagedResponse<RoadSearchResult> second = page(RoadSearchCriteria.builder().build(), 1, 3);

      assertThat(second.content())
          .extracting(RoadSearchResult::forestServiceRoad)
          .containsExactly("Road 4", "Road 5", "Road 6");
      assertThat(second.totalElements()).isEqualTo(7);
      assertThat(second.totalPages()).isEqualTo(3);
      assertThat(second.pageNumber()).isEqualTo(1);
      assertThat(second.pageSize()).isEqualTo(3);
    }

    @Test
    @DisplayName("reaches past the two hundred legacy stopped at")
    void hasNoCap() {
      for (int i = 0; i < 205; i++) {
        givenRoadSection(String.format("R%05d", i), "01", String.format("Road %03d", i));
      }

      PagedResponse<RoadSearchResult> last = page(RoadSearchCriteria.builder().build(), 13, 15);

      assertThat(last.totalElements()).isEqualTo(205);
      assertThat(last.content())
          .extracting(RoadSearchResult::forestServiceRoad)
          .containsExactly("Road 195", "Road 196", "Road 197", "Road 198", "Road 199",
              "Road 200", "Road 201", "Road 202", "Road 203", "Road 204");
    }

    @Test
    @DisplayName("counts the rows it shows, not the rows the joins produced")
    void countsDistinctRows() {
      // One client holding the same file twice — a second FOREST_FILE_CLIENT row, say under
      // another client type — is one row after DISTINCT. A count taken before it would promise a
      // row the pages never show.
      givenRoadSection("R00123", "01", "Bowron FSR");
      givenFileHeldBy(1L, "R00123", "00001012", "CANFOR CORPORATION");
      entityManager.persist(ForestFileClientEntity.builder()
          .forestFileClientSkey(2L).forestFileId("R00123").clientNumber("00001012").build());

      PagedResponse<RoadSearchResult> answer = page(RoadSearchCriteria.builder().build(), 0, 15);

      assertThat(answer.content()).hasSize(1);
      assertThat(answer.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("bounds a page at two hundred rows, and falls back to fifteen when none is asked")
    void boundsThePageSize() {
      givenRoadSection("R00123", "01", "Bowron FSR");

      assertThat(page(RoadSearchCriteria.builder().build(), 0, 5000).pageSize()).isEqualTo(200);
      assertThat(page(RoadSearchCriteria.builder().build(), 0, 0).pageSize()).isEqualTo(15);
      assertThat(page(RoadSearchCriteria.builder().build(), -1, 15).pageNumber()).isZero();
    }
  }

  @Nested
  @DisplayName("when the tenure holder cannot be read")
  class WithoutTheGrant {

    /**
     * CBR is not granted {@code FOREST_FILE_CLIENT}, so the join that names the holder fails with
     * {@code ORA-00942} — and took the whole dialog with it, answering none of the six criteria
     * because two of them needed a table the other four did not.
     */
    @Test
    @DisplayName("still answers the four criteria that do not need it")
    void fallsBackToTheRoadsAlone() {
      givenRoadSection("R00123", "01", "Bowron FSR", "B40");
      givenRoadSection("R00999", "02", "Deadman Spur", "B01");

      List<RoadSearchResult> roads = service
          .searchWithoutTenureHolder(RoadSearchCriteria.builder().tenureType("B40").build(), 0, 15)
          .content();

      assertThat(roads)
          .singleElement()
          .satisfies(road -> {
            assertThat(road.forestServiceRoad()).isEqualTo("Bowron FSR");
            assertThat(road.clientName()).isNull();
            assertThat(road.clientNumber()).isNull();
          });
    }

    @Test
    @DisplayName("drops a client criterion rather than applying it to nothing")
    void ignoresTheClientCriteria() {
      // The honest half of the trade. A criterion that cannot be applied must not silently narrow
      // nothing — a user who typed a client name would otherwise get an empty list, which reads
      // as "no such road" rather than "cannot search on that here".
      givenRoadSection("R00123", "01", "Bowron FSR");

      List<RoadSearchResult> roads = service
          .searchWithoutTenureHolder(
              RoadSearchCriteria.builder().clientName("canfor").build(), 0, 15)
          .content();

      assertThat(roads).hasSize(1);
    }

    @Test
    @DisplayName("pages and counts the same way")
    void pagesToo() {
      for (int i = 1; i <= 4; i++) {
        givenRoadSection("R0010" + i, "01", "Road " + i);
      }

      PagedResponse<RoadSearchResult> first =
          service.searchWithoutTenureHolder(RoadSearchCriteria.builder().build(), 0, 3);

      assertThat(first.content()).hasSize(3);
      assertThat(first.totalElements()).isEqualTo(4);
    }
  }
}
