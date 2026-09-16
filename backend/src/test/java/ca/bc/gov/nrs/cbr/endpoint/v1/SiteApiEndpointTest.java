package ca.bc.gov.nrs.cbr.endpoint.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Pins the delete contract, and above all who may call it.
 *
 * <p>The delete is the one operation in CBR that destroys data irreversibly, so the gate on it is
 * worth a test of its own: a {@code @PreAuthorize} silently widened to {@link CbrAuthorities#READ}
 * would leave every reader able to delete sites, with nothing failing and nothing to notice. That
 * risk is the reason the two operations share an interface but not a capability, and why the tests
 * for both live here together — the contrast is the point.
 */
class SiteApiEndpointTest {

  private static Method searchSites() {
    return method("searchSites");
  }

  private static Method deleteSite() {
    return method("deleteSite");
  }

  private static Method method(String name) {
    return Arrays.stream(SiteApiEndpoint.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("is DELETE /api/v1/sites/{siteId}")
  void isMappedAsADelete() {
    assertThat(SiteApiEndpoint.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/sites");
    assertThat(deleteSite().getAnnotation(DeleteMapping.class).value())
        .containsExactly("/{siteId}");
  }

  @Test
  @DisplayName("is gated on DESTRUCTIVE — CBR_LEVEL_2 and CBR_PENG, and nobody else")
  void requiresTheDestructiveCapability() {
    PreAuthorize preAuthorize = deleteSite().getAnnotation(PreAuthorize.class);

    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo(CbrAuthorities.DESTRUCTIVE);
  }

  @Test
  @DisplayName("does not let an administrator delete")
  void administratorsCannotDelete() {
    // CBR_ADMIN reads everything and writes nothing — the separation of duties in §3.2 of
    // cbr-auth-and-roles.local.md. Adding it to DESTRUCTIVE would be a one-word change here and a
    // change of policy in fact, so the expectation is written down rather than left implied.
    assertThat(deleteSite().getAnnotation(PreAuthorize.class).value()).doesNotContain("CBR_ADMIN");
  }

  @Test
  @DisplayName("takes the site id from the path")
  void takesTheSiteIdFromThePath() {
    assertThat(deleteSite().getParameterTypes()).containsExactly(String.class);
  }

  @Test
  @DisplayName("search is GET /api/v1/sites/search")
  void searchIsMappedUnderApiV1() {
    assertThat(searchSites().getAnnotation(GetMapping.class).value()).containsExactly("/search");
  }

  @Test
  @DisplayName("search is gated on READ — the legacy /showSiteSearch privilege")
  void searchRequiresRead() {
    PreAuthorize preAuthorize = searchSites().getAnnotation(PreAuthorize.class);

    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo(CbrAuthorities.READ);
  }

  @Test
  @DisplayName("search takes the criteria as one bound object, not a flat parameter list")
  void searchBindsCriteriaAsAnObject() {
    // 17 criteria as positional parameters would have to be repeated identically in the interface,
    // the controller override and the service call, where transposing two same-typed neighbours
    // compiles and returns the wrong rows.
    assertThat(searchSites().getParameterTypes())
        .containsExactly(SiteSearchCriteria.class, int.class, int.class);
  }

  @Test
  @DisplayName("reading and deleting are not gated alike")
  void theTwoOperationsDifferInAuthority() {
    // The merge put them in one file; it must not put them behind one gate. Every role that can
    // read can search, and two roles can delete.
    assertThat(searchSites().getAnnotation(PreAuthorize.class).value())
        .isNotEqualTo(deleteSite().getAnnotation(PreAuthorize.class).value());
  }
}
