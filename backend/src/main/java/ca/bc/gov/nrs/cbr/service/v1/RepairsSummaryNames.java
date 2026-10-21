package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.code;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.orgUnit;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.text;

import ca.bc.gov.nrs.cbr.model.v1.RepairPriorityCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RepairStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeOrderEntity;
import ca.bc.gov.nrs.cbr.repository.v1.RepairPriorityCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.RepairStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairTypeOrderRepository;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairsSummaryCriteria;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The names the Repairs Summary prints for what a repair stores as a code — its status, priority,
 * type and the type's unit — and the criteria line under its title.
 *
 * <p>Every code is read, expired ones included, as legacy's {@code REPAIR_SUMMARY} left-joins the
 * whole table: a repair recorded years ago still carries a code since retired.
 */
@Component
public class RepairsSummaryNames {

  /** Legacy's own groups of repair statuses and priorities, by the value the form sends. */
  private static final Map<String, String> GROUPS = Map.of(
      RepairsSummaryCriteria.OUTSTANDING, "Outstanding",
      RepairsSummaryCriteria.SUGGESTED_OR_REQUIRED, "Suggested or Required",
      "U&H&M", "Urgent, High and Medium",
      "H&M", "High and Medium",
      "M&L", "Medium and Low");

  private final RepairStatusCodeRepository statuses;
  private final RepairPriorityCodeRepository priorities;
  private final StructureRepairTypeCodeRepository types;
  private final StructureRepairTypeOrderRepository typeOrders;
  private final ConfigurationService codes;

  public RepairsSummaryNames(
      RepairStatusCodeRepository statuses,
      RepairPriorityCodeRepository priorities,
      StructureRepairTypeCodeRepository types,
      StructureRepairTypeOrderRepository typeOrders,
      ConfigurationService codes) {
    this.statuses = statuses;
    this.priorities = priorities;
    this.types = types;
    this.typeOrders = typeOrders;
    this.codes = codes;
  }

  /** The descriptions and units the rows print, each by its code. */
  Names names() {
    return new Names(
        byCode(statuses.findAll(), RepairStatusCodeEntity::getRepairStatusCode,
            RepairStatusCodeEntity::getDescription),
        byCode(priorities.findAll(), RepairPriorityCodeEntity::getRepairPriorityCode,
            RepairPriorityCodeEntity::getDescription),
        byCode(types.findAll(), StructureRepairTypeCodeEntity::getStructureRepairTypeCode,
            StructureRepairTypeCodeEntity::getDescription),
        byCode(typeOrders.findAll(), StructureRepairTypeOrderEntity::getStructureRepairTypeCode,
            StructureRepairTypeOrderEntity::getStructureRepairUnit));
  }

  /**
   * The criteria line under the title — legacy's {@code getSelectedCriteria}, in its order and
   * wording, with codes shown as their descriptions. Unlike legacy's, it names legacy's groups —
   * whose codes are in no table, so legacy printed nothing for its own default, Outstanding — says
   * so when Downrated Structure is on, and prints Most Recent Inspections Only when it is on, not
   * when it is off.
   */
  String selectedCriteria(RepairsSummaryCriteria criteria) {
    List<String> parts = new ArrayList<>();
    text(criteria.siteId()).ifPresent(value -> parts.add("Site #: " + value));
    text(criteria.structureName()).ifPresent(value -> parts.add("Structure #: " + value));
    orgUnit(codes.getForestDistricts(), criteria.orgUnit())
        .ifPresent(name -> parts.add("Forest District: " + name));
    orgUnit(codes.getBusinessAreas(), criteria.businessAreaOrgUnit())
        .ifPresent(name -> parts.add("Business Area: " + name));
    if (StringUtils.hasText(criteria.managementOrgUnit()) && StringUtils.hasText(criteria.orgUnit())) {
      orgUnit(codes.getManagementAreas(criteria.orgUnit()), criteria.managementOrgUnit())
          .ifPresent(name -> parts.add("Management Area: " + name));
    }
    text(criteria.forestServiceRoad())
        .ifPresent(value -> parts.add("Forest Service Road: " + value));
    text(criteria.forestFileId()).ifPresent(value -> parts.add("Project File ID: " + value));
    text(criteria.roadSectionId()).ifPresent(value -> parts.add("Br.: " + value));
    if (Boolean.TRUE.equals(criteria.downrated())) {
      parts.add("Downrated Structure: Yes");
    }
    grouped(codes.getRepairStatusCodes(), criteria.repairStatusCode())
        .ifPresent(value -> parts.add("Repair Status: " + value));
    grouped(codes.getRepairPriorityCodes(), criteria.repairPriorityCode())
        .ifPresent(value -> parts.add("Repair Priority: " + value));
    grouped(codes.getRepairClassCodes(), criteria.repairClassCode())
        .ifPresent(value -> parts.add("Structural Safety: " + value));
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

  /**
   * A code's description, legacy's name for one of its groups, or — for any other codes joined with
   * {@code &}, as Structural Safety's pairs are — their descriptions joined with "and".
   */
  private static Optional<String> grouped(List<CodeOptionResponse> options, String value) {
    return text(value).map(wanted -> {
      String named = GROUPS.get(wanted);
      if (named != null) {
        return named;
      }
      return Arrays.stream(wanted.split(RepairsSummaryCriteria.GROUP_SEPARATOR))
          .map(one -> code(options, one).orElse(one))
          .collect(Collectors.joining(" and "));
    });
  }

  private static <E> Map<String, String> byCode(
      List<E> rows, Function<E, String> code, Function<E, String> name) {
    Map<String, String> names = new HashMap<>();
    rows.forEach(row -> names.put(code.apply(row), name.apply(row)));
    return names;
  }

  /** The code tables' names, each by its code. */
  record Names(
      Map<String, String> statuses,
      Map<String, String> priorities,
      Map<String, String> types,
      Map<String, String> units) {}
}
