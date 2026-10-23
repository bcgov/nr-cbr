package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.InspectionsDueRows.grouped;
import static ca.bc.gov.nrs.cbr.service.v1.InspectionsDueRows.orNone;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.blankIfNull;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.format;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.number;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.yesNo;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureValue;
import ca.bc.gov.nrs.cbr.specification.v1.ReplacementStructureLine;
import ca.bc.gov.nrs.cbr.specification.v1.ReplacementStructuresReportSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementStructuresCriteria;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The rows the Replacement Structures Summary (legacy CBR009) fills its template with, every value
 * as legacy's template printed it, keyed by its field names.
 *
 * <p>Legacy's {@code CBR_REPORTING.RPLCMNT_STRUCTURE_RPT} is a union of three sections, a
 * structure listed in each it has a year for: its Estimated Load Restriction, its Estimated
 * Replacement, and — on an active site only — its Estimated Closure. Which structures is one query
 * ({@link ReplacementStructuresReportSpecifications}); the sections, their order and their totals
 * are built here, as legacy's ordered them: by region, district and management area, by name;
 * then the year, latest first, Load Restriction before Replacement before Closure in a year; then
 * the road, by name, and the kilometre along it.
 *
 * <p>A structure is a row per bridge record and culvert record it has, as legacy's left joins made
 * it — its Total Length the bridge's, else the culvert's — less any bridge whose portable
 * superstructure is in service ({@code S}). Each year's total counts and costs those rows, as
 * legacy's did.
 *
 * <p>The CSV is the PDF's rows, in its order, each carrying the headings the PDF prints above it —
 * region, district, management area, year and section — with its values written as the other CSVs
 * write them: plain numbers, the type/class code, Project File ID and Br. apart, Capital Road Yes
 * or No. The totals are left for the spreadsheet to sum.
 */
@Component
@Transactional(readOnly = true)
public class ReplacementStructuresRows {

  /** A portable superstructure in service: legacy left its bridge record out. */
  private static final String IN_SERVICE = "S";
  /** Closures are listed for active sites only. */
  private static final String ACTIVE = "ACT";

  /** Legacy's template patterns. */
  private static final String LENGTH = "#,##0.0";
  private static final String WHOLE = "#,##0";

  /** Legacy's order, which its stored procedure applied. */
  private static final Comparator<Entry> ORDER = Comparator
      .comparing((Entry entry) -> entry.where().region(), nullsLast())
      .thenComparing(entry -> entry.where().district(), nullsLast())
      .thenComparing(entry -> entry.where().managementArea(), nullsLast())
      .thenComparing(Entry::sortYear, Comparator.reverseOrder())
      .thenComparing(entry -> entry.line().roadSectionName(), nullsLast())
      .thenComparing(entry -> entry.line().kilometres(), nullsLast());

  /** Legacy's sections, in the order they follow one another within a year. */
  enum Section {
    LOAD_RESTRICTION(3, "Estimated Load", ReplacementStructureLine::loadRestrictionYear),
    REPLACEMENT(2, "Estimated Replacement", ReplacementStructureLine::replacementYear),
    CLOSURE(1, "Closure", ReplacementStructureLine::closureYear);

    /** Legacy's {@code SORT_YEAR} suffix: the year's digits, then this. */
    private final int rank;
    /** "REPLACEMENT FOR:", as legacy printed it. */
    private final String label;
    private final Function<ReplacementStructureLine, Integer> year;

    Section(int rank, String label, Function<ReplacementStructureLine, Integer> year) {
      this.rank = rank;
      this.label = label;
      this.year = year;
    }
  }

  private final EntityManager entityManager;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final StructureInventoryNames names;

  public ReplacementStructuresRows(
      EntityManager entityManager,
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      StructureInventoryNames names) {
    this.entityManager = entityManager;
    this.bridges = bridges;
    this.culverts = culverts;
    this.names = names;
  }

  /** The CSV's columns: the PDF's headings, then its table's columns, in its order. */
  static final List<String> CSV_COLUMNS = List.of(
      "Forest Region", "Forest District", "Management Area", "Replacement Year",
      "Replacement For", "Structure #", "Site #", "Road Name", "Project File ID #", "Br.",
      "Crossing Name", "Type/Class", "Total Length (metres)", "Design Load Rating GVW (tonnes)",
      "Current Load Rating GVW (tonnes)", "Primary User Name", "Replacement Cost ($)",
      "End of Design Life", "Capital Road");

