package ca.bc.gov.nrs.cbr.struct.v1;

import lombok.Builder;

/** The Monitors Summary report's criteria (legacy CBR011), as query parameters. Every one optional. */
@Builder
public record MonitorsSummaryCriteria(
    String structureName,
    String siteStatusCode,
    String siteTypeCode,
    String clientNumber,
    String clientLocationCode,
    String monitoringStatusCode,
    String orgUnit,
    String businessAreaOrgUnit,
    String forestServiceRoad,
    String forestFileId,
    String roadSectionId,
    Boolean mostRecentInspections,
    MonitorsSummarySort sortBy) {

  /** The order chosen, or legacy's default: project, branch and kilometre. */
  public MonitorsSummarySort sortOrDefault() {
    return sortBy == null ? MonitorsSummarySort.PROJECT_BRANCH_KM : sortBy;
  }
}
