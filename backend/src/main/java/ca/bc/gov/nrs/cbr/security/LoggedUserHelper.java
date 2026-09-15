package ca.bc.gov.nrs.cbr.security;

import ca.bc.gov.nrs.cbr.util.JwtPrincipalUtil;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

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
 *
 * <h3>Capability helpers mirror {@link CbrAuthorities}</h3>
 * The {@code can*} methods below use the same floors as the {@code @PreAuthorize} expressions, so a
 * service-layer check and an endpoint gate cannot disagree. See {@link CbrRoles} for the ladder.
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
   * Returns the set of authority strings for the current user (e.g. {@code CBR_LEVEL_2}).
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
   * <p>Off the ladder: this says nothing about write access, because the legacy
   * {@code CBR_ADMINISTRATOR} profile carried none. It does now imply read — see
   * {@link CbrRoles#READERS}.
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
   * True when the caller may read CBR's records — any ladder role, or {@link CbrRoles#ADMIN}.
   *
   * <p>Not a rank comparison: an administrator holds no ladder role, so {@code isAtLeast(GENERAL)}
   * would deny them. See {@link CbrRoles#READERS}.
   */
  public boolean canRead() {
    return CbrRoles.canRead(getAuthorities());
  }

  /** True when the caller may record or amend an inspection. Floor {@link CbrRoles#LEVEL_0}. */
  public boolean canWriteInspection() {
    return isAtLeast(CbrRoles.LEVEL_0);
  }

  /**
   * True when the caller holds the general create/edit surface.
   *
   * <p>{@link CbrRoles#GENERAL} is deliberately excluded — the WebADE export shows all 24 of its
   * privileges are {@code /show*}, so a read-only user must fail this. Floor is
   * {@link CbrRoles#LEVEL_1}, the same floor {@link CbrAuthorities#CONTENT_EDIT} uses.
   */
  public boolean canEdit() {
    return isAtLeast(CbrRoles.LEVEL_1);
  }

  /** Alias for {@link #canEdit()}, kept for call sites that read better as "may write". */
  public boolean canWrite() {
    return canEdit();
  }

  /**
   * True when the caller may delete, archive or override inspection status.
   * Floor {@link CbrRoles#LEVEL_2}.
   */
  public boolean canDestroy() {
    return isAtLeast(CbrRoles.LEVEL_2);
  }

  /**
   * True if the user may review and seal inspection reports.
   *
   * <p><b>Sys-admin does NOT imply this.</b> An earlier version returned
   * {@code isSysAdmin() || PENG}, on the usual assumption that an administrator can do anything.
   * The WebADE export disproves it: {@code ADMINISTRATOR} holds exactly three privileges and the
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
