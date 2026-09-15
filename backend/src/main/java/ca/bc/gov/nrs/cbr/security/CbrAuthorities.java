package ca.bc.gov.nrs.cbr.security;

/**
 * Centralised method-security expressions for {@code @PreAuthorize}.
 *
 * <p>The role&rarr;capability matrix is defined once here, so every endpoint names a capability
 * rather than repeating a role list. Same split as nr-fspts' {@code FspAuthorities}.
 *
 * <p>Expressions use {@code hasAnyAuthority(...)} with bare role names, because
 * {@link Oauth2SecurityCustomizer} exposes authorities with <b>no {@code ROLE_} prefix</b>. (nr-fspts
 * prefixes and uses {@code hasRole}; the two conventions must not be mixed within one application —
 * a prefixed expression against unprefixed authorities simply never matches, and it fails as a
 * blanket 403 rather than as anything that names the cause.)
 *
 * <h2>Capability matrix</h2>
 * Derived from the WebADE {@code ACTION_LNK} export — see {@code cbr-auth-and-roles.local.md} §3.2.
 * Each capability has a <b>floor</b> on the {@link CbrRoles#LADDER}; every rung at or above it
 * qualifies, because the legacy profiles nest.
 *
 * <table>
 *   <caption>Who can do what</caption>
 *   <tr><th>Capability</th><th>Floor</th><th>GENERAL</th><th>LEVEL_0</th><th>LEVEL_1</th><th>LEVEL_2</th><th>PENG</th><th>ADMIN</th></tr>
 *   <tr><td>{@link #READ}</td><td>GENERAL</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td><td><b>✓</b></td></tr>
 *   <tr><td>{@link #INSPECTION_WRITE}</td><td>LEVEL_0</td><td>—</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td><td>—</td></tr>
 *   <tr><td>{@link #CONTENT_EDIT}</td><td>LEVEL_1</td><td>—</td><td>—</td><td>✓</td><td>✓</td><td>✓</td><td>—</td></tr>
 *   <tr><td>{@link #DESTRUCTIVE}</td><td>LEVEL_2</td><td>—</td><td>—</td><td>—</td><td>✓</td><td>✓</td><td>—</td></tr>
 *   <tr><td>{@link #APPROVE}</td><td>PENG</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✓</td><td>—</td></tr>
 *   <tr><td>{@link #ADMIN}</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✓</td></tr>
 * </table>
 *
 * <p><b>{@link #READ} is the only capability {@link CbrRoles#ADMIN} shares.</b> Administrators can
 * search, view and report; they write nothing. Every other row's ADMIN column is empty, and that is
 * what preserves the separation of duties — see {@link CbrRoles#READERS} for why read was granted
 * and legacy was not followed here.
 *
 * <p>These are coarse, role-level gates: they decide whether a kind of user may ever perform an
 * operation. They do <b>not</b> decide whether this caller may do it to <em>this</em> record in
 * <em>this</em> state — that fence is separate, and for CBR it is the
 * {@code STRUCTURE_INSPECTION_REVIEWER} row that binds a sign-off to a named P.Eng (§6). nr-fspts
 * puts the equivalent in {@code FspAccessGuard}.
 *
 * <p>Every expression below is pinned against {@link CbrRoles#LADDER} by {@code CbrAuthoritiesTest},
 * so a reordered ladder fails the build instead of silently re-granting.
 */
public final class CbrAuthorities {

  private CbrAuthorities() {}

  /**
   * View and search sites, structures, inspections and documents; run any of the twelve reports.
   *
   * <p>Every ladder role (floor {@link CbrRoles#GENERAL} — all 24 of that role's legacy privileges
   * are {@code /show*}), <b>plus {@link CbrRoles#ADMIN}</b>, which legacy excluded. See
   * {@link CbrRoles#READERS}.
   */
  public static final String READ =
      "hasAnyAuthority('CBR_GENERAL','CBR_LEVEL_0','CBR_LEVEL_1','CBR_LEVEL_2','CBR_PENG',"
          + "'CBR_ADMIN')";

  /**
   * Record or amend an inspection ({@code /saveInspection}), and the offline capture path.
   *
   * <p>Floor: {@link CbrRoles#LEVEL_0}. Narrower than {@link #CONTENT_EDIT} on purpose — Level 0
   * exists to let a field user file an inspection without touching the site or structure it hangs
   * off.
   */
  public static final String INSPECTION_WRITE =
      "hasAnyAuthority('CBR_LEVEL_0','CBR_LEVEL_1','CBR_LEVEL_2','CBR_PENG')";

  /**
   * The general create/edit surface: {@code /saveSite}, {@code /saveStructure},
   * {@code /updateRepairResponsibility}, and the document upload path.
   *
   * <p>Floor: {@link CbrRoles#LEVEL_1}. Creates and edits but deletes nothing — that is the Level 1
   * / Level 2 line.
   */
  public static final String CONTENT_EDIT =
      "hasAnyAuthority('CBR_LEVEL_1','CBR_LEVEL_2','CBR_PENG')";

  /**
   * Destructive operations: the nine deletes (site, structure, inspection, and the
   * repair/monitor/document children of each), {@code /archiveStructure}, {@code /addSite},
   * {@code /deleteUpload}, {@code /uploadInspections}, and {@code /modifyStructureInspectionStatus}
   * — the manual status override.
   *
   * <p>Floor: {@link CbrRoles#LEVEL_2}. Note {@code /addSite} sits here rather than with
   * {@link #CONTENT_EDIT}: the legacy export puts it on LEVEL_2, alongside the deletes.
   */
  public static final String DESTRUCTIVE =
      "hasAnyAuthority('CBR_LEVEL_2','CBR_PENG')";

  /**
   * Sign off an inspection — {@code /approveInspection} and {@code /pEngAccess}: moving one into
   * {@code RVD}/{@code REJ}/{@code ACC}, and editing one already there.
   *
   * <p>Floor: {@link CbrRoles#PENG}, which in the export holds {@code /approveInspection}
   * <b>alone</b>. {@link CbrRoles#ADMIN} is excluded: sealing an inspection is a professional
   * engineering act tied to a named P.Eng, not an administrative one.
   *
   * <p>Necessary but not sufficient — the service layer must also find an active
   * {@code STRUCTURE_INSPECTION_REVIEWER} row for the caller's userid.
   */
  public static final String APPROVE = "hasAuthority('CBR_PENG')";

  /**
   * Administration: the landing-page bulletin and inspection-reviewer maintenance.
   *
   * <p>Off the ladder. {@link CbrRoles#ADMIN} carries this and {@link #READ}, and nothing else.
   */
  public static final String ADMIN = "hasAuthority('CBR_ADMIN')";
}
