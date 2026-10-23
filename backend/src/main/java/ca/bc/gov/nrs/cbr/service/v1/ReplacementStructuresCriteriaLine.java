package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.code;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.orgUnit;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.range;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.text;

import ca.bc.gov.nrs.cbr.struct.v1.ReplacementStructuresCriteria;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The Replacement Structures Summary's criteria line, under the title — legacy's
 * {@code getSelectedCriteria}, in its order and wording. Forest District and Management Area are
 * left out, as legacy's were: each page heads itself with its own. Codes and org units print as
 * their names; a one-ended year range prints {@code >=} or {@code <=}, as the range now includes
 * that end.
 */
@Component
public class ReplacementStructuresCriteriaLine {

  private final ConfigurationService codes;

  public ReplacementStructuresCriteriaLine(ConfigurationService codes) {
    this.codes = codes;
  }

  String of(ReplacementStructuresCriteria criteria) {
    List<String> parts = new ArrayList<>();
    orgUnit(codes.getBusinessAreas(), criteria.businessAreaOrgUnit())
        .ifPresent(name -> parts.add("Business Area: " + name));
    range(criteria.loadRestrictionYearStart(), criteria.loadRestrictionYearEnd())
        .ifPresent(value -> parts.add("Estimated Load Restriction Date: " + value));
    range(criteria.replacementYearStart(), criteria.replacementYearEnd())
        .ifPresent(value -> parts.add("Estimated Replacement Date: " + value));
    code(codes.getSiteStatusCodes(), criteria.siteStatusCode())
        .ifPresent(value -> parts.add("Site Status: " + value));
    if (Boolean.TRUE.equals(criteria.capitalRoad())) {
      parts.add("Capital Road: Yes");
    }
    code(codes.getSiteTypeCodes(), criteria.siteTypeCode())
        .ifPresent(value -> parts.add("Site Type: " + value));
    text(criteria.clientNumber())
        .ifPresent(value -> parts.add("Primary User Client Number: " + value));
    text(criteria.clientLocationCode())
        .ifPresent(value -> parts.add("Client Location Code: " + value));
    return String.join(" ", parts);
  }
}
