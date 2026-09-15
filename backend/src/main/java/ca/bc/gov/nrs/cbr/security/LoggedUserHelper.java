package ca.bc.gov.nrs.cbr.security;

import ca.bc.gov.nrs.cbr.util.JwtPrincipalUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring bean exposing authorization helpers for the currently authenticated user.
 *
 * <p>Registered as {@code @auth} for programmatic use in services and security configuration.
 *
 * <h3>Everything is read straight off the access token</h3>
 * BC Gov SSO puts the profile claims CBR needs ({@code idir_username}, {@code identity_provider},
 * {@code display_name}) on the <em>access</em> token, alongside the roles. There is no per-request
 * userinfo call and no claim-merging: the Cognito-era {@code /oauth2/userInfo} round trip — which
 * existed only because Cognito withheld those claims from access tokens — is gone.
 *
 * <p>If {@link #getLoggedUserId()} starts returning GUIDs rather than usernames, the claims were
 * mapped onto the ID token only. That is a CSS console setting, not a code change — see
 * {@link JwtPrincipalUtil}.
 */
@Component("auth")
public class LoggedUserHelper {

  // ─── Identity helpers ──────────────────────────────────────────────
  /**
   * Get the ID from the logged user (e.g. {@code IDIR\jsmith}) — the legacy source-directory
   * string CBR's audit columns hold ({@code ENTRY_USERID}, {@code UPDATE_USERID}) and the value
   * {@code STRUCTURE_INSPECTION_REVIEWER.USERID} is matched against. Built from the access token's
   * {@code identity_provider} and {@code idir_username} / {@code bceid_username} claims.
   */
  public String getLoggedUserId() {
    return JwtPrincipalUtil.getUserId(getPrincipal().getClaims());
  }

  /** The user's display name from the token's {@code display_name} claim, or an empty string. */
  public String getLoggedUserDisplayName() {
    return JwtPrincipalUtil.getDisplayName(getPrincipal().getClaims());
  }

  // ─── Role / authority helpers (roles ride the access token — see Oauth2SecurityCustomizer) ──

  /**
   * Returns the set of authority strings for the current user (e.g. {@code CBR_ADMIN}).
   */
  public Set<String> getAuthorities() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Set.of();
    }
    return authentication.getAuthorities()
        .stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  /**
   * Returns {@code true} if the user holds {@link CbrRoles#ADMIN}.
   *
   * <p>Off the ladder: this says nothing about read or write access, because the legacy
   * {@code CBR_ADMINISTRATOR} profile carries neither.
   */
  public boolean isSysAdmin() {
    return CbrRoles.isAdministrator(getAuthorities());
  }

  /**
   * The caller's single effective ladder role, or {@code null} if they hold none — which includes an
   * administrator. Capabilities are never the union of several roles; see {@link CbrRoles}.
   */
  public String effectiveRole() {
    return CbrRoles.effectiveRole(getAuthorities());
  }

  /** True when the caller's effective ladder role is at or above {@code minimum}. */
  public boolean isAtLeast(String minimum) {
    return CbrRoles.isAtLeast(getAuthorities(), minimum);
  }

  /**
   * Returns {@code true} if the user holds the general read/write authority
   * ({@code CBR_GENERAL}).
   */
  public boolean isGeneral() {
    return getAuthorities().contains(CbrRoles.GENERAL);
  }

  /**
   * Returns {@code true} if the user holds the ministry-engineer authority
   * ({@code CBR_ENGINEER}).
   */
  public boolean isEngineer() {
    return getAuthorities().contains(ObsoleteRoles.ENGINEER_AUTHORITY);
  }

  /**
   * Returns {@code true} if the user may perform write operations — any of the global write
   * authorities. Region-scoped permissions are checked separately; see {@link #canRegion(String)}.
   */
  public boolean canWrite() {
    return canEdit();
  }

  // ─── Region capability helpers (region-scoped engineer access) ────

  /**
   * The set of org-unit codes the user holds a regional-engineer role for, parsed from the
   * {@code CBR_REGIONAL_ENGINEER_<code>} authorities. Empty when the user holds none. Codes are
   * upper-cased so comparisons against {@code ORG_UNIT_CODE} are case-insensitive.
   *
   * <p>Same mechanism as nr-frep's per-district CHR roles: the scope rides in the role name, so FAM
   * can grant it without CBR needing a user-to-region table of its own.
   */
  public Set<String> regionalEngineerCodes() {
    return codesWithPrefix(ObsoleteRoles.REGIONAL_ENGINEER_PREFIX);
  }

  /**
   * The set of org-unit codes the user holds a <em>contract</em> regional-engineer role for, parsed
   * from {@code CBR_CONTRACT_REGIONAL_ENGINEER_<code>}. Narrower than
   * {@link #regionalEngineerCodes()} — legacy {@code CBR_CONTRACT_REGIONAL_ENGINEER} held only
   * {@code UPDATE_SITE}.
   */
  public Set<String> contractEngineerCodes() {
    return codesWithPrefix(ObsoleteRoles.CONTRACT_ENGINEER_PREFIX);
  }

  /**
   * True if the user holds a role carrying the general create/edit surface.
   *
   * <p>{@code CBR_GENERAL} is deliberately not among them — the export shows all 24 of its
   * privileges are {@code /show*}, so a read-only user must fail this. Floor is
   * {@link CbrRoles#LEVEL_1} — the same floor {@link CbrAuthorities#CONTENT_EDIT} uses.
   */
  public boolean canEdit() {
    return isAtLeast(CbrRoles.LEVEL_1);
  }

  /** True when the caller may record or amend an inspection. Floor {@link CbrRoles#LEVEL_0}. */
  public boolean canWriteInspection() {
    return isAtLeast(CbrRoles.LEVEL_0);
  }

  /** True when the caller may delete, archive or override inspection status. Floor {@link CbrRoles#LEVEL_2}. */
  public boolean canDestroy() {
    return isAtLeast(CbrRoles.LEVEL_2);
  }

  /**
   * True when the caller may read CBR's records — any ladder role, or {@link CbrRoles#ADMIN}.
   *
   * <p>Not a rank comparison: an administrator holds no ladder role, so {@code isAtLeast(GENERAL)}
   * would deny them. See {@link CbrRoles#READERS}.
   */
  public boolean canRead() {
    return CbrRoles.canRead(getAuthorities());
  }

  /**
   * True if the user may review and seal inspection reports.
   *
   * <p><b>Sys-admin does NOT imply this.</b> An earlier version returned
   * {@code isSysAdmin() || PENG}, on the usual assumption that an administrator can do anything.
   * The WebADE export disproves it: {@code ADMINISTRATOR} holds exactly three privileges
   * ({@code /showBulletinAdmin}, {@code /showInspectionReviewerAdmin}, {@code /showWelcome}) and the
   * {@code CBR_ADMINISTRATOR} profile bundles no other role, while {@code /approveInspection} is
   * held by {@code PROFESSIONAL_ENGINEER} alone.
   *
   * <p>That separation is the point: sealing an inspection is a professional engineering act tied to
   * a named P.Eng, not an administrative one. Holding the role is still only half of it — the
   * workflow also binds to a {@code STRUCTURE_INSPECTION_REVIEWER} row keyed by userid.
   */
  public boolean isPeng() {
    return getAuthorities().contains(CbrRoles.PENG);
  }

  /**
   * True if the user holds a region-scoped role for <em>any</em> region — sys-admin, or at least one
   * {@code CBR_REGIONAL_ENGINEER_*}. Backs {@link CbrAuthorities#REGIONAL_ENGINEER} and
   * {@link CbrAuthorities#DESTRUCTIVE} on id-less endpoints.
   */
  public boolean canAnyRegion() {
    return isSysAdmin() || !regionalEngineerCodes().isEmpty();
  }

  /**
   * True if the user holds <em>any</em> recognised role at all, global or region-scoped. Used to
   * decide whether to admit the caller to the app rather than route them to the role-error page —
   * a region-only user holds no global role and would otherwise look role-less.
   */
  public boolean hasAnyRegion() {
    return canAnyRegion() || !contractEngineerCodes().isEmpty();
  }

  /**
   * True if the user may perform regional-engineer operations — the destructive ones — for the given
   * org-unit code. Sys-admins pass for every region.
   *
   * <p>Callers that have a structure or site id should go through {@link CbrStructureAuthorizer}
   * instead, which resolves the record's org unit first.
   */
  public boolean canRegion(String orgUnitCode) {
    if (isSysAdmin()) {
      return true;
    }
    return orgUnitCode != null
        && regionalEngineerCodes().contains(orgUnitCode.toUpperCase(Locale.ROOT));
  }

  /**
   * True if the user may update a site in the given region — a regional engineer, or a contract
   * regional engineer whose single legacy privilege was exactly {@code UPDATE_SITE}.
   */
  public boolean canUpdateSite(String orgUnitCode) {
    if (canRegion(orgUnitCode)) {
      return true;
    }
    return orgUnitCode != null
        && contractEngineerCodes().contains(orgUnitCode.toUpperCase(Locale.ROOT));
  }

  /** Parses the org-unit suffix out of every authority carrying {@code prefix}. */
  private Set<String> codesWithPrefix(String prefix) {
    return getAuthorities().stream()
        .filter(authority -> authority.startsWith(prefix))
        .map(authority -> authority.substring(prefix.length()))
        .filter(code -> !code.isBlank())
        .map(code -> code.toUpperCase(Locale.ROOT))
        .collect(Collectors.toSet());
  }

  // ─── Internal helpers ─────────────────────────────────────────────

  /**
   * Returns the raw {@link Jwt} principal from the security context.
   */
  private Jwt getPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof Jwt jwtPrincipal) {
      return jwtPrincipal;
    }
    throw new IllegalStateException("No authenticated JWT principal available");
  }

}
