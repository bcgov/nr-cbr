package ca.bc.gov.nrs.cbr.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Canonical CBR role names, and the ladder they form.
 *
 * <p>FAM holds nothing but these six flat strings — no nesting, no scoping, no permissions. The
 * role&rarr;capability matrix lives here and in {@link CbrAuthorities}, which is the same split
 * nr-fspts uses ({@code FsptsRoles} + {@code FspAuthorities}).
 *
 * <h2>Two axes, not one</h2>
 * Five roles form a <b>cumulative ladder</b> — each rung carries everything below it:
 *
 * <pre>
 *   CBR_GENERAL  &lt;  CBR_LEVEL_0  &lt;  CBR_LEVEL_1  &lt;  CBR_LEVEL_2  &lt;  CBR_PENG
 *   read-only       + record an     + create/edit   + delete,        + sign off
 *                     inspection      sites,          archive,         inspections
 *                     (offline)       structures      add site,
 *                                                     status override
 * </pre>
 *
 * That ladder is not invented: it is exactly how the legacy WebADE profiles nest
 * ({@code CBR_READ_ONLY} &sub; {@code CBR_INSPECTOR_TECHNICIAN_0/1/2} &sub;
 * {@code CBR_MINISTRY_PENG}), each bundling every role beneath it. See
 * {@code cbr-auth-and-roles.local.md} §3.
 *
 * <p><b>{@link #ADMIN} is not on the ladder.</b> It is a separate axis: it carries the two admin
 * screens plus read ({@link #READERS}), and no write capability at any level. It is therefore
 * excluded from {@link #LADDER} and from {@link #effectiveRole} — ask {@link #isAdministrator} or
 * {@link #canRead} instead. Giving it a rung would either hand administrators the whole application
 * or place them below a read-only user, and neither is intended.
 *
 * <h2>No role stacking</h2>
 * A user resolves to a single <b>effective</b> ladder role — capabilities are never the union of
 * several. Legacy assigns one profile per person, so in practice one role arrives; when more than
 * one does, {@link #effectiveRole} takes the highest rung rather than trying to merge them.
 */
public final class CbrRoles {

  private CbrRoles() {}

  /** Read-only: search, view, and all twelve reports. Legacy WebADE {@code GENERAL}. */
  public static final String GENERAL = "CBR_GENERAL";

  /** Field capture: record an inspection and use the offline client. Legacy {@code LEVEL_0}. */
  public static final String LEVEL_0 = "CBR_LEVEL_0";

  /** Create and edit sites, structures and inspections; uploads. Legacy {@code LEVEL_1}. */
  public static final String LEVEL_1 = "CBR_LEVEL_1";

  /** Delete, archive, add site, override inspection status. Legacy {@code LEVEL_2}. */
  public static final String LEVEL_2 = "CBR_LEVEL_2";

  /** Sign off inspections — {@code /approveInspection}. Legacy {@code PROFESSIONAL_ENGINEER}. */
  public static final String PENG = "CBR_PENG";

  /**
   * Bulletin and inspection-reviewer administration. Legacy {@code ADMINISTRATOR}.
   *
   * <p>Off the ladder — see the class javadoc. Carries no read access.
   */
  public static final String ADMIN = "CBR_ADMIN";

  /**
   * The cumulative ladder, <b>lowest rung first</b>. Index is the rank; a role at index <i>n</i>
   * carries every capability of the roles below it.
   *
   * <p>Order is authorization. Reordering this list silently changes who can do what, which is why
   * {@link CbrAuthorities} is pinned against it by a test rather than kept in step by hand.
   */
  public static final List<String> LADDER = List.of(GENERAL, LEVEL_0, LEVEL_1, LEVEL_2, PENG);

  /** Every role FAM issues — the ladder plus {@link #ADMIN}. */
  public static final List<String> ALL =
      List.of(GENERAL, LEVEL_0, LEVEL_1, LEVEL_2, PENG, ADMIN);

  /**
   * Roles that carry read access: the whole ladder, <b>plus {@link #ADMIN}</b>.
   *
   * <p><b>This is a deliberate divergence from legacy</b>, decided 2026-09-15. In WebADE the
   * {@code ADMINISTRATOR} role held exactly three privileges — {@code /showBulletinAdmin},
   * {@code /showInspectionReviewerAdmin}, {@code /showWelcome} — and the {@code CBR_ADMINISTRATOR}
   * profile bundled no {@code GENERAL}, so an administrator could not open a site, a structure or an
   * inspection. CBR now grants them the read surface: search, view, and the reports.
   *
   * <p>Read only. An administrator still writes nothing — not an inspection, not a site, and
   * certainly not a sign-off. Every write capability in {@link CbrAuthorities} continues to exclude
   * {@link #ADMIN}, which is what keeps the separation of duties the legacy design was expressing.
   */
  public static final List<String> READERS =
      List.of(GENERAL, LEVEL_0, LEVEL_1, LEVEL_2, PENG, ADMIN);

  /**
   * The role's rung, or {@code -1} if it is not on the ladder — which covers {@link #ADMIN}, any
   * unrecognised string, and {@code null}.
   *
   * <p>The null guard is load-bearing, not defensive padding: {@link #LADDER} is a
   * {@code List.of(...)}, and immutable lists throw {@link NullPointerException} from
   * {@code indexOf(null)} rather than returning {@code -1}. Both {@link #effectiveRole} (which
   * starts with no candidate) and {@link #isAtLeast} (which ranks a possibly-absent effective role)
   * reach this with {@code null} on the ordinary path.
   */
  public static int rank(String role) {
    return role == null ? -1 : LADDER.indexOf(role);
  }

  /**
   * The highest ladder role present, or {@code null} if none is — which includes the case of a user
   * holding only {@link #ADMIN}.
   */
  public static String effectiveRole(Collection<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return null;
    }
    String best = null;
    for (String role : roles) {
      if (rank(role) > rank(best)) {
        best = role;
      }
    }
    return best;
  }

  /**
   * True when the caller's effective ladder role is at least {@code minimum}.
   *
   * <p>Fails closed: an unknown {@code minimum} ranks {@code -1}, and so would be satisfied by
   * anything — so it is rejected outright rather than silently granting.
   */
  public static boolean isAtLeast(Collection<String> roles, String minimum) {
    int required = rank(minimum);
    if (required < 0) {
      throw new IllegalArgumentException("Not a ladder role: " + minimum);
    }
    return rank(effectiveRole(roles)) >= required;
  }

  /** True when the caller holds {@link #ADMIN}. Independent of the ladder. */
  public static boolean isAdministrator(Collection<String> roles) {
    return roles != null && roles.contains(ADMIN);
  }

  /**
   * True when the caller may read CBR's records — any ladder role, or {@link #ADMIN}.
   *
   * <p>Not {@code isAtLeast(GENERAL)}: an administrator holds no ladder role at all, so a rank
   * comparison would deny them. See {@link #READERS}.
   */
  public static boolean canRead(Collection<String> roles) {
    return roles != null && roles.stream().anyMatch(READERS::contains);
  }

  /**
   * The ladder from {@code minimum} upward — every role that satisfies a capability whose floor is
   * {@code minimum}. Used to generate and to verify the {@link CbrAuthorities} expressions.
   */
  public static List<String> atLeast(String minimum) {
    int required = rank(minimum);
    if (required < 0) {
      throw new IllegalArgumentException("Not a ladder role: " + minimum);
    }
    return List.copyOf(LADDER.subList(required, LADDER.size()));
  }

  /**
   * Renders a role list as a {@code hasAnyAuthority(...)} argument list, so the constants in
   * {@link CbrAuthorities} can be checked against the ladder rather than proof-read.
   */
  static String asAuthorityList(Collection<String> roles) {
    List<String> quoted = new ArrayList<>(roles.size());
    for (String role : roles) {
      quoted.add("'" + role + "'");
    }
    return String.join(",", quoted);
  }

  /**
   * Whether a role string issued by FAM is one of ours.
   *
   * <p><b>Exact match only — deliberately unlike nr-fspts</b>, whose {@code canonicalRoleFor} also
   * accepts {@code <role>_<suffix>} because FSPTS scopes roles by organization. CBR scopes nothing:
   * the WebADE export carries no org unit on any grant, and the region-scoped role names that used
   * to be in this codebase were imported from nr-frep by mistake. Accepting a suffix here would
   * quietly resurrect that — {@code CBR_LEVEL_2_DCK} would resolve to {@code CBR_LEVEL_2} and grant
   * province-wide delete to someone a FAM admin believed was scoped to one district.
   */
  public static boolean isKnown(String role) {
    return ALL.contains(role);
  }
}
