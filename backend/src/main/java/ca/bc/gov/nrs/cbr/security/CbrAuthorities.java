package ca.bc.gov.nrs.cbr.security;

/**
 * SpEL authorization expressions for {@code @PreAuthorize} on the API endpoints. Ported from
 * nr-frep's {@code FrepAuthorities} (itself modelled on nr-fspts): one constant per access level,
 * referenced from each endpoint.
 *
 * <p>The Cognito-groups converter exposes authorities <em>without</em> the {@code ROLE_} prefix (see
 * {@link Oauth2SecurityCustomizer}), so these use {@code hasAnyAuthority(...)} rather than
 * {@code hasAnyRole(...)}.
 *
 * <h3>⚠ PROVISIONAL</h3>
 * These follow from {@link RoleConstants}, which is itself a starting point rather than an agreed
 * role set. Revisit together.
 *
 * @see RoleConstants for the authority strings and the legacy mapping.
 */
public final class CbrAuthorities {

  private CbrAuthorities() {}

  /** Any authenticated CBR user with a recognised role. Read surfaces: search, view, reports. */
  public static final String READ =
      "hasAnyAuthority('CBR_ADMIN','CBR_GENERAL','CBR_ENGINEER','CBR_PENG') or @auth.hasAnyRegion()";

  /**
   * The general write surface — create/edit structures, inspections, repairs, monitors, load
   * ratings, attachments. Excludes the destructive and admin operations below.
   */
  public static final String CONTENT_EDIT =
      "hasAnyAuthority('CBR_ADMIN','CBR_GENERAL','CBR_ENGINEER','CBR_PENG')";

  /**
   * Administration: inspection-reviewer maintenance and the landing-page bulletin. Legacy
   * {@code CBR_ADMIN} package.
   */
  public static final String ADMIN = "hasAuthority('CBR_ADMIN')";

  /**
   * Review and seal an inspection report (the ConsignO signing workflow). Requires the P.Eng group
   * <em>and</em> an active row in {@code STRUCTURE_INSPECTION_REVIEWER} — the latter is a service-layer
   * check, not an authority.
   */
  public static final String PENG_REVIEW = "hasAnyAuthority('CBR_ADMIN','CBR_PENG')";

  /**
   * Coarse "may this caller perform region-scoped operations at all" gate: sys-admin, or the holder
   * of at least one {@code CBR_REGIONAL_ENGINEER_*} role.
   *
   * <p><b>Not sufficient on its own for any endpoint that resolves a specific structure or site.</b>
   * Those must use {@code @PreAuthorize("@cbrAuth.canModifyStructure(#id)")}
   * ({@link CbrStructureAuthorizer}), which resolves the record's org unit and checks it against the
   * caller's regions. This constant is for id-less surfaces only — the same rule nr-frep applies to
   * its per-district CHR roles.
   */
  public static final String REGIONAL_ENGINEER = "@auth.canAnyRegion()";

  /**
   * Destructive operations — delete site/structure/inspection/repair/monitor/attachment, and
   * archive a structure. Legacy {@code CBR_REGIONAL_ENGINEER} package, which is the only place these
   * procedures existed.
   */
  public static final String DESTRUCTIVE = "@auth.canAnyRegion()";
}
