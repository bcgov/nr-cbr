package ca.bc.gov.nrs.cbr.struct.v1;

import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.Site;

/**
 * A site a structure might move to, as the Information tab's Edit previews it under Site #.
 *
 * @param site    the site as the Information tab shows one, or null when there is no such site
 * @param problem why the structure cannot move there — the same sentence the save would refuse
 *                with — or null when it can
 */
public record SitePreviewResponse(Site site, String problem) {}
