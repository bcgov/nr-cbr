package ca.bc.gov.nrs.cbr.endpoint.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Pins the HTTP contract and, above all, that it is guarded.
 *
 * <p>A missing {@code @PreAuthorize} is the failure mode worth a test: the endpoint keeps working,
 * the tests keep passing, and an unauthenticated or role-less caller quietly receives site data.
 * Reflection rather than a {@code MockMvc} slice so the assertion is about the declared contract
 * itself, which is what the interface exists to hold.
 */
class SiteSearchApiEndpointTest {

  private static Method searchSites() {
    return Arrays.stream(SiteSearchApiEndpoint.class.getDeclaredMethods())
        .filter(method -> method.getName().equals("searchSites"))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("is published under the versioned API path")
  void mappedUnderApiV1() {
    RequestMapping mapping = SiteSearchApiEndpoint.class.getAnnotation(RequestMapping.class);

    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/api/v1/sites");
    assertThat(searchSites().getAnnotation(GetMapping.class).value()).containsExactly("/search");
  }

  @Test
  @DisplayName("is gated on READ — the legacy /showSiteSearch privilege")
  void requiresRead() {
    PreAuthorize preAuthorize = searchSites().getAnnotation(PreAuthorize.class);

    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo(CbrAuthorities.READ);
  }

  @Test
  @DisplayName("takes the criteria as one bound object, not a flat parameter list")
  void bindsCriteriaAsAnObject() {
    // 17 criteria as positional parameters would have to be repeated identically in the interface,
    // the controller override and the service call, where transposing two same-typed neighbours
    // compiles and returns the wrong rows.
    assertThat(searchSites().getParameterTypes())
        .containsExactly(SiteSearchCriteria.class, int.class, int.class);
  }
}
