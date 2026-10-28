package ca.bc.gov.nrs.cbr.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Encapsulates the OAuth2 {@code client_credentials} exchange used to
 * authenticate outbound calls — to nr-user-lookup-api and to the CSS API. Owns its own
 * {@link RestClient} (pointed at the Keycloak token endpoint), the
 * Basic-auth header derived from client_id/client_secret, and the form
 * body. Returned tokens are short-lived JWTs; this source caches them
 * and refreshes ~60s before expiry to absorb clock skew + token-endpoint
 * latency.
 */
public final class ClientCredentialsTokenSource {

  private static final Logger LOG = LoggerFactory.getLogger(ClientCredentialsTokenSource.class);

  private final RestClient http;
  /** Which client this authenticates, for the logs: {@code user-lookup}, {@code css-api}. */
  private final String name;
  private final String tokenUrl;
  private final String clientId;
  private final String scope;
  private final String basicAuthHeader;

  // Cached access token + the moment it expires. volatile because callers may
  // hit fetchCached() from different request threads; a stray double-fetch on
  // a race is harmless.
  private volatile String cachedAccessToken = "";
  private volatile Instant cachedAccessTokenExpiresAt = Instant.EPOCH;

  private ClientCredentialsTokenSource(RestClient http, String name, String tokenUrl,
      String clientId, String scope, String basicAuthHeader) {
    this.http = http;
    this.name = name;
    this.tokenUrl = tokenUrl;
    this.clientId = clientId;
    this.scope = scope;
    this.basicAuthHeader = basicAuthHeader;
  }

  /**
   * @return a configured source when all three of token-url / client-id /
   *     client-secret are non-blank; {@code null} when client-credentials auth
   *     wasn't requested at all (lets the caller fall back to unauthenticated).
   */
  public static ClientCredentialsTokenSource fromProps(
      String tokenUrl, String clientId, String clientSecret, String scope,
      Duration connectTimeout, Duration readTimeout) {
    return fromProps("user-lookup", tokenUrl, clientId, clientSecret, scope,
        connectTimeout, readTimeout);
  }

  /**
   * As {@link #fromProps(String, String, String, String, Duration, Duration)}, for the client
   * {@code name}, whose settings are the environment variables named after it: {@code css-api},
   * {@code CSS_API_*}.
   */
  public static ClientCredentialsTokenSource fromProps(
      String name, String tokenUrl, String clientId, String clientSecret,
      String scope, Duration connectTimeout, Duration readTimeout) {
    String envPrefix = name.toUpperCase(Locale.ROOT).replace('-', '_');
    String url = tokenUrl == null ? "" : tokenUrl.trim();
    String id = clientId == null ? "" : clientId.trim();
    String secret = clientSecret == null ? "" : clientSecret.trim();
    String requestedScope = scope == null ? "" : scope.trim();

    if (url.isBlank() && id.isBlank() && secret.isBlank()) {
      return null;
    }
    if (url.isBlank() || id.isBlank() || secret.isBlank()) {
      throw new IllegalStateException(
          name + " client_credentials misconfigured — set ALL of "
              + envPrefix + "_TOKEN_URL, " + envPrefix + "_CLIENT_ID, " + envPrefix
              + "_CLIENT_SECRET (or none).");
    }

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    RestClient http = RestClient.builder()
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();

    String basic = "Basic " + Base64.getEncoder().encodeToString(
        (id + ":" + secret).getBytes(StandardCharsets.UTF_8));
    return new ClientCredentialsTokenSource(http, name, url, id, requestedScope, basic);
  }

  public String tokenUrl() {
    return tokenUrl;
  }

  public String clientId() {
    return clientId;
  }

  public String scope() {
    return scope;
  }

  /**
   * Returns a valid access token from the cache, fetching a fresh one when the
   * cache is empty or within ~60s of expiry. Returns {@code ""} (never throws)
   * when a fetch fails so the caller can decide how to proceed; the next call
   * will retry.
   */
  public String fetchCached() {
    Instant now = Instant.now();
    if (!cachedAccessToken.isBlank()
        && now.isBefore(cachedAccessTokenExpiresAt.minusSeconds(60))) {
      return cachedAccessToken;
    }
    try {
      Token fresh = fetch();
      cachedAccessToken = fresh.accessToken();
      cachedAccessTokenExpiresAt = now.plusSeconds(fresh.expiresInSeconds());
      return cachedAccessToken;
    } catch (RuntimeException ex) {
      LOG.warn("{} client_credentials token fetch failed ({}); next call will retry",
          name, ex.getMessage());
      cachedAccessToken = "";
      cachedAccessTokenExpiresAt = Instant.EPOCH;
      return "";
    }
  }

  Token fetch() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    if (!scope.isBlank()) {
      form.add("scope", scope);
    }
    TokenResponse resp = http.post()
        .uri(URI.create(tokenUrl))
        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .body(TokenResponse.class);

    if (resp == null || resp.accessToken() == null || resp.accessToken().isBlank()) {
      throw new IllegalStateException("token endpoint returned no access_token");
    }
    // Default to 5 minutes if the server omits expires_in so we still rotate
    // reasonably often.
    long ttl = resp.expiresIn() == null || resp.expiresIn() <= 0 ? 300L : resp.expiresIn();
    return new Token(resp.accessToken(), ttl);
  }

  // Token endpoints typically return extra fields (refresh_token, id_token,
  // scope, ...); ignore them so a strict deserializer can't break the exchange.
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("token_type") String tokenType) {
  }

  record Token(String accessToken, long expiresInSeconds) {
  }
}
