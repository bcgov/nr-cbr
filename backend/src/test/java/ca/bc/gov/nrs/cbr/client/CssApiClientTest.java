package ca.bc.gov.nrs.cbr.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ca.bc.gov.nrs.cbr.client.CssApiClient.CssUser;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** HTTP-contract tests for {@link CssApiClient}, against a {@link MockRestServiceServer}. */
class CssApiClientTest {

  private static final String USERS =
      "http://css.test/integrations/6599/dev/roles/CBR_INSPECTION_REVIEWER/users";

  private MockRestServiceServer server;
  private CssApiClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://css.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new CssApiClient(builder.build(), "6599", "dev");
  }

  private static String user(String guid, String idirUsername, String first, String last) {
    return """
        {"username":"%s@idir","email":"x@gov.bc.ca","firstName":"%s","lastName":"%s",
         "attributes":{"display_name":["%s %s"],"idir_user_guid":["%s"],
                       "idir_username":["%s"]}}"""
        .formatted(guid, first, last, first, last, guid.toUpperCase(), idirUsername);
  }

  private static String page(int page, List<String> users) {
    return "{\"page\":" + page + ",\"data\":[" + String.join(",", users) + "]}";
  }

  @Test
  @DisplayName("lists the role's users from the integration and environment it is set up for")
  void listsTheRolesUsers() {
    server.expect(requestTo(USERS + "?page=1&max=50"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(page(1, List.of(user("abc", "JSMITH", "Jane", "Smith"))),
            MediaType.APPLICATION_JSON));

    List<CssUser> users = client.usersWithRole("CBR_INSPECTION_REVIEWER");

    server.verify();
    assertThat(users).singleElement().satisfies(user -> {
      assertThat(user.firstName()).isEqualTo("Jane");
      assertThat(user.lastName()).isEqualTo("Smith");
      assertThat(user.userId()).isEqualTo("IDIR\\JSMITH");
    });
  }

  @Test
  @DisplayName("reads on while a page comes back full")
  void readsEveryPage() {
    List<String> full = IntStream.range(0, CssApiClient.PAGE_SIZE)
        .mapToObj(n -> user("g" + n, "U" + n, "F", "L" + n))
        .toList();
    server.expect(requestTo(USERS + "?page=1&max=50"))
        .andRespond(withSuccess(page(1, full), MediaType.APPLICATION_JSON));
    server.expect(requestTo(USERS + "?page=2&max=50"))
        .andRespond(withSuccess(page(2, List.of(user("last", "LAST", "F", "L"))),
            MediaType.APPLICATION_JSON));

    assertThat(client.usersWithRole("CBR_INSPECTION_REVIEWER"))
        .hasSize(CssApiClient.PAGE_SIZE + 1);
    server.verify();
  }

  @Test
  @DisplayName("a failure reaches the caller")
  void failureThrows() {
    server.expect(requestTo(USERS + "?page=1&max=50")).andRespond(withServerError());

    assertThatThrownBy(() -> client.usersWithRole("CBR_INSPECTION_REVIEWER"))
        .isInstanceOf(RestClientException.class);
  }

  @Test
  @DisplayName("builds the user ID as the signed-in user's is built")
  void userIds() {
    assertThat(new CssUser("abc@idir", "Jane", "Smith",
        Map.of("idir_username", List.of("JSMITH"))).userId()).isEqualTo("IDIR\\JSMITH");
    // IDIR - MFA's alias folds to IDIR, as at sign-in.
    assertThat(new CssUser("abc@azureidir", "Jane", "Smith",
        Map.of("idir_username", List.of("JSMITH"))).userId()).isEqualTo("IDIR\\JSMITH");
    assertThat(new CssUser("abc@bceidbusiness", "Bo", "Lee",
        Map.of("bceid_username", List.of("blee"))).userId()).isEqualTo("BCEID\\blee");
    // No username attribute: the GUID, upper-cased, as at sign-in.
    assertThat(new CssUser("abc@idir", null, null, Map.of()).userId()).isEqualTo("IDIR\\ABC");
    assertThat(new CssUser(null, null, null, null).userId()).isEmpty();
  }

  @Test
  @DisplayName("unconfigured, it says so")
  void unconfigured() {
    CssApiClient inert = new CssApiClient("http://css.test", "", "", "", "6599", "dev",
        Duration.ofSeconds(1), Duration.ofSeconds(1));

    assertThat(inert.isConfigured()).isFalse();
    assertThat(client.isConfigured()).isTrue();
  }
}
