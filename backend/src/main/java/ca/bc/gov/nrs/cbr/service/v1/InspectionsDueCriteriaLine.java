package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.code;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.orgUnit;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.range;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.text;

import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueSort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The Inspections Due reports' criteria line, under the title — legacy's
 * {@code getSelectedCriteria}, in its order and wording. Codes and org units print as their names,
 * where legacy printed Management Area's number; a one-ended Next Planned Inspection Date prints
 * {@code >=} or {@code <=}, as the range now includes that end. Structure # leads, for the Blank
 * Report, whose legacy form printed no criteria at all; a report with no Sort By prints none.
 */
@Component
public class InspectionsDueCriteriaLine {

  private final ConfigurationService codes;

  public InspectionsDueCriteriaLine(ConfigurationService codes) {
    this.codes = codes;
  }

  /** The line, {@code sort} null for a report whose order is fixed. */
  String of(InspectionsDueCriteria criteria, InspectionsDueSort sort) {
    List<String> parts = new ArrayList<>();
    text(criteria.structureName()).ifPresent(value -> parts.add("Structure #: " + value));
    text(criteria.forestFileId()).ifPresent(value -> parts.add("Project File ID: " + value));
    text(criteria.roadSectionId()).ifPresent(value -> parts.add("Br.: " + value));
    orgUnit(codes.getForestDistricts(), criteria.orgUnit())
        .ifPresent(name -> parts.add("Forest District: " + name));
    orgUnit(codes.getBusinessAreas(), criteria.businessAreaOrgUnit())
        .ifPresent(name -> parts.add("Business Area: " + name));
    if (Boolean.TRUE.equals(criteria.noNextPlannedInspectionDate())) {
      parts.add("No Next Planned Inspection Date");
    } else {
      range(criteria.nextPlannedInspectionDateStart(), criteria.nextPlannedInspectionDateEnd())
          .ifPresent(value -> parts.add("Next Planned Inspection Date: " + value));
    }
    code(codes.getSpecialAccessCodes(), criteria.specialAccessCode())
        .ifPresent(value -> parts.add("Special Access Requirements: " + value));
    if (InspectionsDueCriteria.NO_SPECIAL_EQUIPMENT.equals(criteria.specialEquipmentCode())) {
      parts.add("Special Equipment Requirements: None");
    } else {
      code(codes.getSpecialEquipmentCodes(), criteria.specialEquipmentCode())
          .ifPresent(value -> parts.add("Special Equipment Requirements: " + value));
    }
    text(criteria.closeProximity()).ifPresent(value -> parts.add(
        "Close Proximity Inspection Required: " + ("Y".equals(value) ? "Yes" : "No")));
    code(codes.getStructureTypeClassCodes(), criteria.structureTypeClassCode())
        .ifPresent(value -> parts.add("Type/Class: " + value));
    code(codes.getStructureInspectionStatusCodes(), criteria.structureInspectionStatusCode())
        .ifPresent(value -> parts.add("Inspection Status: " + value));
    if (StringUtils.hasText(criteria.managementOrgUnit()) && StringUtils.hasText(criteria.orgUnit())) {
      orgUnit(codes.getManagementAreas(criteria.orgUnit()), criteria.managementOrgUnit())
          .ifPresent(name -> parts.add("Management Area: " + name));
    }
    if (sort != null) {
      parts.add("Sort: " + sort.label());
    }
    code(codes.getSiteStatusCodes(), criteria.siteStatusCode())
        .ifPresent(value -> parts.add("Site Status: " + value));
    code(codes.getSiteTypeCodes(), criteria.siteTypeCode())
        .ifPresent(value -> parts.add("Site Type: " + value));
    text(criteria.clientNumber())
        .ifPresent(value -> parts.add("Primary User Client Number: " + value));
    text(criteria.clientLocationCode())
        .ifPresent(value -> parts.add("Client Location Code: " + value));
    return String.join(" ", parts);
  }
}
