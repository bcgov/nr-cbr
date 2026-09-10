package ca.bc.gov.nrs.cbr.security;

import org.springframework.stereotype.Component;

/**
 * Exposes role constants as Spring beans for use in SpEL expressions and security configuration.
 *
 * <p>Ported from nr-frep's {@code RoleConstants}. BC Gov SSO (CSS) role names replace the retired
 * WebADE application roles; URL-level rules stay minimal and the real gating happens per-endpoint
 * via {@code @PreAuthorize} — see {@link CbrAuthorities}.
 *
 * <h3>⚠ PROVISIONAL — the CSS role set for CBR has not been agreed</h3>
 * The names below track the legacy role set, but the target role list is still a product decision.
 * Confirm against {@code CBR_SecurityMatrix.xls} (in the legacy tree at
 * {@code database/roles_users/}) and the WebADE {@code ACTION_LNK} export before provisioning. See
 * {@code cbr-auth-and-roles.local.md}.
 *
 * <h3>The legacy role set</h3>
 * CBR had <strong>five</strong> business roles plus a reporting account. They are defined in
 * {@code database/roles_users/roles.sql} and each is reached through one proxy connection account
 * in {@code users.sql}:
 *
 * <table>
 *   <caption>Legacy → target mapping</caption>
 *   <tr><th>Business role</th><th>Oracle role</th><th>Proxy account</th><th>CSS role</th></tr>
 *   <tr><td>Read Only</td><td>{@code CBR_GENERAL}</td><td>{@code CBR$WEB1}</td>
 *       <td>{@link #GENERAL_AUTHORITY}</td></tr>
 *   <tr><td>Inspector Level 1</td><td>{@code CBR_LEVEL_1}</td><td>{@code CBR$WEB2}</td>
 *       <td>{@link #LEVEL_1_AUTHORITY}</td></tr>
 *   <tr><td>Inspector Level 2</td><td>{@code CBR_LEVEL_2}</td><td>{@code CBR$WEB3}</td>
 *       <td>{@link #LEVEL_2_AUTHORITY}</td></tr>
 *   <tr><td>PENG</td><td>{@code CBR_PROFESSIONAL_ENGINEER}</td><td>{@code CBR$WEB4}</td>
 *       <td>{@link #PENG_AUTHORITY}</td></tr>
 *   <tr><td>Administrator</td><td>{@code CBR_ADMINISTRATOR}</td><td>{@code CBR$WEB5}</td>
 *       <td>{@link #SYS_ADMIN_AUTHORITY}</td></tr>
 *   <tr><td><em>(reporting only)</em></td><td>{@code CBR_CRYSTAL_REPORTS_USER}</td>
 *       <td>{@code CBR$RPT1}</td><td>— replaced by JasperReports</td></tr>
 * </table>
 *
 * <p>Mirrored by {@code frontend/src/context/auth/types.ts} — keep the two in step.
 *
 * <h3>⚠ There is no region scoping in legacy CBR</h3>
 * An earlier version of this class defined {@code CBR_ENGINEER} plus region-scoped
 * {@code CBR_REGIONAL_ENGINEER_<org unit>} and {@code CBR_CONTRACT_REGIONAL_ENGINEER_<org unit>}
 * roles. <strong>None of those has a legacy counterpart.</strong> They were derived from the
 * {@code roles=} attributes in {@code struts-config.xml}, which {@code SecurityRequestProcessor}
 * only tests for <em>emptiness</em> — the values are never compared to anything.
 * {@code INTERNAL_ENGINEER} occurs exactly once in the whole legacy codebase, in one such
 * attribute.
 *
 * <p>{@code CBR_REGIONAL_ENGINEER} and {@code CBR_CONTRACT_REGIONAL_ENGINEER} are real, but they
 * are <strong>PL/SQL packages, not roles</strong> — {@code roles.sql} issues no {@code create role}
 * for either name, and no proxy account holds one. They are reached by {@code GRANT EXECUTE ON
 * THE.CBR_REGIONAL_ENGINEER TO CBR_LEVEL_1 / CBR_LEVEL_2 / CBR_PROFESSIONAL_ENGINEER} — unscoped,
 * with no org unit anywhere in the grant. So the destructive procedures ({@code DELETE_SITE},
 * {@code DELETE_STRUCTURE}, {@code ARCHIVE_STRUCTURE}, {@code INSERT_SITE} …) are simply what
 * Level 1, Level 2 and P.Eng may do province-wide, and {@code UPDATE_SITE} likewise.
 *
 * <p>The region-scoped mechanism came from nr-frep's per-district CHR roles, not from CBR. The
 * deprecated members below are retained only so the existing dependents keep compiling; removing
 * them is the D3 cleanup, which also takes out {@link CbrStructureAuthorizer}, the region helpers
 * in {@link LoggedUserHelper}, and the region branches in the frontend auth layer.
 */
@Component("roles")
public class RoleConstants {

  private RoleConstants() {}

  /**
   * Full administrative access — inspection-reviewer administration and bulletin management.
   * Legacy Oracle {@code CBR_ADMINISTRATOR}, reached through {@code CBR$WEB5}.
   */
  public static final String SYS_ADMIN_AUTHORITY = "CBR_ADMIN";

