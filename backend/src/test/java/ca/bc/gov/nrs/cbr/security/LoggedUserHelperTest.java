package ca.bc.gov.nrs.cbr.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Covers the capability gates in {@link LoggedUserHelper} — that they agree with the ladder in
 * {@link CbrRoles} and with the {@code @PreAuthorize} expressions in {@link CbrAuthorities}.
 *
 * <p>The identity helpers are not covered here — see {@code JwtPrincipalUtilTest} for those.
 */
class LoggedUserHelperTest {

  private final LoggedUserHelper helper = new LoggedUserHelper();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateWith(String... authorities) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "tester",
            "n/a",
            List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
  }

  @Test
  @DisplayName("CBR_GENERAL is read-only: it must not pass a write gate")
  void generalIsReadOnly() {
    // Pinned against the WebADE export: all 24 of GENERAL's ACTION_LNK rows are /show* privileges,
    // and CBR_READ_ONLY is a profile holding GENERAL and nothing else. The Oracle role of the same
    // name IS broad — the CBR_GENERAL package carries most of the write surface — so the temptation
    // to treat this as a write role is real, and it would hand every read-only user the edit
    // surface. The application-layer check is the only thing that ever constrained them.
    authenticateWith(CbrRoles.GENERAL);

    assertThat(helper.canEdit()).isFalse();
    assertThat(helper.canWrite()).isFalse();
    assertThat(helper.isPeng()).isFalse();
  }

  @Test
  @DisplayName("Level 1 and above carry the create/edit surface")
  void writeAuthoritiesGrantEdit() {
    for (String role : CbrRoles.atLeast(CbrRoles.LEVEL_1)) {
      SecurityContextHolder.clearContext();
      authenticateWith(role);
      assertThat(helper.canEdit()).as("canEdit for %s", role).isTrue();
      assertThat(helper.canWrite()).as("canWrite for %s", role).isTrue();
    }

    SecurityContextHolder.clearContext();
    authenticateWith(CbrRoles.GENERAL);
    assertThat(helper.canEdit()).isFalse();
  }

  @Test
  @DisplayName("P.Eng is the only role that signs off — sys-admin does NOT imply it")
  void pengAuthority() {
    authenticateWith(CbrRoles.PENG);
    assertThat(helper.isPeng()).isTrue();

    // The export is unambiguous: /approveInspection is held by PROFESSIONAL_ENGINEER alone, and the
    // CBR_ADMINISTRATOR profile bundles no role other than ADMINISTRATOR — which carries three
    // privileges, none of them /approveInspection or /pEngAccess. Sealing an inspection is a
    // professional engineering act, not an administrative one; letting an admin through here would
    // put a non-engineer's name on a sealed engineering document.
    SecurityContextHolder.clearContext();
    authenticateWith(CbrRoles.ADMIN);
    assertThat(helper.isPeng()).isFalse();

    SecurityContextHolder.clearContext();
    authenticateWith(CbrRoles.GENERAL);
    assertThat(helper.isPeng()).isFalse();
  }

  @Test
  @DisplayName("an unauthenticated context yields no authorities rather than throwing")
  void unauthenticatedYieldsNoAuthorities() {
    assertThat(helper.getAuthorities()).isEqualTo(Set.of());
    assertThat(helper.canRead()).isFalse();
    assertThat(helper.canEdit()).isFalse();
  }
}
