package ca.bc.gov.nrs.cbr.security;

import ca.bc.gov.nrs.cbr.util.JwtPrincipalUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring bean exposing authorization helpers for the currently authenticated user.
 *
 * <p>Registered as {@code @auth} for programmatic use in services and security configuration.
 *
 * <h3>Access-token migration note</h3>
 * The frontend now sends a Cognito <em>access token</em> (not an ID token).
 * Access tokens contain {@code cognito:groups} and {@code sub} but lack the
 * {@code custom:idp_*} profile claims that the identity helpers below depend on.
 * Those claims are fetched on demand from the Cognito {@code /oauth2/userInfo}
 * endpoint (via {@link CognitoUserInfoService}) and merged into a synthetic
 * claims map so that existing {@link JwtPrincipalUtil} methods continue to work
 * without modification.
 */
@Component("auth")
public class LoggedUserHelper {

  private final CognitoUserInfoService userInfoService;

  public LoggedUserHelper(CognitoUserInfoService userInfoService) {
    this.userInfoService = userInfoService;
  }

  // ─── Identity helpers ──────────────────────────────────────────────
  /**
   * Get the ID from the logged user (e.g. {@code IDIR\jsmith}).
   * Requires the {@code custom:idp_username} and {@code custom:idp_name} claims
   * which are obtained from the Cognito userInfo endpoint.
   */
  public String getLoggedUserId() {
    return JwtPrincipalUtil.getUserId(getEnrichedClaims());
  }
  // ─── Role / authority helpers (these use cognito:groups from the access token) ──

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
   * Returns {@code true} if the user holds the {@code CBR_ADMIN} authority.
   */
  public boolean isSysAdmin() {
    return getAuthorities().contains(RoleConstants.SYS_ADMIN_AUTHORITY);
  }

  /**
   * Returns {@code true} if the user holds the general read/write authority
   * ({@code CBR_GENERAL}).
   */
  public boolean isGeneral() {
    return getAuthorities().contains(RoleConstants.GENERAL_AUTHORITY);
  }

  /**
   * Returns {@code true} if the user holds the ministry-engineer authority
   * ({@code CBR_ENGINEER}).
   */
  public boolean isEngineer() {
    return getAuthorities().contains(RoleConstants.ENGINEER_AUTHORITY);
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
    return codesWithPrefix(RoleConstants.REGIONAL_ENGINEER_PREFIX);
  }

  /**
   * The set of org-unit codes the user holds a <em>contract</em> regional-engineer role for, parsed
   * from {@code CBR_CONTRACT_REGIONAL_ENGINEER_<code>}. Narrower than
   * {@link #regionalEngineerCodes()} — legacy {@code CBR_CONTRACT_REGIONAL_ENGINEER} held only
   * {@code UPDATE_SITE}.
   */
  public Set<String> contractEngineerCodes() {
    return codesWithPrefix(RoleConstants.CONTRACT_ENGINEER_PREFIX);
  }

  /** True if the user holds any global role that permits writing. */
  public boolean canEdit() {
    Set<String> authorities = getAuthorities();
    return Arrays.stream(RoleConstants.WRITE_AUTHORITIES).anyMatch(authorities::contains);
  }

  /** True if the user may review and seal inspection reports (P.Eng, or sys-admin). */
  public boolean isPeng() {
    return isSysAdmin() || getAuthorities().contains(RoleConstants.PENG_AUTHORITY);
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

  /**
   * Builds a merged claims map that contains:
   * <ol>
   *   <li>All claims from the access token (cognito:groups, sub, etc.)</li>
   *   <li>Profile claims from the Cognito userInfo endpoint
   *       (custom:idp_name, custom:idp_username, email, etc.)</li>
   * </ol>
   * UserInfo claims do NOT overwrite access-token claims if there's a collision.
   */
  private Map<String, Object> getEnrichedClaims() {
    Jwt accessToken = getPrincipal();
    Map<String, Object> userInfoClaims = userInfoService.getUserInfo(accessToken);

    // Start with userInfo (lower precedence), overlay with access token claims
    java.util.HashMap<String, Object> merged = new java.util.HashMap<>(userInfoClaims);
    merged.putAll(accessToken.getClaims());
    return merged;
  }

}
