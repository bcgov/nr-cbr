package ca.bc.gov.nrs.cbr.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link CbrAuthorities} expressions to {@link CbrRoles#LADDER}.
 *
 * <p>The SpEL constants have to be string literals — {@code @PreAuthorize} takes a compile-time
 * constant — so they cannot be generated from the ladder. That leaves them free to drift from it,
 * and a drift here is not a crash but a silent re-grant. These tests rebuild each expression from
 * the ladder and compare, so reordering {@link CbrRoles#LADDER} or editing a constant by hand fails
 * the build.
 */
class CbrAuthoritiesTest {

  /** Rebuilds the SpEL a capability with this floor should carry, straight from the ladder. */
  private static String fromLadder(String floor) {
    return "hasAnyAuthority(" + CbrRoles.asAuthorityList(CbrRoles.atLeast(floor)) + ")";
  }

  @Test
  @DisplayName("each write capability admits exactly its floor and everything above it")
  void expressionsMatchTheLadder() {
    String inspectionWrite = CbrAuthorities.INSPECTION_WRITE;
    String contentEdit = CbrAuthorities.CONTENT_EDIT;
    String destructive = CbrAuthorities.DESTRUCTIVE;

    assertThat(inspectionWrite).isEqualTo(fromLadder(CbrRoles.LEVEL_0));
    assertThat(contentEdit).isEqualTo(fromLadder(CbrRoles.LEVEL_1));
    assertThat(destructive).isEqualTo(fromLadder(CbrRoles.LEVEL_2));
  }

  @Test
  @DisplayName("APPROVE is P.Eng alone — the top of the ladder, so hasAuthority not hasAnyAuthority")
  void approveIsPengOnly() {
    String approve = CbrAuthorities.APPROVE;

    assertThat(CbrRoles.atLeast(CbrRoles.PENG)).containsExactly(CbrRoles.PENG);
    assertThat(approve).isEqualTo("hasAuthority('CBR_PENG')");
  }

  @Test
  @DisplayName("READ admits every ladder role plus CBR_ADMIN")
  void readAdmitsAdministrators() {
    // A deliberate divergence from legacy, decided 2026-09-15: in WebADE the ADMINISTRATOR role held
    // three privileges and its profile bundled no GENERAL, so an administrator could not open a
    // site. CBR grants them the read surface.
    String read = CbrAuthorities.READ;

    assertThat(read)
        .isEqualTo("hasAnyAuthority(" + CbrRoles.asAuthorityList(CbrRoles.READERS) + ")");
    assertThat(CbrRoles.READERS).containsAll(CbrRoles.LADDER).contains(CbrRoles.ADMIN);
    assertThat(CbrRoles.canRead(Set.of(CbrRoles.ADMIN))).isTrue();
  }

  @Test
  @DisplayName("read is the ONLY thing an administrator gains — every write capability excludes it")
  void administratorsWriteNothing() {
    List<String> writeCapabilities = List.of(
        CbrAuthorities.INSPECTION_WRITE,
        CbrAuthorities.CONTENT_EDIT,
        CbrAuthorities.DESTRUCTIVE,
        CbrAuthorities.APPROVE);

    String admin = CbrAuthorities.ADMIN;

    assertThat(writeCapabilities).noneMatch(expr -> expr.contains(CbrRoles.ADMIN));
    assertThat(admin).isEqualTo("hasAuthority('CBR_ADMIN')");

    // Still off the ladder, so no rank comparison ever admits an administrator to a write gate.
    assertThat(CbrRoles.LADDER).doesNotContain(CbrRoles.ADMIN);
    assertThat(CbrRoles.isAtLeast(Set.of(CbrRoles.ADMIN), CbrRoles.LEVEL_1)).isFalse();
  }

  @Test
  @DisplayName("the ladder is cumulative: each rung admits strictly more than the one above")
  void ladderIsCumulative() {
    for (int i = 0; i < CbrRoles.LADDER.size() - 1; i++) {
      List<String> lower = CbrRoles.atLeast(CbrRoles.LADDER.get(i));
      List<String> higher = CbrRoles.atLeast(CbrRoles.LADDER.get(i + 1));
      assertThat(lower).containsAll(higher).hasSize(higher.size() + 1);
    }
  }

  @Test
  @DisplayName("effective role is the highest rung held, and ADMIN never becomes one")
  void effectiveRole() {
    assertThat(CbrRoles.effectiveRole(Set.of(CbrRoles.GENERAL, CbrRoles.LEVEL_2)))
        .isEqualTo(CbrRoles.LEVEL_2);
    assertThat(CbrRoles.effectiveRole(Set.of(CbrRoles.PENG, CbrRoles.LEVEL_1)))
        .isEqualTo(CbrRoles.PENG);
    assertThat(CbrRoles.effectiveRole(Set.of(CbrRoles.ADMIN))).isNull();
    assertThat(CbrRoles.effectiveRole(Set.of())).isNull();
    assertThat(CbrRoles.effectiveRole(null)).isNull();
    assertThat(CbrRoles.effectiveRole(Set.of("SOMETHING_ELSE"))).isNull();
  }

  @Test
  @DisplayName("isAtLeast compares rungs; an administrator satisfies no rung")
  void isAtLeast() {
    assertThat(CbrRoles.isAtLeast(Set.of(CbrRoles.LEVEL_2), CbrRoles.LEVEL_1)).isTrue();
    assertThat(CbrRoles.isAtLeast(Set.of(CbrRoles.LEVEL_1), CbrRoles.LEVEL_2)).isFalse();
    assertThat(CbrRoles.isAtLeast(Set.of(CbrRoles.ADMIN), CbrRoles.GENERAL)).isFalse();
    assertThat(CbrRoles.isAdministrator(Set.of(CbrRoles.ADMIN))).isTrue();
    assertThat(CbrRoles.isAdministrator(Set.of(CbrRoles.PENG))).isFalse();
  }

  @Test
  @DisplayName("a floor that is not on the ladder is rejected rather than granting everything")
  void unknownFloorFailsClosed() {
    // rank() returns -1 for anything off the ladder, so a silent `>= -1` would be satisfied by
    // every caller — including one with no roles at all.
    Set<String> holder = Set.of(CbrRoles.GENERAL);

    assertThatThrownBy(() -> CbrRoles.isAtLeast(holder, CbrRoles.ADMIN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CbrRoles.atLeast("CBR_NOT_A_ROLE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("role names are matched exactly — a suffixed role is not ours")
  void suffixedRolesAreNotRecognised() {
    // Unlike nr-fspts, which accepts an org suffix. CBR scopes nothing, so CBR_LEVEL_2_DCK must NOT
    // resolve to CBR_LEVEL_2 — that would grant province-wide delete to someone a FAM admin
    // believed was scoped to one district.
    assertThat(CbrRoles.isKnown(CbrRoles.LEVEL_2)).isTrue();
    assertThat(CbrRoles.isKnown("CBR_LEVEL_2_DCK")).isFalse();
    assertThat(CbrRoles.rank("CBR_LEVEL_2_DCK")).isEqualTo(-1);
  }
}
