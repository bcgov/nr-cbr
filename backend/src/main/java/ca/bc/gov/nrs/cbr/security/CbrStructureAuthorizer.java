package ca.bc.gov.nrs.cbr.security;

import org.springframework.stereotype.Component;

/**
 * Resource-aware authorization for {@code @PreAuthorize} expressions, registered as the
 * {@code @cbrAuth} bean. Ported from nr-frep's {@code ChrChecklistAuthorizer}.
 *
 * <p>Resolves a structure's or site's owning org unit and checks it against the caller's
 * region-scoped roles, so per-region gating lives on the endpoint annotation rather than being
 * scattered through the services. Sys-admins pass for any region (see
 * {@link LoggedUserHelper#canRegion(String)}).
 *
 * <p>The coarse {@link CbrAuthorities#REGIONAL_ENGINEER} gate is <b>not</b> sufficient for any
 * endpoint that resolves a specific record — there is no service-layer region check to fall back on,
 * exactly as in nr-frep. Endpoints taking a structure or site id must use this bean.
 *
 * <h3>⚠ NOT YET WIRED</h3>
 * The lookups below need the CBR repositories, which land with the domain port. The open question
 * they depend on is which org-unit column defines a region — {@code CROSSING_SITE} carries
 * {@code MANAGEMENT_ORG_UNIT_NO}, {@code BUSINESS_AREA_ORG_UNIT_NO} and {@code ORG_UNIT_NO}, and
 * {@code CBR_ORG_UNIT} rolls districts into areas. See {@link ObsoleteRoles#REGIONAL_ENGINEER_PREFIX}.
 * Until that is settled these methods deliberately fail closed rather than guess.
 */
@Component("cbrAuth")
public class CbrStructureAuthorizer {

  private final LoggedUserHelper loggedUserHelper;

  public CbrStructureAuthorizer(LoggedUserHelper loggedUserHelper) {
    this.loggedUserHelper = loggedUserHelper;
  }

  /**
   * True if the caller may perform regional-engineer operations on the structure identified by
   * {@code crossingStructureId} — the destructive set: delete, archive.
   */
  public boolean canModifyStructure(long crossingStructureId) {
    return loggedUserHelper.canRegion(resolveStructureOrgUnitCode(crossingStructureId));
  }

  /** True if the caller may update the site identified by {@code crossingSiteId}. */
  public boolean canUpdateSite(String crossingSiteId) {
    return loggedUserHelper.canUpdateSite(resolveSiteOrgUnitCode(crossingSiteId));
  }

  /**
   * Resolves the org-unit code that scopes a structure. Structures reach an org unit through their
   * site ({@code CROSSING_STRUCTURE.CROSSING_SITE_ID → CROSSING_SITE}).
   *
   * @return {@code null} until the repository is wired, which makes {@link #canModifyStructure(long)}
   *     fail closed for every non-sys-admin caller.
   */
  private String resolveStructureOrgUnitCode(long crossingStructureId) {
    // TODO(domain-port): look up via CrossingStructureRepository → CrossingSiteRepository.
    return null;
  }

  /**
   * Resolves the org-unit code that scopes a site.
   *
   * @return {@code null} until the repository is wired — fails closed, as above.
   */
  private String resolveSiteOrgUnitCode(String crossingSiteId) {
    // TODO(domain-port): look up via CrossingSiteRepository.
    return null;
  }
}
