package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.client.LrmopsApiClient;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.District;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadPage;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadSection;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadSort;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadSummary;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.TenureType;
import ca.bc.gov.nrs.cbr.exception.RoadLookupUnavailableException;
import ca.bc.gov.nrs.cbr.exception.RoadSectionNotFoundException;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.HeaderSort;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionResponse.RoadDistrict;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSortColumn;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/** {@link RoadSectionService} over a mocked {@link LrmopsApiClient}. */
class RoadSectionServiceTest {

  private final LrmopsApiClient lrmops = mock(LrmopsApiClient.class);
  private final RoadSectionService service = new RoadSectionService(lrmops);

  @BeforeEach
  void configured() {
    when(lrmops.isConfigured()).thenReturn(true);
  }

  @Test
  @DisplayName("search passes the criteria and the sorts in the API's terms, and maps the rows")
  void searchMapsBothWays() {
    when(lrmops.searchRoads(any(), anyInt(), anyInt(), any())).thenReturn(new RoadPage(
        List.of(new RoadSummary("R07571", "AA", "BIG CREEK FSR", "FSR", true, "ACME LTD")),
        16, 2, 1, 15));

    PagedResponse<RoadSearchResult> page = service.search(
        new RoadSearchCriteria("big creek", "R07571", null, "FSR", "acme"), 1, 15,
        List.of(new HeaderSort<>(RoadSortColumn.FOREST_SERVICE_ROAD, Sort.Direction.DESC),
            new HeaderSort<>(RoadSortColumn.PROJECT_FILE, Sort.Direction.ASC)));

    ArgumentCaptor<LrmopsApiClient.RoadSearchCriteria> criteria =
        ArgumentCaptor.forClass(LrmopsApiClient.RoadSearchCriteria.class);
    verify(lrmops).searchRoads(criteria.capture(), eq(1), eq(15),
        eq(List.of(new RoadSort("ROAD_NAME", "DESC"), new RoadSort("ROAD_ID", "ASC"))));
    assertThat(criteria.getValue()).isEqualTo(
        new LrmopsApiClient.RoadSearchCriteria("big creek", "R07571", null, "FSR", "acme"));

    assertThat(page.totalElements()).isEqualTo(16);
    // Retired, so shown as LRMOPS stores it — the code the dropdown filters on.
    assertThat(page.content()).containsExactly(
        new RoadSearchResult("BIG CREEK FSR", "R07571", "AA", "FSR-RETIRE", "ACME LTD"));
  }

  @Test
  @DisplayName("find answers the section with every district it lies in")
  void findListsTheDistricts() {
    when(lrmops.getRoadSection("R07571", "AA")).thenReturn(Optional.of(new RoadSection(
        "R07571", "AA", "BIG CREEK FSR", "FSR", false,
        List.of(new District(18L, "DPG", 1200.0), new District(19L, "DMH")), null, "ACME")));

    RoadSectionResponse section = service.find("R07571", "AA");

    assertThat(section.forestServiceRoad()).isEqualTo("BIG CREEK FSR");
    assertThat(section.tenureType()).isEqualTo("FSR");
    assertThat(section.clientName()).isEqualTo("ACME");
    assertThat(section.districts()).containsExactly(
        new RoadDistrict(18L, "DPG", 1200.0), new RoadDistrict(19L, "DMH", null));
  }

  @Test
  @DisplayName("find is a 404 for an unknown or half-typed pair")
  void findUnknownIsNotFound() {
    when(lrmops.getRoadSection("R07571", "ZZ")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.find("R07571", "ZZ"))
        .isInstanceOf(RoadSectionNotFoundException.class);
    assertThatThrownBy(() -> service.find("R07571", " "))
        .isInstanceOf(RoadSectionNotFoundException.class);
  }

  @Test
  @DisplayName("find is a 503, not a 404, when the API refuses or can't be reached")
  void findUnavailable() {
    when(lrmops.getRoadSection("R07571", "AA"))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
        .thenThrow(new ResourceAccessException("read timed out"));

    assertThatThrownBy(() -> service.find("R07571", "AA"))
        .isInstanceOf(RoadLookupUnavailableException.class)
        .extracting(ex -> ((RoadLookupUnavailableException) ex).getStatusCode().value())
        .isEqualTo(503);
    assertThatThrownBy(() -> service.find("R07571", "AA"))
        .isInstanceOf(RoadLookupUnavailableException.class);
  }

  @Test
  @DisplayName("an unconfigured client is unavailable, without a call")
  void unconfiguredIsUnavailable() {
    when(lrmops.isConfigured()).thenReturn(false);

    assertThatThrownBy(() -> service.find("R07571", "AA"))
        .isInstanceOf(RoadLookupUnavailableException.class);
    assertThat(service.check("CRS", "R07571", "AA")).isEqualTo(RoadCheck.UNAVAILABLE);
    verify(lrmops, never()).getRoadSection(any(), any());
  }

  @Test
  @DisplayName("check: found, not found, unavailable, and not applicable")
  void check() {
    when(lrmops.getRoadSection("R07571", "AA")).thenReturn(Optional.of(new RoadSection(
        "R07571", "AA", null, "RP", true, List.of(), null, null)));
    when(lrmops.getRoadSection("R07571", "ZZ")).thenReturn(Optional.empty());
    when(lrmops.getRoadSection("R07571", "XX"))
        .thenThrow(new ResourceAccessException("connect timed out"));

    // A retired road counts (business, 2026-11-23).
    assertThat(service.check("CRS", "R07571", "AA")).isEqualTo(RoadCheck.FOUND);
    assertThat(service.check("CRS", "R07571", "ZZ")).isEqualTo(RoadCheck.NOT_FOUND);
    assertThat(service.check("CRS", "R07571", "XX")).isEqualTo(RoadCheck.UNAVAILABLE);
    assertThat(service.check("REC", "R07571", "ZZ")).isEqualTo(RoadCheck.NOT_APPLICABLE);
    assertThat(service.check("CRS", "R07571", "")).isEqualTo(RoadCheck.NOT_APPLICABLE);
  }

  @Test
  @DisplayName("tenure types in the business's order, retired after each, and no '!'")
  void tenureTypesInOrder() {
    when(lrmops.getRoadTenureTypes()).thenReturn(List.of(
        new TenureType("CP", "Licence/Cutting Permit Road"),
        new TenureType("FSR", "Forest Service Road"),
        new TenureType("NSR", "Non Status Road"),
        new TenureType("RP", "Road Permit"),
        new TenureType("SUP", "Special Use Permit"),
        new TenureType("!", "Geometry only"),
        new TenureType("ZZZ", "Added later"),
        new TenureType("OTH", "Other"),
        new TenureType("FSR-RETIRE", "FSR (Retired)"),
        new TenureType("RP-RETIRE", "RP (Retired)"),
        new TenureType("SUP-RETIRE", "SUP (Retired)")));

    assertThat(service.tenureTypes()).extracting(CodeOptionResponse::code).containsExactly(
        "FSR", "FSR-RETIRE", "RP", "RP-RETIRE", "SUP", "SUP-RETIRE", "NSR", "CP", "OTH", "ZZZ");
    assertThat(service.tenureTypes().get(0))
        .isEqualTo(new CodeOptionResponse("FSR", "Forest Service Road"));
  }
}
