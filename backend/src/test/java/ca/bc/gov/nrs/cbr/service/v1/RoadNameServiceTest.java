package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.client.LrmopsApiClient;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadSummary;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.SectionKey;
import ca.bc.gov.nrs.cbr.exception.RoadLookupUnavailableException;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/** {@link RoadNameService} over a mocked {@link LrmopsApiClient}. */
class RoadNameServiceTest {

  private final LrmopsApiClient lrmops = mock(LrmopsApiClient.class);
  private final RoadNameService service = new RoadNameService(lrmops);

  @BeforeEach
  void configured() {
    when(lrmops.isConfigured()).thenReturn(true);
  }

  @Test
  @DisplayName("suggestions ask only for a term of three characters or more")
  void suggestions() {
    when(lrmops.getRoadNames("big", RoadNameService.SUGGESTIONS))
        .thenReturn(List.of("BIG CREEK FSR"));

    assertThat(service.suggestions("big")).containsExactly("BIG CREEK FSR");
    assertThat(service.suggestions(" bi ")).isEmpty();
    assertThat(service.suggestions(null)).isEmpty();
    verify(lrmops, times(1)).getRoadNames(any(), anyInt());
  }

  @Test
  @DisplayName("the filter is null for no name, and the sections normalized for one")
  void sectionsNamed() {
    when(lrmops.getRoadSectionsNamed("Big Creek FSR")).thenReturn(List.of(
        new SectionKey("R07571", "aa"), new SectionKey("R07571", "AA"), new SectionKey(null, "X")));

    assertThat(service.sectionsNamed("  ")).isNull();
    assertThat(service.sectionsNamed("Big Creek FSR"))
        .containsExactly(RoadSectionKey.of("R07571", "AA"));
  }

  @Test
  @DisplayName("the filter fails loudly when LRMOPS cannot answer, rather than matching all or none")
  void sectionsNamedUnavailable() {
    when(lrmops.getRoadSectionsNamed(any())).thenThrow(new ResourceAccessException("down"));

    assertThatThrownBy(() -> service.sectionsNamed("Big Creek FSR"))
        .isInstanceOf(RoadLookupUnavailableException.class);
  }

  @Test
  @DisplayName("names are looked up once per distinct section, keyed as CBR keys them")
  void namesOf() {
    when(lrmops.lookUpRoadSections(any())).thenReturn(List.of(
        new RoadSummary("R07571", "AA", "BIG CREEK FSR", "FSR", false, null),
        new RoadSummary("R00001", "B", null, "OTH", false, null)));

    var names = service.namesOf(java.util.Arrays.asList(
        RoadSectionKey.of("r07571", "aa"), RoadSectionKey.of("R07571", "AA"),
        RoadSectionKey.of("R00001", "B"), null));

    assertThat(names).containsExactlyEntriesOf(
        java.util.Map.of(RoadSectionKey.of("R07571", "AA"), "BIG CREEK FSR"));
    verify(lrmops).lookUpRoadSections(
        List.of(new SectionKey("R07571", "AA"), new SectionKey("R00001", "B")));
  }

  @Test
  @DisplayName("many sections are looked up in batches")
  void namesOfInBatches() {
    List<RoadSectionKey> many = IntStream.range(0, RoadNameService.LOOKUP_BATCH * 2 + 1)
        .mapToObj(i -> RoadSectionKey.of("R" + i, "A")).toList();
    when(lrmops.lookUpRoadSections(any())).thenReturn(List.of());

    service.namesOf(many);

    verify(lrmops, times(3)).lookUpRoadSections(any());
  }

  @Test
  @DisplayName("names are blank, not an error, when LRMOPS cannot answer or is not configured")
  void namesOfUnavailable() {
    when(lrmops.lookUpRoadSections(any())).thenThrow(new ResourceAccessException("down"));
    assertThat(service.namesOf(List.of(RoadSectionKey.of("R07571", "AA")))).isEmpty();

    when(lrmops.isConfigured()).thenReturn(false);
    assertThat(service.namesOf(List.of(RoadSectionKey.of("R07571", "AA")))).isEmpty();
    assertThat(service.namesOf(new ArrayList<>())).isEmpty();
    verify(lrmops, times(1)).lookUpRoadSections(any());
    verify(lrmops, never()).getRoadNames(any(), anyInt());
  }

  @Test
  @DisplayName("one section's name, null when blank or not found")
  void nameOf() {
    when(lrmops.lookUpRoadSections(List.of(new SectionKey("R07571", "AA")))).thenReturn(
        List.of(new RoadSummary("R07571", "AA", "BIG CREEK FSR", "FSR", false, null)));

    assertThat(service.nameOf(" r07571", "aa ")).isEqualTo("BIG CREEK FSR");
    assertThat(service.nameOf("R07571", " ")).isNull();
    assertThat(service.nameOf("R99999", "A")).isNull();
  }
}
