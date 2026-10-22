package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.code;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.orgUnit;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.range;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.text;

import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedSort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The Inspections Conducted reports' criteria line, under the title — legacy's
 * {@code getSelectedCriteria}, in its order and wording. Codes and org units print as their names,
 * where legacy printed Management Area's number and Site Type's code; a one-ended Inspection Date
 * prints {@code >=} or {@code <=}, as the range now includes that end.
 */
@Component
public class InspectionsConductedCriteriaLine {

  private final ConfigurationService codes;

  public InspectionsConductedCriteriaLine(ConfigurationService codes) {
    this.codes = codes;
  }

  String of(InspectionsConductedCriteria criteria, InspectionsConductedSort sort) {
    List<String> parts = new ArrayList<>();
    text(criteria.forestFileId()).ifPresent(value -> parts.add("Project File ID: " + value));
    text(criteria.roadSectionId()).ifPresent(value -> parts.add("Br.: " + value));
    orgUnit(codes.getForestDistricts(), criteria.orgUnit())
        .ifPresent(name -> parts.add("Forest District: " + name));
    orgUnit(codes.getBusinessAreas(), criteria.businessAreaOrgUnit())
        .ifPresent(name -> parts.add("Business Area: " + name));
    if (Boolean.TRUE.equals(criteria.closeProximity())) {
      parts.add("Close Proximity Inspection Required: Yes");
    }
    text(criteria.structureName()).ifPresent(value -> parts.add("Structure #: " + value));
    code(codes.getInspectionTypeCodes(), criteria.inspectionTypeCode())
        .ifPresent(value -> parts.add("Inspection Type: " + value));
    code(codes.getInspectionReportStatusCodes(), criteria.inspectionReportStatusCode())
        .ifPresent(value -> parts.add("Inspection Report Status: " + value));
    range(criteria.inspectionDateStart(), criteria.inspectionDateEnd())
        .ifPresent(value -> parts.add("Inspection Date: " + value));
    if (StringUtils.hasText(criteria.managementOrgUnit()) && StringUtils.hasText(criteria.orgUnit())) {
      orgUnit(codes.getManagementAreas(criteria.orgUnit()), criteria.managementOrgUnit())
          .ifPresent(name -> parts.add("Management Area: " + name));
    }
    if (Boolean.TRUE.equals(criteria.mostRecentInspections())) {
      parts.add("Most Recent Inspections Only: Yes");
    }
    text(criteria.clientNumber())
        .ifPresent(value -> parts.add("Primary User Client Number: " + value));
    text(criteria.clientLocationCode())
        .ifPresent(value -> parts.add("Client Location Code: " + value));
    if (Boolean.TRUE.equals(criteria.professionalInspection())) {
      parts.add("Professional Inspection Required: Yes");
    }
    text(criteria.inspectorName()).ifPresent(value -> parts.add("Inspector Name: " + value));
    code(codes.getSiteTypeCodes(), criteria.siteTypeCode())
        .ifPresent(value -> parts.add("Site Type: " + value));
    parts.add("Sort: " + sort.label());
    return String.join(" ", parts);
  }
}
