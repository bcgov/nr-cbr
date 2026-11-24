package ca.bc.gov.nrs.cbr.service.v1;

/**
 * What the save's road check found for a site's Project File ID# and Br.
 *
 * <p>Computed by {@link SiteService} through {@link RoadSectionService#check} and handed to
 * {@link SiteValidator}, which turns it into a message: the lookup belongs to the caller, the rule
 * to the validator.
 */
public enum RoadCheck {
  /** No check applies: a recreation site, or either half of the pair is blank. */
  NOT_APPLICABLE,
  /** LRMOPS has the section. */
  FOUND,
  /** LRMOPS has no such section. */
  NOT_FOUND,
  /** The LRM Ops API could not answer, so the road is unchecked and the save is refused. */
  UNAVAILABLE
}