  /**
   * The base role every CBR user holds. Legacy Oracle {@code CBR_GENERAL}, reached through
   * {@code CBR$WEB1}.
   *
   * <p><b>Open question:</b> the business-facing role table names this one <em>"Read Only"</em>,
   * but the legacy {@code CBR_GENERAL} package is not read-only — it carries the bulk of the write
   * surface, and {@code CBR_ADMINISTRATOR} is granted it. It is therefore left in
   * {@link #WRITE_AUTHORITIES} pending confirmation; if "Read Only" is meant literally, move it to
   * {@link #READ_AUTHORITIES} alone and the two arrays stop being identical.
   */
  public static final String GENERAL_AUTHORITY = "CBR_GENERAL";

  /**
   * Inspector Level 1. Legacy Oracle {@code CBR_LEVEL_1}, reached through {@code CBR$WEB2}; granted
   * {@code EXECUTE} on the {@code CBR_REGIONAL_ENGINEER} and {@code CBR_CONTRACT_REGIONAL_ENGINEER}
   * packages, so it carries the destructive operations province-wide.
   *
   * <p>Gates the {@code /level1Access} privilege, checked in 24 JSPs.
   */
  public static final String LEVEL_1_AUTHORITY = "CBR_LEVEL_1";

  /**
   * Inspector Level 2. Legacy Oracle {@code CBR_LEVEL_2}, reached through {@code CBR$WEB3}; same
   * package grants as {@link #LEVEL_1_AUTHORITY}.
   *
   * <p>Gates the {@code /level2Access} privilege, checked in 24 JSPs. Which of Level 1 and Level 2
   * is the broader role is not recoverable from the grants — both hold the same {@code EXECUTE}
   * set — and needs the WebADE {@code ACTION_LNK} export to settle.
   */
  public static final String LEVEL_2_AUTHORITY = "CBR_LEVEL_2";

  /**
   * Professional Engineer — may review and seal inspection reports.
   *
   * <p>Legacy Oracle {@code CBR_PROFESSIONAL_ENGINEER}, reached through {@code CBR$WEB4}. Holds the
   * same package grants as Level 1 and Level 2, so it is a superset rather than a parallel branch.
   * Gates the {@code /pEngAccess} privilege (19 JSPs) and, separately, {@code /approveInspection} —
   * the actual sign-off gate. Reviewers are additionally rows in
   * {@code STRUCTURE_INSPECTION_REVIEWER}, keyed by userid: holding the role is necessary, but the
   * reviewer record is what the workflow binds to.
   */
  public static final String PENG_AUTHORITY = "CBR_PENG";

  /**
   * Ministry engineer.
   *
   * @deprecated No legacy counterpart. Derived from the single {@code roles="INTERNAL_ENGINEER"}
   *     attribute in {@code struts-config.xml}, a value nothing reads. Retained only so existing
   *     dependents compile; remove with the D3 cleanup.
   */
  @Deprecated(forRemoval = true)
  public static final String ENGINEER_AUTHORITY = "CBR_ENGINEER";

  /**
   * Prefix for region-scoped regional-engineer roles.
   *
   * <p>Note the separator: nr-frep's equivalent had to change from {@code _} to {@code -} under
   * Keycloak, because FAM flattens a scoped grant into one role string joined with {@code -}. That
   * question is moot here — CBR grants no scoped roles, so nothing will ever match this prefix in
   * either spelling.
   *
   * @deprecated No legacy counterpart — legacy CBR has no region scoping at all.
   *     {@code CBR_REGIONAL_ENGINEER} is a PL/SQL package granted unscoped to
   *     {@link #LEVEL_1_AUTHORITY}, {@link #LEVEL_2_AUTHORITY} and {@link #PENG_AUTHORITY}. The
   *     prefix convention came from nr-frep. Retained only so existing dependents compile; remove
   *     with the D3 cleanup.
   */
  @Deprecated(forRemoval = true)
  public static final String REGIONAL_ENGINEER_PREFIX = "CBR_REGIONAL_ENGINEER_";

  /**
   * Prefix for region-scoped contract-engineer roles.
   *
   * @deprecated No legacy counterpart. {@code CBR_CONTRACT_REGIONAL_ENGINEER} is a PL/SQL package
   *     holding exactly one procedure ({@code UPDATE_SITE}), granted unscoped to
   *     {@link #LEVEL_1_AUTHORITY}, {@link #LEVEL_2_AUTHORITY} and {@link #PENG_AUTHORITY}. Note
   *     the struts attribute even reversed the word order ({@code REGIONAL_CONTRACT_ENGINEER}),
   *     which is a fair sign those strings were loose labels rather than identifiers. Retained only
   *     so existing dependents compile; remove with the D3 cleanup.
   */
  @Deprecated(forRemoval = true)
  public static final String CONTRACT_ENGINEER_PREFIX = "CBR_CONTRACT_REGIONAL_ENGINEER_";

  /**
   * Roles that may perform write operations.
   *
   * <p>{@link #ENGINEER_AUTHORITY} is deliberately absent: no user can hold a role that does not
   * exist in the realm, so listing it granted nothing. See the note on {@link #GENERAL_AUTHORITY}
   * for the one membership still open.
   */
  public static final String[] WRITE_AUTHORITIES = {
      SYS_ADMIN_AUTHORITY,
      GENERAL_AUTHORITY,
      LEVEL_1_AUTHORITY,
      LEVEL_2_AUTHORITY,
      PENG_AUTHORITY,
  };

  /**
   * Roles that may perform read operations.
   *
   * <p>Identical to {@link #WRITE_AUTHORITIES} today. They separate if "Read Only" turns out to be
   * literal for {@link #GENERAL_AUTHORITY}.
   */
  public static final String[] READ_AUTHORITIES = WRITE_AUTHORITIES;
}
