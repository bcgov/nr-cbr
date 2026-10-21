package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.code;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.orgUnit;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.text;

import ca.bc.gov.nrs.cbr.struct.v1.MonitorsSummaryCriteria;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The Monitors Summary's criteria line, under its title — legacy's {@code getSelectedCriteria}, in
 * its order and wording, codes shown as their descriptions. Two of its slips are corrected: it
 * labelled Site Type "Site Status:", and ran "Br." into whatever followed it.
 */
@Component
public class MonitorsSummaryCriteriaLine {

  private final ConfigurationService codes;

  public MonitorsSummaryCriteriaLine(ConfigurationService codes) {
    this.codes = codes;
  }

  String of(MonitorsSummaryCriteria criteria) {
    List<String> parts = new ArrayList<>();
    text(criteria.structureName()).ifPresent(value -> parts.add("Structure #: " + value));
    orgUnit(codes.getForestDistricts(), criteria.orgUnit())
        .ifPresent(name -> parts.add("Forest District: " + name));
    orgUnit(codes.getBusinessAreas(), criteria.businessAreaOrgUnit())
        .ifPresent(name -> parts.add("Business Area: " + name));
    text(criteria.forestServiceRoad())
        .ifPresent(value -> parts.add("Forest Service Road: " + value));
    text(criteria.forestFileId()).ifPresent(value -> parts.add("Project File ID: " + value));
    text(criteria.roadSectionId()).ifPresent(value -> parts.add("Br.: " + value));
    code(codes.getMonitoringStatusCodes(), criteria.monitoringStatusCode())
        .ifPresent(value -> parts.add("Monitoring Status: " + value));
    if (Boolean.TRUE.equals(criteria.mostRecentInspections())) {
      parts.add("Most Recent Inspections Only: Yes");
    }
    text(criteria.clientNumber())
        .ifPresent(value -> parts.add("Primary User Client Number: " + value));
    text(criteria.clientLocationCode())
        .ifPresent(value -> parts.add("Client Location Code: " + value));
    parts.add("Sort By: " + criteria.sortOrDefault().label());
    code(codes.getSiteStatusCodes(), criteria.siteStatusCode())
        .ifPresent(value -> parts.add("Site Status: " + value));
    code(codes.getSiteTypeCodes(), criteria.siteTypeCode())
        .ifPresent(value -> parts.add("Site Type: " + value));
    return String.join(" ", parts);
  }
}
