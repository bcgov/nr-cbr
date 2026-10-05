package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureTypeClassCodeEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Structure Search, against the database — the rules are joins, subqueries and null checks, and a
 * mocked repository would only assert what it was told.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({StructureSearchService.class, StructureDeleteBlockers.class})
class StructureSearchServiceTest {

  @Autowired
  private StructureSearchService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  @BeforeEach
  void setUp() {
    for (String entity : List.of("StructureInspectionEntity", "StructureRepairEntity",
        "ForestServiceBridgeEntity", "ForestServiceCulvertEntity",
        "CrossingStructureEntity", "CrossingSiteEntity", "CbrRoadSectionEntity", "OrgUnitEntity",
        "ClientPublicEntity", "StructureTypeClassCodeEntity", "CrossingSiteStatusCodeEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
    for (String[] type : new String[][] {
        {"TB", "Timber Bridge"}, {"PB", "Portable Bridge"}, {"CUL", "Culvert"}}) {
      entityManager.persist(StructureTypeClassCodeEntity.builder()
          .structureTypeClassCode(type[0]).description(type[1]).build());
    }
  }

  /* ------------------------------------------------------------------ fixtures */

  private void givenDistrict(long orgUnitNo, String code) {
    entityManager.persist(OrgUnitEntity.builder()
        .orgUnitNo(orgUnitNo).orgUnitCode(code).orgUnitName(code + " District").build());
  }

  private void givenSite(String id, Consumer<CrossingSiteEntity.CrossingSiteEntityBuilder> change) {
    CrossingSiteEntity.CrossingSiteEntityBuilder site = CrossingSiteEntity.builder()
        .crossingSiteId(id).capitalRoadInd("N");
    change.accept(site);
    entityManager.persist(site.build());
  }

  private void givenSite(String id) {
    givenSite(id, site -> { });
  }

  /** An active timber bridge on {@code siteId}, complete unless {@code change} says otherwise. */
  private void givenStructure(
      long id, String name, String siteId,
      Consumer<CrossingStructureEntity.CrossingStructureEntityBuilder> change) {
    CrossingStructureEntity.CrossingStructureEntityBuilder structure =
        CrossingStructureEntity.builder()
            .crossingStructureId(id)
            .crossingStructureName(name)
            .crossingSiteId(siteId)
            .structureTypeClassCode("TB")
            .activeInd("Y")
            .closeProximityInd("N")
            .portableStructureInd("N")
            .designLoadRating(new BigDecimal("50"))
            .currentLoadRating(new BigDecimal("50"))
            .fullLogHaulReplacementYear(2040)
            .nextPlannedInspectionDate(LocalDate.of(2027, 1, 1))
            .structureSourceCode("MOF");
    change.accept(structure);
    entityManager.persist(structure.build());
  }

  private void givenStructure(long id, String name, String siteId) {
    givenStructure(id, name, siteId, structure -> { });
  }

  private void givenCompleteBridge(long id, long structureId, String superstructure, String curb) {
    entityManager.persist(ForestServiceBridgeEntity.builder()
        .forestServiceBridgeId(id).crossingStructureId(structureId)
        .superstructureTypeCode(superstructure).structureCurbTypeCode(curb)
        .totalBridgeLength(new BigDecimal("12.5")).deckWidth(new BigDecimal("4.2"))
        .runningSurfaceCode("GRV").deckTypeCode("TIM").build());
  }

  private List<String> names(StructureSearchCriteria.StructureSearchCriteriaBuilder criteria) {
    return page(criteria).content().stream().map(StructureSearchResult::structureName).toList();
  }

  private PagedResponse<StructureSearchResult> page(
      StructureSearchCriteria.StructureSearchCriteriaBuilder criteria) {
    entityManager.flush();
    entityManager.clear();
    return service.search(criteria.build(), 0, 50, null, Sort.Direction.ASC);
  }

  /** One page of every structure, in legacy's order. */
  private PagedResponse<StructureSearchResult> unsorted(int pageNumber, int pageSize) {
    return service.search(criteria().build(), pageNumber, pageSize, null, Sort.Direction.ASC);
  }

  private List<String> sorted(StructureSortColumn column, Sort.Direction direction) {
    entityManager.flush();
    entityManager.clear();
    return service.search(criteria().build(), 0, 50, column, direction).content().stream()
        .map(StructureSearchResult::structureName).toList();
  }

  private static StructureSearchCriteria.StructureSearchCriteriaBuilder criteria() {
    return StructureSearchCriteria.builder();
  }

  /* ------------------------------------------------------------------ the tests */

  @Nested
  @DisplayName("criteria")
  class Criteria {

    @Test
    @DisplayName("matches a structure name on a fragment, whatever case was typed")
    void matchesTheName() {
      givenSite("SITE-1");
      givenStructure(1L, "BR000101", "SITE-1");
      givenStructure(2L, "CU000202", "SITE-1");

      assertThat(names(criteria().structureName("br0001"))).containsExactly("BR000101");
    }

    @Test
    @DisplayName("matches on the site the structure stands on, and on its road")
    void matchesOnTheSite() {
      entityManager.persist(CbrRoadSectionEntity.builder()
          .forestFileId("R00123").roadSectionId("01").roadSectName("Bowron FSR").build());
      // CROSSING_SITE's status is a real foreign key.
      entityManager.persist(CrossingSiteStatusCodeEntity.builder()
          .crossingSiteStatusCode("ACT").description("Active")
          .effectiveDate(LocalDateTime.now().minusYears(1))
          .expiryDate(LocalDateTime.now().plusYears(1))
          .updateTimestamp(LocalDateTime.now()).build());
      givenSite("SITE-1", site -> site.forestFileId("R00123").roadSectionId("01")
          .crossingName("Deadman Creek").crossingSiteStatusCode("ACT"));
      givenSite("SITE-2", site -> site.crossingName("Other Creek"));
      givenStructure(1L, "ON-ROAD", "SITE-1");
      givenStructure(2L, "ELSEWHERE", "SITE-2");

      assertThat(names(criteria().forestServiceRoad("bowron"))).containsExactly("ON-ROAD");
      assertThat(names(criteria().crossingName("deadman"))).containsExactly("ON-ROAD");
      assertThat(names(criteria().siteStatusCode("ACT"))).containsExactly("ON-ROAD");
      assertThat(names(criteria().siteId("site-2"))).containsExactly("ELSEWHERE");
    }

    @Test
    @DisplayName("bounds kilometres and years inclusively, at either end alone")
    void boundsRanges() {
      givenSite("SITE-1", site -> site.pointOfCommencementDistance(new BigDecimal("10.00")));
      givenSite("SITE-2", site -> site.pointOfCommencementDistance(new BigDecimal("20.00")));
      givenStructure(1L, "AT-10", "SITE-1", s -> s.yearBuilt(1990));
      givenStructure(2L, "AT-20", "SITE-2", s -> s.yearBuilt(2010));

      assertThat(names(criteria().kiloStart("10").kiloEnd("15"))).containsExactly("AT-10");
      assertThat(names(criteria().kiloStart("20"))).containsExactly("AT-20");
      assertThat(names(criteria().yearBuiltEnd("1990"))).containsExactly("AT-10");
    }

    @Test
    @DisplayName("leaves archived structures out unless asked to include them")
    void leavesArchivedOut() {
      givenSite("SITE-1");
      givenStructure(1L, "STANDING", "SITE-1");
      givenStructure(2L, "ARCHIVED", "SITE-1", s -> s.activeInd("N"));

      assertThat(names(criteria())).containsExactly("STANDING");
      assertThat(names(criteria().includeArchived(true)))
          .containsExactlyInAnyOrder("STANDING", "ARCHIVED");
    }

    @Test
    @DisplayName("finds a downrated structure: rated below its design, or never rated at all")
    void findsDownratedStructures() {
      givenSite("SITE-1");
      givenStructure(1L, "AS-BUILT", "SITE-1");
      givenStructure(2L, "LOWER", "SITE-1", s -> s.currentLoadRating(new BigDecimal("30")));
      givenStructure(3L, "UNRATED", "SITE-1", s -> s.currentLoadRating(null));

      assertThat(names(criteria().downrated(true)))
          .containsExactlyInAnyOrder("LOWER", "UNRATED");
    }

    @Test
    @DisplayName("filters on the bridge's superstructure and curb, and the culvert's type")
    void filtersOnBridgeAndCulvert() {
      givenSite("SITE-1");
      givenStructure(1L, "STEEL", "SITE-1");
      givenStructure(2L, "TIMBER", "SITE-1");
      givenStructure(3L, "PIPE", "SITE-1", s -> s.structureTypeClassCode("CUL"));
      givenCompleteBridge(11L, 1L, "STL", "CONC");
      givenCompleteBridge(12L, 2L, "TIM", "WOOD");
      entityManager.persist(ForestServiceCulvertEntity.builder()
          .forestServiceCulvertId(21L).crossingStructureId(3L)
          .engineeredCulvertTypeCode("CSP").build());

      assertThat(names(criteria().superstructureTypeCode("STL"))).containsExactly("STEEL");
      assertThat(names(criteria().structureCurbTypeCode("WOOD"))).containsExactly("TIMBER");
      assertThat(names(criteria().culvertTypeCode("CSP"))).containsExactly("PIPE");
    }

    @Test
    @DisplayName("matches a maintainer typed as a name, through the client's name")
    void matchesAMaintainerByName() {
      entityManager.persist(ClientPublicEntity.builder()
          .clientNumber("00001012").clientName("CANFOR CORPORATION").build());
      givenSite("SITE-1", site -> site.clientNumber("00001012").clientLocnCode("01"));
      givenSite("SITE-2");
      givenStructure(1L, "CANFOR-OWNED", "SITE-1");
      givenStructure(2L, "NO-MAINTAINER", "SITE-2");

      assertThat(names(criteria().primaryUserName("canfor"))).containsExactly("CANFOR-OWNED");
    }
  }

  @Nested
  @DisplayName("Incomplete Data?")
  class Incomplete {

    @Test
    @DisplayName("finds a structure missing one of its own values")
    void findsAMissingValue() {
      givenSite("SITE-1", site -> site.clientNumber("00001012").clientLocnCode("01"));
      givenStructure(1L, "COMPLETE", "SITE-1");
      givenCompleteBridge(11L, 1L, "STL", "CONC");
      givenStructure(2L, "NO-SOURCE", "SITE-1", s -> s.structureSourceCode(null));
      givenCompleteBridge(12L, 2L, "STL", "CONC");

      assertThat(names(criteria().incomplete(true))).containsExactly("NO-SOURCE");
    }

    @Test
    @DisplayName("finds a bridge type with no bridge row, or with one missing a value")
    void findsAnIncompleteBridge() {
      givenSite("SITE-1", site -> site.clientNumber("00001012").clientLocnCode("01"));
      givenStructure(1L, "COMPLETE", "SITE-1");
      givenCompleteBridge(11L, 1L, "STL", "CONC");
      givenStructure(2L, "NO-BRIDGE-ROW", "SITE-1");
      givenStructure(3L, "NO-DECK-WIDTH", "SITE-1");
      entityManager.persist(ForestServiceBridgeEntity.builder()
          .forestServiceBridgeId(13L).crossingStructureId(3L).superstructureTypeCode("STL")
          .totalBridgeLength(new BigDecimal("12.5")).runningSurfaceCode("GRV").deckTypeCode("TIM")
          .build());

      assertThat(names(criteria().incomplete(true)))
          .containsExactlyInAnyOrder("NO-BRIDGE-ROW", "NO-DECK-WIDTH");
    }

    @Test
    @DisplayName("asks for abutments only of a portable superstructure in service")
    void asksForAbutmentsWhenInService() {
      givenSite("SITE-1", site -> site.clientNumber("00001012").clientLocnCode("01"));
      givenStructure(1L, "IN-SERVICE", "SITE-1", s -> s.structureTypeClassCode("PB"));
      entityManager.persist(ForestServiceBridgeEntity.builder()
          .forestServiceBridgeId(11L).crossingStructureId(1L).superstructureTypeCode("STL")
          .totalBridgeLength(new BigDecimal("12.5")).deckWidth(new BigDecimal("4.2"))
          .runningSurfaceCode("GRV").deckTypeCode("TIM")
          .portableSuperstructureStatusCode("S").build());
      givenStructure(2L, "IN-STORAGE", "SITE-1", s -> s.structureTypeClassCode("PB"));
      entityManager.persist(ForestServiceBridgeEntity.builder()
          .forestServiceBridgeId(12L).crossingStructureId(2L).superstructureTypeCode("STL")
          .totalBridgeLength(new BigDecimal("12.5")).deckWidth(new BigDecimal("4.2"))
          .runningSurfaceCode("GRV").deckTypeCode("TIM")
          .portableSuperstructureStatusCode("R").build());

      assertThat(names(criteria().incomplete(true))).containsExactly("IN-SERVICE");
    }
  }

  @Nested
  @DisplayName("results")
  class Results {

    @Test
    @DisplayName("orders as legacy does — district, road, branch, kilometre")
    void ordersAsLegacy() {
      givenDistrict(18L, "DPG");
      givenDistrict(21L, "DKA");
      givenSite("DPG-20", site -> site.orgUnitNo(18L).pointOfCommencementDistance(BigDecimal.TEN
          .add(BigDecimal.TEN)));
      givenSite("DPG-10", site -> site.orgUnitNo(18L).pointOfCommencementDistance(BigDecimal.TEN));
      givenSite("DKA-99", site -> site.orgUnitNo(21L)
          .pointOfCommencementDistance(new BigDecimal("99")));
      givenStructure(1L, "ON-DPG-20", "DPG-20");
      givenStructure(2L, "ON-DPG-10", "DPG-10");
      givenStructure(3L, "ON-DKA-99", "DKA-99");

      assertThat(names(criteria())).containsExactly("ON-DKA-99", "ON-DPG-10", "ON-DPG-20");
    }

    @Test
    @DisplayName("sorts by a chosen column, either way, ahead of legacy's order")
    void sortsByAChosenColumn() {
      givenSite("SITE-1");
      givenStructure(1L, "b-200", "SITE-1");
      givenStructure(2L, "A-300", "SITE-1");
      givenStructure(3L, "C-100", "SITE-1");

      assertThat(sorted(StructureSortColumn.STRUCTURE_NAME, Sort.Direction.ASC))
          .containsExactly("A-300", "b-200", "C-100");
      assertThat(sorted(StructureSortColumn.STRUCTURE_NAME, Sort.Direction.DESC))
          .containsExactly("C-100", "b-200", "A-300");
    }

    @Test
    @DisplayName("sorts Type/Class by the description it shows, keeping a structure with none")
    void sortsByTypeClass() {
      givenSite("SITE-1");
      givenStructure(1L, "TIMBER", "SITE-1");
      givenStructure(2L, "CULVERT", "SITE-1", structure -> structure.structureTypeClassCode("CUL"));
      givenStructure(3L, "PORTABLE", "SITE-1", structure -> structure.structureTypeClassCode("PB"));

      assertThat(sorted(StructureSortColumn.TYPE_CLASS, Sort.Direction.ASC))
          .containsExactly("CULVERT", "PORTABLE", "TIMBER");
    }

    @Test
    @DisplayName("sorts Maintainer by client number, then location")
    void sortsByMaintainer() {
      givenSite("SITE-1", site -> site.clientNumber("00000002").clientLocnCode("00"));
      givenSite("SITE-2", site -> site.clientNumber("00000001").clientLocnCode("02"));
      givenSite("SITE-3", site -> site.clientNumber("00000001").clientLocnCode("01"));
      givenStructure(1L, "ON-1", "SITE-1");
      givenStructure(2L, "ON-2", "SITE-2");
      givenStructure(3L, "ON-3", "SITE-3");

      assertThat(sorted(StructureSortColumn.MAINTAINER, Sort.Direction.ASC))
          .containsExactly("ON-3", "ON-2", "ON-1");
    }

    @Test
    @DisplayName("can sort by every column without failing")
    void sortsByEveryColumn() {
      givenSite("SITE-1");
      givenStructure(1L, "ONLY", "SITE-1");
      givenStructure(2L, "NOWHERE", null);

      for (StructureSortColumn column : StructureSortColumn.values()) {
        assertThat(sorted(column, Sort.Direction.DESC)).as(column.name()).hasSize(2);
      }
    }

    @Test
    @DisplayName("tells a caller who can delete what stops each structure being deleted")
    void reportsDeleteBlockers() {
      when(loggedUser.canDestroy()).thenReturn(true);
      givenSite("SITE-1");
      givenStructure(1L, "FREE", "SITE-1");
      givenStructure(2L, "INSPECTED", "SITE-1");
      entityManager.persist(StructureInspectionEntity.builder()
          .inspectionId(100L).crossingStructureId(2L).build());
      entityManager.persist(StructureRepairEntity.builder()
          .repairId(100L).crossingStructureId(2L).build());

      Map<String, List<String>> blockers = page(criteria()).content().stream().collect(
          Collectors.toMap(StructureSearchResult::structureName,
              StructureSearchResult::deleteBlockers));

      assertThat(blockers.get("FREE")).isEmpty();
      assertThat(blockers.get("INSPECTED")).containsExactly("inspections", "repairs");
    }

    @Test
    @DisplayName("does not work blockers out for a caller who cannot delete")
    void skipsBlockersForReaders() {
      givenSite("SITE-1");
      givenStructure(1L, "FREE", "SITE-1");

      assertThat(page(criteria()).content()).singleElement()
          .satisfies(row -> assertThat(row.deleteBlockers()).isNull());
    }

    @Test
    @DisplayName("keeps a structure with no site, which the left joins must not drop")
    void keepsAStructureWithNoSite() {
      givenStructure(1L, "NOWHERE", null);

      assertThat(page(criteria()).content()).singleElement().satisfies(row -> {
        assertThat(row.structureName()).isEqualTo("NOWHERE");
        assertThat(row.siteId()).isNull();
        assertThat(row.structureTypeClass()).isEqualTo("Timber Bridge");
      });
    }

    @Test
    @DisplayName("names the maintainer, read a page at a time")
    void namesTheMaintainer() {
      entityManager.persist(ClientPublicEntity.builder()
          .clientNumber("00001012").clientName("CANFOR CORPORATION").build());
      givenSite("SITE-1", site -> site.clientNumber("00001012").clientLocnCode("01"));
      givenStructure(1L, "BR000101", "SITE-1");

      assertThat(page(criteria()).content()).singleElement().satisfies(row -> {
        assertThat(row.clientNumber()).isEqualTo("00001012");
        assertThat(row.clientLocationCode()).isEqualTo("01");
        assertThat(row.clientName()).isEqualTo("CANFOR CORPORATION");
      });
    }

    @Test
    @DisplayName("reports the true total, not the size of the page")
    void reportsTheTotal() {
      givenSite("SITE-1");
      for (long id = 1; id <= 5; id++) {
        givenStructure(id, "BR00000" + id, "SITE-1");
      }
      entityManager.flush();
      entityManager.clear();

      PagedResponse<StructureSearchResult> first = unsorted(0, 2);

      assertThat(first.content()).hasSize(2);
      assertThat(first.totalElements()).isEqualTo(5);
      assertThat(first.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("caps the page size, so one request cannot ask for the province")
    void capsThePageSize() {
      assertThat(unsorted(0, 5000).pageSize()).isEqualTo(200);
      assertThat(unsorted(-1, 0).pageSize()).isEqualTo(20);
    }
  }
}