  /** The CSV's rows, the PDF's, in {@link #CSV_COLUMNS} order. */
  public List<List<String>> csvRows(ReplacementStructuresCriteria criteria) {
    return entries(criteria).stream().map(entry -> {
      ReplacementStructureLine line = entry.line();
      return List.of(
          blankIfNull(entry.where().region()),
          blankIfNull(entry.where().district()),
          blankIfNull(entry.where().managementArea()),
          entry.year().toString(),
          entry.section().label,
          blankIfNull(line.structureName()),
          blankIfNull(line.siteId()),
          blankIfNull(line.roadSectionName()),
          blankIfNull(line.forestFileId()),
          blankIfNull(line.roadSectionId()),
          blankIfNull(line.crossingName()),
          blankIfNull(line.structureTypeClassCode()),
          plain(entry.totalLength()),
          plain(line.designLoadRating()),
          plain(line.currentLoadRating()),
          blankIfNull(entry.client()),
          plain(line.estimatedReplacementCost()),
          number(line.endOfDesignLife()),
          yesNo(line.capitalRoadInd()));
    }).toList();
  }

  /** The rows, in order: a structure per section it has a year for, each per bridge and culvert. */
  public List<Map<String, String>> rows(ReplacementStructuresCriteria criteria) {
    List<Entry> entries = entries(criteria);
    Map<String, Totals> totals = new LinkedHashMap<>();
    for (Entry entry : entries) {
      totals.computeIfAbsent(entry.yearKey(), key -> new Totals()).add(entry.line());
    }
    return entries.stream().map(entry -> row(entry, totals.get(entry.yearKey()))).toList();
  }

  private List<Entry> entries(ReplacementStructuresCriteria criteria) {
    List<ReplacementStructureLine> lines = entityManager
        .createQuery(ReplacementStructuresReportSpecifications.lines(
            entityManager.getCriteriaBuilder(), criteria))
        .getResultList();
    List<Long> ids = lines.stream().map(ReplacementStructureLine::structureId).toList();
    Map<Long, List<ForestServiceBridgeEntity>> bridgeRecords = grouped(ids,
        bridges::findByCrossingStructureIdInOrderByForestServiceBridgeId,
        ForestServiceBridgeEntity::getCrossingStructureId);
    Map<Long, List<StructureValue<BigDecimal>>> culvertLengths =
        grouped(ids, culverts::findLengths, StructureValue::crossingStructureId);
    Map<Long, String> orgUnits = names.orgUnitNames(lines.stream()
        .flatMap(line -> Stream.of(line.regionOrgUnitNo(), line.districtOrgUnitNo(),
            line.managementOrgUnitNo()))
        .toList());
    Map<String, String> clients = names.clientNames(
        lines.stream().map(ReplacementStructureLine::clientNumber).toList());

    List<Entry> entries = new ArrayList<>();
    for (ReplacementStructureLine line : lines) {
      Where where = new Where(orgUnits.get(line.regionOrgUnitNo()),
          orgUnits.get(line.districtOrgUnitNo()), orgUnits.get(line.managementOrgUnitNo()));
      String client = line.clientNumber() == null ? null : clients.get(line.clientNumber());
      List<BigDecimal> lengths = lengths(
          bridgeRecords.get(line.structureId()), culvertLengths.get(line.structureId()));
      for (Section section : Section.values()) {
        Integer year = section.year.apply(line);
        if (year != null && (section != Section.CLOSURE || ACTIVE.equals(line.siteStatusCode()))) {
          lengths.forEach(length -> entries.add(
              new Entry(line, section, year, where, client, length)));
        }
      }
    }
    entries.sort(ORDER);
    return entries;
  }

  /**
   * The Total Length of each row a structure is listed as: one per bridge record and culvert record,
   * as legacy's left joins made it — the bridge's length, else the culvert's — less the bridges
   * whose portable superstructure is in service. A structure with only such bridges is not listed.
   */
  private static List<BigDecimal> lengths(
      List<ForestServiceBridgeEntity> bridgeRecords,
      List<StructureValue<BigDecimal>> culvertLengths) {
    List<BigDecimal> lengths = new ArrayList<>();
    for (ForestServiceBridgeEntity bridge : orNone(bridgeRecords)) {
      if (bridge == null || !IN_SERVICE.equals(bridge.getPortableSuperstructureStatusCode())) {
        for (StructureValue<BigDecimal> culvert : orNone(culvertLengths)) {
          lengths.add(length(bridge, culvert));
        }
      }
    }
    return lengths;
  }

