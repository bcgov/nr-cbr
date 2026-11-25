package ca.bc.gov.nrs.cbr.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Client for the <b>LRM Ops API</b>: read-only LRMOPS road data from the TFM database, which CBR
 * used to read directly.
 *
 * <p>Every call carries a Keycloak {@code client_credentials} bearer token minted from CBR's own
 * service account (see {@link ClientCredentialsTokenSource}). The user's token is not forwarded:
 * the API authorizes the calling application by the {@code lrmops:roads:read} scope, which CI
 * assigns to CBR's service account as a default scope
 * ({@code .github/scripts/ensure-keycloak-service-account.sh}).
 *
 * <p>That service account lives in the realm the API validates tokens against (the deploy's
 * {@code KEYCLOAK_SA_ISSUER_URI}), not the standard realm CBR's own users sign in through.
 *
 * <h3>Configuration</h3>
 * {@code ca.bc.gov.nrs.lrmops-api.*}, from the {@code LRMOPS_API_*} environment variables:
 * {@code base-url}, {@code token-url}, {@code client-id}, {@code client-secret}. Set all of the
 * last three or none; unset, {@link #isConfigured()} is false and calls go out unauthenticated
 * (and are refused), which is how PR previews run without the service account.
 */
@Component
public class LrmopsApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(LrmopsApiClient.class);

  /** The API's path prefix, as segments, appended to the configured base URL. */
  private static final String[] API_V1 = {"api", "v1"};

  private final RestClient http;
  private final ClientCredentialsTokenSource clientCredentials;
  private final boolean configured;

  @Autowired
  public LrmopsApiClient(
      @Value("${ca.bc.gov.nrs.lrmops-api.base-url:}") String baseUrl,
      @Value("${ca.bc.gov.nrs.lrmops-api.token-url:}") String tokenUrl,
      @Value("${ca.bc.gov.nrs.lrmops-api.client-id:}") String clientId,
      @Value("${ca.bc.gov.nrs.lrmops-api.client-secret:}") String clientSecret,
      @Value("${ca.bc.gov.nrs.lrmops-api.connect-timeout:5s}") Duration connectTimeout,
      @Value("${ca.bc.gov.nrs.lrmops-api.read-timeout:35s}") Duration readTimeout) {
    this.clientCredentials = ClientCredentialsTokenSource.fromProps(
        "lrmops-api", tokenUrl, clientId, clientSecret, "", connectTimeout, readTimeout);
    String url = baseUrl == null ? "" : baseUrl.trim();
    this.configured = clientCredentials != null && !url.isEmpty();

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    this.http = RestClient.builder()
        .baseUrl(url)
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();

    if (configured) {
      LOG.info("lrmops-api client active (base-url={}, token-url={}, client-id={})", url,
          clientCredentials.tokenUrl(), clientCredentials.clientId());
    } else {
      LOG.info("lrmops-api client inactive - set LRMOPS_API_BASE_URL and "
          + "LRMOPS_API_TOKEN_URL/CLIENT_ID/CLIENT_SECRET to enable");
    }
  }

  /** Test hook: a pre-built {@link RestClient} (e.g. a MockRestServiceServer's), no auth. */
  LrmopsApiClient(RestClient http) {
    this.http = http;
    this.clientCredentials = null;
    this.configured = true;
  }

  /** Whether the client has an API to call and a service account to call it as. */
  public boolean isConfigured() {
    return configured;
  }

  /**
   * {@code GET /api/v1/roads}: road sections matching every non-blank criterion (partial,
   * case-insensitive), one page at a time.
   *
   * @param sorts columns ({@code ROAD_NAME}, {@code ROAD_ID}, {@code SECTION_ID},
   *     {@code TENURE_TYPE}, {@code CLIENT_NAME}) and directions; may be empty
   */
  public RoadPage searchRoads(RoadSearchCriteria criteria, int pageNumber, int pageSize,
      List<RoadSort> sorts) {
    RoadPage page = http.get()
        .uri(uriBuilder -> {
          uriBuilder.pathSegment(API_V1).pathSegment("roads");
          queryParam(uriBuilder, "roadName", criteria.roadName());
          queryParam(uriBuilder, "roadId", criteria.roadId());
          queryParam(uriBuilder, "sectionId", criteria.sectionId());
          queryParam(uriBuilder, "tenureType", criteria.tenureType());
          queryParam(uriBuilder, "clientName", criteria.clientName());
          uriBuilder.queryParam("pageNumber", pageNumber);
          uriBuilder.queryParam("pageSize", pageSize);
          for (RoadSort sort : sorts == null ? List.<RoadSort>of() : sorts) {
            uriBuilder.queryParam("sortBy", sort.column());
            uriBuilder.queryParam("sortDirection", sort.direction());
          }
          URI uri = uriBuilder.build();
          LOG.debug("lrmops-api road search: {}", uri);
          return uri;
        })
        .headers(this::applyAuth)
        .retrieve()
        .body(RoadPage.class);
    return page == null ? new RoadPage(List.of(), 0, 0, pageNumber, pageSize) : page;
  }

  /**
   * {@code GET /api/v1/roads/{roadId}/sections/{sectionId}}: one road section, matched exactly
   * (case-insensitive). Empty when LRMOPS has no such section.
   */
  public Optional<RoadSection> getRoadSection(String roadId, String sectionId) {
    if (!StringUtils.hasText(roadId) || !StringUtils.hasText(sectionId)) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(http.get()
          .uri(uriBuilder -> uriBuilder.pathSegment(API_V1)
              .pathSegment("roads", roadId.trim(), "sections", sectionId.trim())
              .build())
          .headers(this::applyAuth)
          .retrieve()
          .body(RoadSection.class));
    } catch (HttpClientErrorException ex) {
      if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
        return Optional.empty();
      }
      throw ex;
    }
  }

  /**
   * {@code GET /api/v1/road-tenure-types}: LRMOPS's road tenure types (e.g. {@code FSR},
   * {@code RP}) with their descriptions — what {@link RoadSearchCriteria#tenureType()} matches.
   */
  public List<TenureType> getRoadTenureTypes() {
    TenureType[] types = http.get()
        .uri(uriBuilder -> uriBuilder.pathSegment(API_V1).pathSegment("road-tenure-types").build())
        .headers(this::applyAuth)
        .retrieve()
        .body(TenureType[].class);
    return types == null ? List.of() : List.of(types);
  }

  /**
   * {@code GET /api/v1/roads/names}: road names containing {@code term}, those starting with it
   * first, at most {@code limit}. Empty for a term under two characters.
   */
  public List<String> getRoadNames(String term, int limit) {
    String[] names = http.get()
        .uri(uriBuilder -> uriBuilder.pathSegment(API_V1).pathSegment("roads", "names")
            .queryParam("search", term == null ? "" : term.trim())
            .queryParam("limit", limit)
            .build())
        .headers(this::applyAuth)
        .retrieve()
        .body(String[].class);
    return names == null ? List.of() : List.of(names);
  }

  /**
   * {@code GET /api/v1/roads/sections}: every road section named exactly {@code roadName}
   * (case-insensitive, runs of spaces counting as one).
   */
  public List<SectionKey> getRoadSectionsNamed(String roadName) {
    SectionKey[] keys = http.get()
        .uri(uriBuilder -> uriBuilder.pathSegment(API_V1).pathSegment("roads", "sections")
            .queryParam("roadName", roadName == null ? "" : roadName.trim())
            .build())
        .headers(this::applyAuth)
        .retrieve()
        .body(SectionKey[].class);
    return keys == null ? List.of() : List.of(keys);
  }

  /**
   * {@code POST /api/v1/roads/sections/lookup}: the sections asked for that LRMOPS has, with their
   * names. At most 1,000 a call; the caller batches.
   */
  public List<RoadSummary> lookUpRoadSections(Collection<SectionKey> keys) {
    RoadSummary[] found = http.post()
        .uri(uriBuilder -> uriBuilder.pathSegment(API_V1)
            .pathSegment("roads", "sections", "lookup").build())
        .headers(this::applyAuth)
        .contentType(MediaType.APPLICATION_JSON)
        .body(List.copyOf(keys))
        .retrieve()
        .body(RoadSummary[].class);
    return found == null ? List.of() : List.of(found);
  }

  // ── Internals ─────────────────────────────────────────────────────

  private void applyAuth(HttpHeaders headers) {
    if (clientCredentials == null) {
      return;
    }
    String token = clientCredentials.fetchCached();
    if (!token.isBlank()) {
      headers.setBearerAuth(token);
    }
  }

  private static void queryParam(UriBuilder uriBuilder, String name, String value) {
    if (StringUtils.hasText(value)) {
      uriBuilder.queryParam(name, value.trim());
    }
  }

  // ── Local records (do not leak the API's DTO types) ────────────────

  /** Search criteria; blank ones are left out of the request. */
  public record RoadSearchCriteria(
      String roadName, String roadId, String sectionId, String tenureType, String clientName) {
  }

  /** A road section by road id and section id, as the API takes and returns them. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SectionKey(String roadId, String sectionId) {
  }

  /** A sort column and {@code ASC}/{@code DESC}. */
  public record RoadSort(String column, String direction) {
  }

  /** One road section in the search results. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RoadSummary(
      String roadId,
      String sectionId,
      String roadName,
      String tenureType,
      boolean retired,
      String clientName) {
  }

  /** A page of search results, with the total. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RoadPage(
      List<RoadSummary> content,
      long totalElements,
      int totalPages,
      int pageNumber,
      int pageSize) {
    public RoadPage {
      content = content == null ? List.of() : content;
    }
  }

  /** A road tenure type and its description. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TenureType(String code, String description) {
  }

  /**
   * A district a road section lies in.
   *
   * @param lengthMetres how much of the section lies in this district, when the API says; null
   *     until it does
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record District(Long orgUnitNo, String code, Double lengthMetres) {
    public District(Long orgUnitNo, String code) {
      this(orgUnitNo, code, null);
    }
  }

  /** One road section's details. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RoadSection(
      String roadId,
      String sectionId,
      String roadName,
      String tenureType,
      boolean retired,
      List<District> districts,
      String bctsOffice,
      String clientName) {
    public RoadSection {
      districts = districts == null ? List.of() : districts;
    }
  }
}
