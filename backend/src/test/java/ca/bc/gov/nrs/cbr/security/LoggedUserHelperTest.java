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
 * Covers the CBR-specific parts of {@link LoggedUserHelper}: how region-scoped roles are parsed out
 * of the token's role strings, and how the global and region-scoped gates combine.
 *
 * <p>The identity helpers are not covered here — see {@code JwtPrincipalUtil} for those.
 *
 * <p>The region-scoped cases below are pinned behaviour for code that is deprecated-for-removal:
 * legacy CBR has no region scoping, so no real token carries a {@code CBR_REGIONAL_ENGINEER_*}
 * role. They go with the D3 cleanup, not before it.
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
  @DisplayName("region codes are parsed out of the role-name suffix")
  void parsesRegionCodesFromRoleNames() {
    authenticateWith(
        "CBR_REGIONAL_ENGINEER_DCK",
        "CBR_REGIONAL_ENGINEER_DSQ",
        "CBR_GENERAL");

    assertThat(helper.regionalEngineerCodes()).containsExactlyInAnyOrder("DCK", "DSQ");
    assertThat(helper.contractEngineerCodes()).isEmpty();
  }

  @Test
  @DisplayName("region codes are upper-cased so comparison against ORG_UNIT_CODE is case-insensitive")
  void upperCasesRegionCodes() {
    authenticateWith("CBR_REGIONAL_ENGINEER_dck");

    assertThat(helper.regionalEngineerCodes()).containsExactly("DCK");
    assertThat(helper.canRegion("DCK")).isTrue();
    assertThat(helper.canRegion("dck")).isTrue();
  }

  @Test
  @DisplayName("a bare prefix with no code is ignored rather than yielding an empty region")
  void ignoresPrefixWithNoSuffix() {
    authenticateWith("CBR_REGIONAL_ENGINEER_");

    assertThat(helper.regionalEngineerCodes()).isEmpty();
    assertThat(helper.canAnyRegion()).isFalse();
  }

  @Test
  @DisplayName("contract engineer roles are kept separate from full regional engineer roles")
  void separatesContractFromFullRegionalEngineer() {
    authenticateWith("CBR_CONTRACT_REGIONAL_ENGINEER_DCK");

    // The contract role's only legacy privilege was UPDATE_SITE, so it must not grant the
    // destructive operations that canRegion() gates.
    assertThat(helper.contractEngineerCodes()).containsExactly("DCK");
    assertThat(helper.regionalEngineerCodes()).isEmpty();
    assertThat(helper.canRegion("DCK")).isFalse();
    assertThat(helper.canUpdateSite("DCK")).isTrue();
    assertThat(helper.canAnyRegion()).isFalse();
    assertThat(helper.hasAnyRegion()).isTrue();
  }

  @Test
  @DisplayName("CBR_CONTRACT_REGIONAL_ENGINEER_ is not mis-parsed as a CBR_REGIONAL_ENGINEER_ role")
  void prefixesDoNotOverlap() {
    // Guards a real hazard: both prefixes contain "REGIONAL_ENGINEER_", so a substring match rather
    // than startsWith would give a contract engineer the destructive privileges.
    authenticateWith("CBR_CONTRACT_REGIONAL_ENGINEER_DCK");

    assertThat(helper.regionalEngineerCodes()).isEmpty();
  }

  @Test
  @DisplayName("sys-admin passes every region check without holding a region role")
  void sysAdminPassesEveryRegion() {
    authenticateWith(CbrRoles.ADMIN);

    assertThat(helper.regionalEngineerCodes()).isEmpty();
    assertThat(helper.canAnyRegion()).isTrue();
    assertThat(helper.canRegion("ANY")).isTrue();
    assertThat(helper.canUpdateSite("ANY")).isTrue();
  }

  @Test
  @DisplayName("a null org unit fails closed for a non-admin")
  void nullOrgUnitFailsClosed() {
    authenticateWith("CBR_REGIONAL_ENGINEER_DCK");

    // CbrStructureAuthorizer returns null until the repositories are wired; that must deny, not allow.
    assertThat(helper.canRegion(null)).isFalse();
    assertThat(helper.canUpdateSite(null)).isFalse();
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
    authenticateWith("CBR_REGIONAL_ENGINEER_DCK");
    assertThat(helper.canEdit()).isFalse();
    assertThat(helper.canAnyRegion()).isTrue();
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
    assertThat(helper.canAnyRegion()).isFalse();
    assertThat(helper.canEdit()).isFalse();
  }
}
