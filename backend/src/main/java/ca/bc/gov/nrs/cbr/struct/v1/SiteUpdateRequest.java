package ca.bc.gov.nrs.cbr.struct.v1;

import java.math.BigDecimal;

/**
 * What an edit of a site may change — {@code PUT /api/v1/sites/{siteId}}.
 *
 * <p>The fields a user can type into on Site Detail, and no others. The number comes from the path
 * and never changes. The Designated Maintainer, User Kilometres, BCTS BA Responsible and Capital
 * Road are absent because no role sets them on this screen — legacy disables them on every branch
 * of {@code site.jsp}, and LRMOPS writes them (see {@code UPDATE_CROSSING_SITE_FROM_LRM}). A field
 * the request cannot carry is one no client can overwrite.
 *
 * <p>Which of these a caller may actually change depends on their role; see
 * {@link ca.bc.gov.nrs.cbr.service.v1.SiteService#update}.
 */
public record SiteUpdateRequest(
    String crossingName,
    BigDecimal pointOfCommencementDistance,
    String crossingSiteStatusCode,
    String structureInspectionStatusCode,
    String crossingSiteTypeCode,
    String specialAccessRqmtCode,
    Long orgUnitNo,
    Long managementOrgUnitNo,
    String forestFileId,
    String roadSectionId,
    BigDecimal longitude,
    BigDecimal latitude,
    Integer utmZone,
    Long utmEasting,
    Long utmNorthing,
    String pointOfAccessDescription) {}
