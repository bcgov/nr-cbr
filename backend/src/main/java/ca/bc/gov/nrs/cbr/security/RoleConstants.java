package ca.bc.gov.nrs.cbr.security;

import org.springframework.stereotype.Component;

/**
 * Exposes role constants as Spring beans for use in SpEL expressions and security configuration.
 *
 * <p>Ported from nr-frep's {@code RoleConstants}. Cognito (FAM) group names replace the retired
 * WebADE application roles; URL-level rules stay minimal and the real gating happens per-endpoint
 * via {@code @PreAuthorize} — see {@link CbrAuthorities}.
 *
 * <h3>⚠ PROVISIONAL — the FAM role set for CBR has not been agreed</h3>
 * The names below are a direct translation of the legacy authorization model and are a starting
 * point for that discussion, not a decision. Confirm against {@code CBR_SecurityMatrix.xls} and the
 * package grants before wiring FAM. See {@code docs/legacy-cbr-overview.md} §9 and §18.
 *
 * <h3>Legacy model this replaces</h3>
 * Authorization used to be enforced three times over: a WebADE application role declared on the
 * Struts action, an Oracle database role, and {@code EXECUTE} grants on the PL/SQL packages. With
 * WebADE retired the first two collapse into Cognito group membership; the package grants remain in
 * the database but are no longer the enforcement point, because the app connects as a single user.
 *
 * <table>
 *   <caption>Legacy → target mapping</caption>
 *   <tr><th>Struts {@code roles=}</th><th>Oracle role / package</th><th>Cognito group</th></tr>
 *   <tr><td>{@code GENERAL}</td><td>{@code CBR_GENERAL}</td><td>{@link #GENERAL_AUTHORITY}</td></tr>
 *   <tr><td>{@code INTERNAL_ENGINEER}</td><td>{@code CBR_GENERAL}</td><td>{@link #ENGINEER_AUTHORITY}</td></tr>
 *   <tr><td>{@code REGIONAL_ENGINEER}</td><td>{@code CBR_REGIONAL_ENGINEER}</td><td>{@link #REGIONAL_ENGINEER_PREFIX} + org unit</td></tr>
 *   <tr><td>{@code REGIONAL_CONTRACT_ENGINEER}</td><td>{@code CBR_CONTRACT_REGIONAL_ENGINEER}</td><td>{@link #CONTRACT_ENGINEER_PREFIX} + org unit</td></tr>
 *   <tr><td>{@code ADMINISTRATOR}</td><td>{@code CBR_ADMIN}</td><td>{@link #SYS_ADMIN_AUTHORITY}</td></tr>
 * </table>
 */
@Component("roles")
public class RoleConstants {

  private RoleConstants() {}

  /**
   * Full administrative access — inspection-reviewer administration and bulletin management.
   * Legacy: Struts {@code ADMINISTRATOR}, Oracle {@code CBR_ADMINISTRATOR} (grants {@code CBR_ADMIN}).
   */
  public static final String SYS_ADMIN_AUTHORITY = "CBR_ADMIN";

  /**
   * Read plus the general write surface (structures, inspections, repairs, monitors, attachments).
   * Legacy: Struts {@code GENERAL}, Oracle {@code CBR_GENERAL}.
   */
  public static final String GENERAL_AUTHORITY = "CBR_GENERAL";

  /**
   * Ministry engineer — the province-wide (non-region-scoped) engineering role.
   * Legacy: Struts {@code INTERNAL_ENGINEER}.
   */
  public static final String ENGINEER_AUTHORITY = "CBR_ENGINEER";

  /**
   * Professional Engineer — may review and seal inspection reports.
   *
   * <p>Legacy Oracle {@code CBR_PROFESSIONAL_ENGINEER} was granted {@code CBR_GENERAL},
   * {@code CBR_REGIONAL_ENGINEER} <em>and</em> {@code CBR_CONTRACT_REGIONAL_ENGINEER}, so this is a
   * superset role rather than a parallel branch. Reviewers are additionally rows in
   * {@code STRUCTURE_INSPECTION_REVIEWER}, keyed by userid — holding the group is necessary but the
   * reviewer record is what the workflow binds to.
   */
  public static final String PENG_AUTHORITY = "CBR_PENG";

  /**
   * Prefix for region-scoped regional-engineer roles: {@code CBR_REGIONAL_ENGINEER_<org unit code>}.
   * Follows nr-frep's {@code FREP_CHR_EDITOR_DISTRICT_<code>} convention — the scope travels in the
   * role name and is parsed out of the caller's groups by {@link LoggedUserHelper}.
   *
   * <p>Grants the destructive operations the legacy {@code CBR_REGIONAL_ENGINEER} package held:
   * {@code DELETE_SITE}, {@code DELETE_STRUCTURE}, {@code DELETE_INSPECTION}, {@code DELETE_REPAIR},
   * {@code DELETE_MONITOR}, {@code DELETE_FILE_ATTACHMENT}, {@code ARCHIVE_STRUCTURE},
   * {@code INSERT_SITE} — within the holder's region only.
   *
   * <p><b>Open question:</b> which org-unit column defines "region" for scoping. {@code CROSSING_SITE}
   * carries {@code MANAGEMENT_ORG_UNIT_NO}, {@code BUSINESS_AREA_ORG_UNIT_NO} and {@code ORG_UNIT_NO},
   * and the {@code CBR_ORG_UNIT} view rolls districts up into three areas. Resolve before FAM roles
   * are provisioned — it determines the cardinality of the role set.
   */
  public static final String REGIONAL_ENGINEER_PREFIX = "CBR_REGIONAL_ENGINEER_";

  /**
   * Prefix for region-scoped contract-engineer roles:
   * {@code CBR_CONTRACT_REGIONAL_ENGINEER_<org unit code>}. Narrower than
   * {@link #REGIONAL_ENGINEER_PREFIX}: the legacy {@code CBR_CONTRACT_REGIONAL_ENGINEER} package
   * contained exactly one procedure, {@code UPDATE_SITE}.
   */
  public static final String CONTRACT_ENGINEER_PREFIX = "CBR_CONTRACT_REGIONAL_ENGINEER_";

  /** Global roles that may perform write operations. Region-scoped roles are checked separately. */
  public static final String[] WRITE_AUTHORITIES = {
      SYS_ADMIN_AUTHORITY,
      GENERAL_AUTHORITY,
      ENGINEER_AUTHORITY,
      PENG_AUTHORITY,
  };

  /**
   * Global roles that may perform read operations.
   *
   * <p>Identical to {@link #WRITE_AUTHORITIES}: CBR has no read-only global role today. A holder of
   * only a region-scoped role has no global role at all and is authorized through {@code @auth}
   * instead — see {@link LoggedUserHelper}.
   */
  public static final String[] READ_AUTHORITIES = WRITE_AUTHORITIES;
}
