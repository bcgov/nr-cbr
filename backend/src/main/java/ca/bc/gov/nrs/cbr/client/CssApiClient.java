package ca.bc.gov.nrs.cbr.client;

import ca.bc.gov.nrs.cbr.util.JwtPrincipalUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client for the <b>CSS API</b> — the Common Hosted Single Sign-On service's API over CBR's own
 * integration. FAM grants CBR's roles as client roles on that integration, so CSS is where the list
 * of who holds one lives ({@code cbr-inspection-reviewer.local.md} §10).
 *
 * <p>Every call carries a {@code client_credentials} token minted from CBR's CSS API account (see
 * {@link ClientCredentialsTokenSource}); the caller's own token is not forwarded.
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code ca.bc.gov.nrs.css-api.base-url} — the API, {@code .../api/v1}</li>
 *   <li>{@code ca.bc.gov.nrs.css-api.token-url} / {@code .client-id} / {@code .client-secret} —
 *       the CSS API account</li>
 *   <li>{@code ca.bc.gov.nrs.css-api.integration-id} — CBR's integration</li>
 *   <li>{@code ca.bc.gov.nrs.css-api.environment} — {@code dev}, {@code test} or {@code prod}: the
 *       CSS environment this deployment signs in against</li>
 * </ul>
 * Unconfigured, the client is inert: {@link #isConfigured()} is false and nothing is called.
 */
@Component
public class CssApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(CssApiClient.class);

  private static final String ROLE_USERS_PATH =
      "/integrations/{integrationId}/{environment}/roles/{roleName}/users";
  /** Users asked for per page — CSS's most, 1 to 50. A shorter page is the last. */
  static final int PAGE_SIZE = 50;
  /** A stop for a server that never returns a short page. */
  private static final int MAX_PAGES = 50;

  private final RestClient http;
  private final ClientCredentialsTokenSource clientCredentials;
  private final String integrationId;
  private final String environment;
  private final boolean configured;

  @Autowired
  public CssApiClient(
      @Value("${ca.bc.gov.nrs.css-api.base-url:}") String baseUrl,
      @Value("${ca.bc.gov.nrs.css-api.token-url:}") String tokenUrl,
      @Value("${ca.bc.gov.nrs.css-api.client-id:}") String clientId,
      @Value("${ca.bc.gov.nrs.css-api.client-secret:}") String clientSecret,
      @Value("${ca.bc.gov.nrs.css-api.integration-id:}") String integrationId,
      @Value("${ca.bc.gov.nrs.css-api.environment:}") String environment,
      @Value("${ca.bc.gov.nrs.css-api.connect-timeout:5s}") Duration connectTimeout,
      @Value("${ca.bc.gov.nrs.css-api.read-timeout:10s}") Duration readTimeout) {
    this.clientCredentials = ClientCredentialsTokenSource.fromProps("css-api", "CSS_API",
        tokenUrl, clientId, clientSecret, "", connectTimeout, readTimeout);
    this.integrationId = integrationId == null ? "" : integrationId.trim();
    this.environment = environment == null ? "" : environment.trim();
    this.configured = clientCredentials != null && !this.integrationId.isEmpty()
        && !this.environment.isEmpty();

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    this.http = RestClient.builder()
        .baseUrl(baseUrl == null ? "" : baseUrl.trim())
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();

    LOG.info(configured
        ? "css-api client active (integration {}, environment {})"
        : "css-api client inactive — set CSS_API_TOKEN_URL/CLIENT_ID/CLIENT_SECRET, "
            + "CSS_API_INTEGRATION_ID and CSS_API_ENVIRONMENT to enable", this.integrationId,
        this.environment);
  }

  /** Test hook: a pre-built {@link RestClient} (e.g. a MockRestServiceServer's), no auth. */
  CssApiClient(RestClient http, String integrationId, String environment) {
    this.http = http;
    this.clientCredentials = null;
    this.integrationId = integrationId;
    this.environment = environment;
    this.configured = true;
  }

  /** Whether the client has an integration and an environment to ask, and an account to ask as. */
  public boolean isConfigured() {
    return configured;
  }

  /**
   * Every user holding the client role {@code roleName} on CBR's integration, page by page.
   *
   * @throws org.springframework.web.client.RestClientException when CSS fails
   */
  public List<CssUser> usersWithRole(String roleName) {
    List<CssUser> users = new ArrayList<>();
    for (int page = 1; page <= MAX_PAGES; page++) {
      int current = page;
      RoleUsersResponse response = http.get()
          .uri(uriBuilder -> {
            URI uri = uriBuilder.path(ROLE_USERS_PATH)
                .queryParam("page", current)
                .queryParam("max", PAGE_SIZE)
                .build(integrationId, environment, roleName);
            LOG.debug("css-api role users: {}", uri);
            return uri;
          })
          .headers(this::applyAuth)
          .retrieve()
          .body(RoleUsersResponse.class);
      List<CssUser> data = response == null || response.data() == null
          ? List.of() : response.data();
      users.addAll(data);
      if (data.size() < PAGE_SIZE) {
        return users;
      }
    }
    LOG.warn("css-api role users: stopped after {} pages of {}", MAX_PAGES, PAGE_SIZE);
    return users;
  }

  private void applyAuth(HttpHeaders headers) {
    if (clientCredentials == null) {
      return;
    }
    String token = clientCredentials.fetchCached();
    if (!token.isBlank()) {
      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
  }

  // ── Local response records (do not leak the API's DTO types) ───────

  /**
   * A user as CSS lists them: {@code username} is {@code <guid>@<identity provider>}, and
   * {@code attributes} holds the directory's own values, each a one-element list.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CssUser(
      String username, String firstName, String lastName, Map<String, List<String>> attributes) {

    /**
     * The user ID as CBR stores it — {@code IDIR\JSMITH} — built by the same rules as the signed-in
     * user's ({@link JwtPrincipalUtil#getUserId}), so it matches the rows their reviews wrote.
     * Empty when CSS gives nothing to build it from.
     */
    public String userId() {
      Map<String, Object> claims = new HashMap<>();
      if (username != null) {
        claims.put("preferred_username", username);
        int at = username.indexOf('@');
        if (at >= 0) {
          claims.put("identity_provider", username.substring(at + 1));
        }
      }
      if (attributes != null) {
        attributes.forEach((name, values) -> {
          if (values != null && !values.isEmpty() && values.get(0) != null) {
            claims.put(name, values.get(0));
          }
        });
      }
      return JwtPrincipalUtil.getUserId(claims);
    }
  }

  /** Wire shape of the role-users endpoint. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RoleUsersResponse(Integer page, List<CssUser> data) {
  }
}
