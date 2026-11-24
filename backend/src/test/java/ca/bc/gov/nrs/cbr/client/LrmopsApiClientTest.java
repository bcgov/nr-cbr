package ca.bc.gov.nrs.cbr.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.District;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadPage;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadSearchCriteria;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadSection;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.RoadSort;
import ca.bc.gov.nrs.cbr.client.LrmopsApiClient.TenureType;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** HTTP-contract tests for {@link LrmopsApiClient}, using a {@link MockRestServiceServer}. */
class LrmopsApiClientTest {

  private MockRestServiceServer server;
  private LrmopsApiClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://lrmops.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new LrmopsApiClient(builder.build());
  }

  @Test
  void searchSendsOnlyTheNonBlankCriteriaAndTheSorts() {
    server.expect(requestTo("http://lrmops.test/api/v1/roads?roadName=big%20creek&roadId=R07571"
            + "&pageNumber=1&pageSize=15&sortBy=ROAD_NAME&sortDirection=DESC"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("""
            {"content":[{"roadId":"R07571","sectionId":"AA","roadName":"BIG CREEK FSR",
              "tenureType":"FSR","retired":false,"clientName":null}],
             "totalElements":16,"totalPages":2,"pageNumber":1,"pageSize":15}
            """, MediaType.APPLICATION_JSON));

    RoadPage page = client.searchRoads(
        new RoadSearchCriteria(" big creek ", "R07571", "  ", null, ""), 1, 15,
        List.of(new RoadSort("ROAD_NAME", "DESC")));

    server.verify();
    assertThat(page.totalElements()).isEqualTo(16);
    assertThat(page.content()).singleElement()
        .satisfies(road -> {
          assertThat(road.roadId()).isEqualTo("R07571");
          assertThat(road.tenureType()).isEqualTo("FSR");
        });
  }

  @Test
  void sectionMapsTheDistricts() {
    server.expect(requestTo("http://lrmops.test/api/v1/roads/R07571/sections/AA"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("""
            {"roadId":"R07571","sectionId":"AA","roadName":"BIG CREEK FSR","tenureType":"FSR",
             "retired":false,"districts":[{"orgUnitNo":18,"code":"DPG"},{"orgUnitNo":19,"code":"DMH"}],
             "bctsOffice":null}
            """, MediaType.APPLICATION_JSON));

    Optional<RoadSection> section = client.getRoadSection(" R07571 ", "AA");

    server.verify();
    assertThat(section).get()
        .extracting(RoadSection::districts)
        .isEqualTo(List.of(new District(18L, "DPG"), new District(19L, "DMH")));
  }

  @Test
  void aSectionIdWithASpaceIsEncoded() {
    server.expect(requestTo("http://lrmops.test/api/v1/roads/S00100/sections/a%201"))
        .andRespond(withSuccess("{\"roadId\":\"S00100\",\"sectionId\":\"a 1\"}",
            MediaType.APPLICATION_JSON));

    assertThat(client.getRoadSection("S00100", "a 1")).get()
        .extracting(RoadSection::districts).isEqualTo(List.of());
    server.verify();
  }

  @Test
  void anUnknownSectionIsEmpty() {
    server.expect(requestTo("http://lrmops.test/api/v1/roads/R07571/sections/ZZ"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(client.getRoadSection("R07571", "ZZ")).isEmpty();
    server.verify();
  }

  @Test
  void aBlankIdIsEmptyWithoutACall() {
    assertThat(client.getRoadSection(" ", "AA")).isEmpty();
    assertThat(client.getRoadSection("R07571", null)).isEmpty();
    server.verify();
  }

  @Test
  void aRefusedCallIsNotMistakenForNotFound() {
    server.expect(requestTo("http://lrmops.test/api/v1/roads/R07571/sections/AA"))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> client.getRoadSection("R07571", "AA"))
        .isInstanceOf(HttpClientErrorException.Forbidden.class);
  }

  @Test
  void tenureTypesAreListed() {
    server.expect(requestTo("http://lrmops.test/api/v1/road-tenure-types"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("""
            [{"code":"FSR","description":"Forest Service Road"},
             {"code":"RP","description":"Road Permit"}]
            """, MediaType.APPLICATION_JSON));

    assertThat(client.getRoadTenureTypes()).containsExactly(
        new TenureType("FSR", "Forest Service Road"), new TenureType("RP", "Road Permit"));
    server.verify();
  }

  @Test
  void withoutAServiceAccountTheClientIsInactive() {
    LrmopsApiClient unset = new LrmopsApiClient("https://lrmops.test", "", "", "",
        Duration.ofSeconds(1), Duration.ofSeconds(1));
    assertThat(unset.isConfigured()).isFalse();

    LrmopsApiClient noUrl = new LrmopsApiClient("", "https://kc.test/token", "nr-cbr-backend",
        "secret", Duration.ofSeconds(1), Duration.ofSeconds(1));
    assertThat(noUrl.isConfigured()).isFalse();
  }

  @Test
  void aPartialServiceAccountFailsFast() {
    Duration second = Duration.ofSeconds(1);
    assertThatThrownBy(() -> new LrmopsApiClient("https://lrmops.test", "https://kc.test/token",
        "nr-cbr-backend", "", second, second))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("LRMOPS_API_CLIENT_SECRET");
  }
}
