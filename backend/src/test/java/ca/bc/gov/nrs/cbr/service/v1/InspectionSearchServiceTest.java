package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.model.v1.CbrRoadSectionEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Paging, the empty-criteria refusal, and the mapping to a results row.
 *
 * <p>The predicates themselves are tested against a database in
 * {@link ca.bc.gov.nrs.cbr.specification.v1.InspectionSearchSpecificationsTest}; this covers what
 * this class actually decides.
 */
class InspectionSearchServiceTest {

  private final StructureInspectionRepository repository = mock(StructureInspectionRepository.class);
  private final InspectionSearchService service = new InspectionSearchService(repository);

  private static InspectionSearchCriteria someCriteria() {
    return InspectionSearchCriteria.builder().siteId("site-1").build();
  }

  private static StructureInspectionEntity inspection() {
    OrgUnitEntity orgUnit = OrgUnitEntity.builder()
        .orgUnitNo(18L).orgUnitCode("DPG").orgUnitName("Prince George").build();
    CbrRoadSectionEntity roadSection = CbrRoadSectionEntity.builder()
        .forestFileId("R00123").roadSectionId("01").roadSectName("Deadman FSR").build();
    CrossingSiteEntity site = CrossingSiteEntity.builder()
        .crossingSiteId("site-1")
        .crossingName("Deadman Creek")
        .pointOfCommencementDistance(new BigDecimal("12.50"))
        .forestFileId("R00123")
        .roadSectionId("01")
        .orgUnit(orgUnit)
        .roadSection(roadSection)
        .build();
    CrossingStructureEntity structure = CrossingStructureEntity.builder()
        .crossingStructureId(100L).crossingStructureName("BR000001").site(site).build();
    InspectionReportStatusEntity status = InspectionReportStatusEntity.builder()
        .inspectionReportStatusId(1L)
        .inspectionReportStatusCode("SUB")
        .statusCode(InspectionReportStatusCodeEntity.builder()
            .inspectionReportStatusCode("SUB").description("Submitted").build())
        .build();
    return StructureInspectionEntity.builder()
        .inspectionId(42L)
        .inspectionDate(LocalDate.of(2026, 6, 15))
        .siteAtTimeOfInspection("site-0")
        .structure(structure)
        .currentStatus(status)
        .build();
  }

  private void givenOneResult() {
    when(repository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(inspection()), PageRequest.of(0, 20), 1));
  }

  @Test
  @DisplayName("answers an empty search rather than refusing it, as Site Search does")
  void answersAnEmptySearch() {
    // Legacy refuses this on all five of its search forms with errors.search.select, because its
    // query was unpaginated and "no criteria" meant the whole province in one response. Paging
    // removes the reason. The two search screens must agree, and SiteSearchService answers.
    givenOneResult();

    PagedResponse<InspectionSearchResult> response =
        service.search(InspectionSearchCriteria.builder().build(), 0, 20);

    assertThat(response.content()).hasSize(1);
    verify(repository).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  @DisplayName("maps every column of a results row from the right association")
  void mapsAResultRow() {
    // Four of these values arrive through three levels of association — inspection → structure →
    // site → org unit — and every one of them is a String. Crossing two over compiles.
    givenOneResult();

    InspectionSearchResult row = service.search(someCriteria(), 0, 20).content().getFirst();

    assertThat(row.id()).isEqualTo("42");
    assertThat(row.inspectionDate()).isEqualTo("2026-06-15");
    assertThat(row.inspectionReportStatusCode()).isEqualTo("SUB");
    assertThat(row.inspectionReportStatusDescription()).isEqualTo("Submitted");
    assertThat(row.siteAtTimeOfInspection()).isEqualTo("site-0");
    assertThat(row.structureName()).isEqualTo("BR000001");
    assertThat(row.orgUnitCode()).isEqualTo("DPG");
    assertThat(row.orgUnitName()).isEqualTo("Prince George");
    assertThat(row.forestServiceRoad()).isEqualTo("Deadman FSR");
    assertThat(row.pointOfCommencementDistance()).isEqualTo("12.50");
    assertThat(row.crossingName()).isEqualTo("Deadman Creek");
    assertThat(row.forestFileId()).isEqualTo("R00123");
    assertThat(row.roadSectionId()).isEqualTo("01");
  }

  @Test
  @DisplayName("a missing optional association is a blank column, not a failed page")
  void toleratesMissingAssociations() {
    // The road section comes from a materialized view over a DB link and is routinely absent; the
    // org unit can be missing too. Legacy's left joins behave the same way.
    CrossingSiteEntity bareSite = CrossingSiteEntity.builder().crossingSiteId("site-1").build();
    StructureInspectionEntity bare = StructureInspectionEntity.builder()
        .inspectionId(7L)
        .structure(CrossingStructureEntity.builder().crossingStructureId(1L).site(bareSite).build())
        .currentStatus(InspectionReportStatusEntity.builder()
            .inspectionReportStatusId(1L).inspectionReportStatusCode("OFL").build())
        .build();
    when(repository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(bare), PageRequest.of(0, 20), 1));

    InspectionSearchResult row = service.search(someCriteria(), 0, 20).content().getFirst();

    assertThat(row.id()).isEqualTo("7");
    assertThat(row.inspectionReportStatusCode()).isEqualTo("OFL");
    assertThat(row.inspectionReportStatusDescription()).isNull();
    assertThat(row.orgUnitCode()).isNull();
    assertThat(row.forestServiceRoad()).isNull();
    assertThat(row.inspectionDate()).isNull();
  }

  @Test
  @DisplayName("keeps the kilometre's scale rather than letting it become scientific notation")
  void keepsDecimalScale() {
    givenOneResult();

    assertThat(service.search(someCriteria(), 0, 20).content().getFirst()
        .pointOfCommencementDistance()).isEqualTo("12.50");
  }

  @Test
  @DisplayName("caps the page size and floors the page number")
  void boundsThePagingRequest() {
    givenOneResult();
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

    service.search(someCriteria(), -5, 5000);

    verify(repository).findAll(any(Specification.class), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isZero();
    assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
  }

  @Test
  @DisplayName("a page size of zero falls back to the default rather than failing")
  void defaultsAnUnusablePageSize() {
    givenOneResult();
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

    service.search(someCriteria(), 0, 0);

    verify(repository).findAll(any(Specification.class), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
  }

  @Test
  @DisplayName("asks for no sort on the Pageable, because every sort key is on a joined table")
  void doesNotSortThroughThePageable() {
    // Spring Data resolves a sort path with an INNER join, which would drop every inspection whose
    // site has no org unit. The ordering is applied inside the specification instead.
    givenOneResult();
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

    service.search(someCriteria(), 0, 20);

    verify(repository).findAll(any(Specification.class), pageable.capture());
    assertThat(pageable.getValue().getSort().isSorted()).isFalse();
  }
}
