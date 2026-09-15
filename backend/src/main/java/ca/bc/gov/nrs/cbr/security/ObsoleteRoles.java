package ca.bc.gov.nrs.cbr.security;

/**
 * Role names that are <b>not</b> part of CBR's authorization model, kept only so their remaining
 * call sites compile until the D3 cleanup removes them.
 *
 * <p>Nothing here is issued by FAM, and nothing here will ever appear on a token. The live
 * vocabulary is {@link CbrRoles}; this class exists so that a reader of {@code CbrRoles} sees six
 * real roles and nothing else.
 *
 * <h2>Why they existed</h2>
 * All three are real WebADE {@code APPLICATION_ROLE} rows whose definitions read literally
 * {@code OBSOLETE_..._OBSOLETE}, and all three hold <b>zero {@code ACTION_LNK} rows</b> — they grant
 * nothing to anyone. They were CBR's role model before {@code LEVEL_1}/{@code LEVEL_2} replaced it,
 * which is why the {@code roles=} attributes in the legacy {@code struts-config.xml} are named after
 * them. The PL/SQL packages kept the retired names: {@code CBR_REGIONAL_ENGINEER}'s nine destructive
 * procedures are now {@link CbrRoles#LEVEL_2}'s privileges, and
 * {@code CBR_CONTRACT_REGIONAL_ENGINEER}'s lone {@code UPDATE_SITE} is {@link CbrRoles#LEVEL_1}'s
 * {@code /saveSite}.
 *
 * <p><b>There is no region scoping in CBR.</b> No grant in the WebADE export carries an org unit.
 * The two prefixes below were imported from nr-frep's per-district CHR roles on a false premise and
 * match nothing — see {@code cbr-auth-and-roles.local.md} §3.1.
 *
 * <h2>Removing them</h2>
 * The D3 cleanup deletes this class along with {@link CbrStructureAuthorizer}, the region helpers in
 * {@link LoggedUserHelper}, and the region branches in the frontend auth layer.
 *
 * @deprecated Retained for compilation only. Do not reference from new code.
 */
@Deprecated(forRemoval = true)
public final class ObsoleteRoles {

  private ObsoleteRoles() {}

  /**
   * Ministry engineer.
   *
   * @deprecated WebADE {@code INTERNAL_ENGINEER}, defined as
   *     {@code OBSOLETE_Internal Engineer role_OBSOLETE} with no privilege grants.
   */
  @Deprecated(forRemoval = true)
  public static final String ENGINEER_AUTHORITY = "CBR_ENGINEER";

  /**
   * Prefix for region-scoped regional-engineer roles.
   *
   * @deprecated WebADE {@code REGIONAL_ENGINEER}, defined as
   *     {@code OBSOLETE_Regional Engineer role_OBSOLETE} with no privilege grants. CBR has no region
   *     scoping, so nothing matches this prefix.
   */
  @Deprecated(forRemoval = true)
  public static final String REGIONAL_ENGINEER_PREFIX = "CBR_REGIONAL_ENGINEER_";

  /**
   * Prefix for region-scoped contract-engineer roles.
   *
   * @deprecated WebADE {@code REGIONAL_CONTRACT_ENGINEER}, defined as
   *     {@code OBSOLETE_Regional Contract Engineer role_OBSOLETE} with no privilege grants.
   */
  @Deprecated(forRemoval = true)
  public static final String CONTRACT_ENGINEER_PREFIX = "CBR_CONTRACT_REGIONAL_ENGINEER_";
}