  private static BigDecimal length(
      ForestServiceBridgeEntity bridge, StructureValue<BigDecimal> culvert) {
    if (bridge != null) {
      return bridge.getTotalBridgeLength();
    }
    return culvert == null ? null : culvert.value();
  }

  private static <T extends Comparable<? super T>> Comparator<T> nullsLast() {
    return Comparator.nullsLast(Comparator.naturalOrder());
  }

  private static Map<String, String> row(Entry entry, Totals totals) {
    ReplacementStructureLine line = entry.line();
    Map<String, String> row = new HashMap<>();
    row.put("SECTION_KEY", entry.sectionKey());
    row.put("YEAR_KEY", entry.yearKey());
    row.put("FOREST_REGION_NAME", entry.where().region());
    row.put("FOREST_DISTRICT_NAME", entry.where().district());
    row.put("MANAGEMENT_AREA_NAME", entry.where().managementArea());
    row.put("YEAR", entry.year().toString());
    row.put("REPLACEMENT_FOR", entry.section().label);
    row.put("CROSSING_STRUCTURE_NAME", line.structureName());
    row.put("CROSSING_SITE_ID", line.siteId());
    row.put("FOREST_SERVICE_ROAD", line.roadSectionName());
    row.put("FOREST_FILE_ID", line.forestFileId());
    row.put("ROAD_SECTION_ID", line.roadSectionId());
    row.put("CROSSING_NAME", line.crossingName());
    row.put("STRUCTURE_TYPE_CLASS_CODE", line.structureTypeClassCode());
    row.put("TOTAL_LENGTH", decimal(entry.totalLength(), LENGTH));
    row.put("DESIGN_LOAD_RATING", decimal(line.designLoadRating(), WHOLE));
    row.put("LOAD_RATING", decimal(line.currentLoadRating(), WHOLE));
    row.put("CLIENT_NAME", entry.client());
    row.put("ESTIMATED_REPLACEMENT_COST", line.estimatedReplacementCost() == null
        ? null
        : format(BigDecimal.valueOf(line.estimatedReplacementCost()), WHOLE));
    row.put("END_OF_DESIGN_LIFE", Objects.toString(line.endOfDesignLife(), null));
    row.put("CAPITAL_ROAD_IND", line.capitalRoadInd());
    row.put("TOTAL_COST", totals.cost == null ? null : format(totals.cost, WHOLE));
    row.put("TOTAL_COUNT", format(BigDecimal.valueOf(totals.count), WHOLE));
    return row;
  }

  private static String decimal(BigDecimal value, String pattern) {
    return value == null ? null : format(value, pattern);
  }

  /** The names of a structure's region, district and management area. */
  private record Where(String region, String district, String managementArea) {}

  /** One printed row: the structure, the section it is listed under, and its Total Length. */
  private record Entry(
      ReplacementStructureLine line, Section section, Integer year, Where where, String client,
      BigDecimal totalLength) {

    /** Legacy's {@code SORT_YEAR}: the year, then the section's rank. */
    long sortYear() {
      return year * 10L + section.rank;
    }

    /** Legacy's page break: a district and management area to a page. */
    String sectionKey() {
      return line.regionOrgUnitNo() + "-" + line.districtOrgUnitNo() + "-"
          + line.managementOrgUnitNo();
    }

    /** Legacy's year group, within its page's: a year and section each, with its totals. */
    String yearKey() {
      return sectionKey() + "|" + sortYear();
    }
  }

  /** A year's totals: legacy's count of rows with a Structure #, and the sum of their costs. */
  private static final class Totals {

    private int count;
    private BigDecimal cost;

    void add(ReplacementStructureLine line) {
      if (StringUtils.hasLength(line.structureName())) {
        count++;
      }
      if (line.estimatedReplacementCost() != null) {
        BigDecimal value = BigDecimal.valueOf(line.estimatedReplacementCost());
        cost = cost == null ? value : cost.add(value);
      }
    }
  }
}
