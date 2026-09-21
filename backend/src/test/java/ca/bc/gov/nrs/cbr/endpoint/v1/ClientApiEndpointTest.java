package ca.bc.gov.nrs.cbr.endpoint.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Pins the lookup's published contract.
 *
 * <p>Both halves matter and neither fails loudly on its own. A lost {@code @PreAuthorize} leaves a
 * working endpoint that hands maintainer names to any authenticated caller; a changed path leaves
 * the combo box silently suggesting nothing, because a request that matches no mapping in Spring
 * falls through to the static-resource handler rather than erroring anywhere near the cause.
 */
class ClientApiEndpointTest {

  private static Method searchClients() throws NoSuchMethodException {
    return ClientApiEndpoint.class.getMethod("searchClients", String.class);
  }

  @Test
  @DisplayName("is published under the versioned API path")
  void mappedUnderApiV1() {
    RequestMapping mapping = ClientApiEndpoint.class.getAnnotation(RequestMapping.class);

    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/api/v1/clients");
  }

  @Test
  @DisplayName("is a GET on the collection itself")
  void mappedAsAGet() throws NoSuchMethodException {
    // No sub-path: the resource is the client collection and the term narrows it, which is what a
    // query parameter is for. "/search" would make the same request twice-named.
    GetMapping mapping = searchClients().getAnnotation(GetMapping.class);

    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).isEmpty();
  }

  @Test
  @DisplayName("takes an optional term that defaults to empty rather than being required")
  void termIsOptional() throws NoSuchMethodException {
    // A required parameter would turn a cleared field into a 400. The service answers an empty
    // term with an empty list, which is the same thing said without an error.
    RequestParam term = searchClients().getParameters()[0].getAnnotation(RequestParam.class);

    assertThat(term).isNotNull();
    assertThat(term.name()).isEqualTo("term");
    assertThat(term.defaultValue()).isEmpty();
  }

  @Test
  @DisplayName("is gated on READ, the same capability as the search form it fills in")
  void gatedOnRead() throws NoSuchMethodException {
    PreAuthorize guard = searchClients().getAnnotation(PreAuthorize.class);

    assertThat(guard).isNotNull();
    assertThat(guard.value()).isEqualTo(CbrAuthorities.READ);
  }
}
