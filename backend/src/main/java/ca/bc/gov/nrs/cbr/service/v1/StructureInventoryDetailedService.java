package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.blankIfNull;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.format;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.formatDate;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.filename;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.firstOf;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.number;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.yesNo;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryNames.Codes.describe;

import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureBridgeDetail;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCulvertDetail;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.specification.v1.StructureInventoryDetailLine;
import ca.bc.gov.nrs.cbr.specification.v1.StructureInventoryReportSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Structure Inventory – Detailed report (legacy CBR007): a block of labelled fields per active
 * structure, filtered by the criteria, as a PDF in legacy's layout — or as a CSV of legacy's
 * fuller export, the one its "Excel" format wrote, a row per structure — or as a KMZ of those rows
 * to see on a map.
 *
 * <p>Legacy filled its template from {@code CBR_REPORTING.STRCTRE_INVNTRY_DTL_RPT}. Which
 * structures, in what order, and what a block shows of the structure and its site are one query
 * ({@link StructureInventoryReportSpecifications#detailed}); this class reads the rest a batch of
 * structures at a time — the bridge with its descriptions and span count, the culvert's length
 * where there is no bridge, the last routine inspection — and formats every value as legacy's
 * template printed it.
 *
 * <p>Where it differs from the Summary, it does as legacy's Detailed did: the last routine
 * inspection counts whatever its status, and a structure with no type/class is not listed.
 */
@Service
@Transactional(readOnly = true)
public class StructureInventoryDetailedService {

  private static final Logger log = LoggerFactory.getLogger(StructureInventoryDetailedService.class);

  /** The template under {@code classpath:reports/}. */
  static final String TEMPLATE = "STRUCTURE_INVENTORY_DETAILED";

  /** The legacy report number the header prints. */
  static final String REPORT_ID = "CBR007";

  /** The file's name before the date — legacy's own, {@code StructureInventoryDetailReport}. */
  static final String FILENAME = "StructureInventoryDetailReport";

  private static final String YES = "Y";

  /** Legacy's template pattern for a whole number: {@code #,##0}. */
  private static final String WHOLE = "#,##0";

  /** The CSV's columns: legacy's {@code CBRCSVUtils} headings, in its order, less Structure ID. */
  static final List<String> CSV_COLUMNS = List.of(
      "Structure #", "Site #", "Site Status", "Last Inspection Date", "KM", "User KM",
      "Structure Type/Class", "Portable?", "Project File ID #", "Br.", "BCTS Business Area",
      "Road Name", "Crossing Name", "Superstructure Type", "Deck Type", "Abutment Left End",
      "Abutment Right End", "Design Load GVW (tonnes)", "# Spans", "Total Length (metres)",
      "Year Superstructure Fabricated", "Location Details", "Design Vehicle", "Deck Width",
      "Built For", "Next Planned Inspection Date", "Primary User Name", "Site Type",
      "Estimated Replacement", "Estimated Replacement Cost", "Estimated Closure Date",
      "Estimated Load Restriction", "GPS Coordinates in decimal degrees Longs",
      "GPS Coordinates in decimal degrees Lats", "Source", "Culvert Type", "Culvert Material",
      "Opening Height", "Opening Width", "Culvert Length", "Culvert Gradient",
      "Inlet Cover Depth", "Outlet Cover Depth", "Management Area", "Forest District",
      "Regional Org Unit", "Inspection Status", "Inspection Report Status",
      "Current Load Rating GVW (tonnes)", "Capital Road", "Curb Type",
      "Load Rating Review Required");

  private final EntityManager entityManager;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final StructureInspectionRepository inspections;
  private final ReportRenderer renderer;
  private final StructureInventoryReportSupport support;
  private final StructureInventoryNames names;

  public StructureInventoryDetailedService(
      EntityManager entityManager,
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      StructureInspectionRepository inspections,
      ReportRenderer renderer,
      StructureInventoryReportSupport support,
      StructureInventoryNames names) {
    this.entityManager = entityManager;
    this.bridges = bridges;
    this.culverts = culverts;
    this.inspections = inspections;
    this.renderer = renderer;
    this.support = support;
    this.names = names;
  }

  /** The report, as a PDF or a CSV. */
  public ReportFile generate(StructureInventoryDetailedCriteria criteria, ReportFormat format) {
    long started = System.nanoTime();
    List<Facts> facts = facts(criteria);
    String printed = support.today();
    byte[] content = switch (format) {
      case PDF -> renderer.pdf(TEMPLATE, support.parameters(criteria, REPORT_ID, printed),
          blocks(facts).stream().<Map<String, ?>>map(Block::fields).toList());
      case CSV -> StructureInventoryReportSupport.csv(CSV_COLUMNS, csvRows(facts));
    };
    log.info("Structure Inventory - Detailed: {} structures as {} ({} bytes) in {} ms",
        facts.size(), format, content.length, millisSince(started));
    return new ReportFile(content, filename(FILENAME, printed, format), format.mediaType());
  }

  /** The KMZ's media type: Google Earth's. */
  static final MediaType KMZ = MediaType.parseMediaType("application/vnd.google-earth.kmz");

  /**
   * The report as a KMZ, to see on a map: a placemark per structure with a latitude and longitude,
   * its pop-up legacy's fields, written from the CSV's rows ({@link StructureInventoryKmz}).
   */
  public ReportFile kmz(StructureInventoryDetailedCriteria criteria) {
    long started = System.nanoTime();
    List<Facts> facts = facts(criteria);
    String printed = support.today();
    byte[] content = StructureInventoryKmz.kmz(CSV_COLUMNS, csvRows(facts));
    log.info("Structure Inventory - Detailed: {} structures as KMZ ({} bytes) in {} ms",
        facts.size(), content.length, millisSince(started));
    return new ReportFile(content, FILENAME + "_" + printed.replace('/', '-') + ".kmz", KMZ);
  }

  /** The PDF's blocks, in order, every value as legacy's template printed it. */
  List<Block> blocks(StructureInventoryDetailedCriteria criteria) {
    return blocks(facts(criteria));
  }

  /** The CSV's rows, in order, in {@link #CSV_COLUMNS} order. */
  List<List<String>> csvRows(StructureInventoryDetailedCriteria criteria) {
    return csvRows(facts(criteria));
  }

  /**
   * What the report knows of each structure: its line, and its bridge, culvert and last routine
   * inspection, read a batch of structures at a time. The PDF and the CSV are both made from it,
   * so they cover the same structures and agree on every value they share.
   */
  private List<Facts> facts(StructureInventoryDetailedCriteria criteria) {
    List<StructureInventoryDetailLine> lines = entityManager.createQuery(
        StructureInventoryReportSpecifications.detailed(
            entityManager.getCriteriaBuilder(), criteria)).getResultList();
    List<Long> ids = lines.stream().map(StructureInventoryDetailLine::crossingStructureId).toList();
    Map<Long, StructureBridgeDetail> bridgeDetails = firstOf(ids, bridges::findDetails,
        StructureBridgeDetail::crossingStructureId, Function.identity());
    Map<Long, StructureCulvertDetail> culvertDetails = firstOf(ids, culverts::findDetails,
        StructureCulvertDetail::crossingStructureId, Function.identity());
    Map<Long, LocalDate> lastInspected =
        firstOf(ids, inspections::findLastRoutineInspectionDatesAnyStatus);
    return lines.stream().map(line -> {
      Long id = line.crossingStructureId();
      return new Facts(line, bridgeDetails.get(id), culvertDetails.get(id), lastInspected.get(id));
    }).toList();
  }

  private static List<Block> blocks(List<Facts> facts) {
    return facts.stream().map(fact -> {
      StructureInventoryDetailLine line = fact.line();
      StructureBridgeDetail bridge = fact.bridge();
      return new Block(
          blankIfNull(line.crossingStructureName()),
          blankIfNull(line.crossingSiteId()),
          blankIfNull(line.siteStatusDescription()),
          formatDate(fact.lastInspected()),
          format(line.pointOfCommencementDistance(), "###0.00"),
          format(line.userKm(), "#,##0.00"),
          blankIfNull(line.typeClassDescription()),
          YES.equalsIgnoreCase(line.portableStructureInd()) ? "Yes" : "No",
          blankIfNull(line.forestFileId()),
          blankIfNull(line.roadSectionId()),
          blankIfNull(line.districtName()),
          blankIfNull(line.roadSectName()),
          blankIfNull(line.crossingName()),
          of(bridge, StructureBridgeDetail::superstructureType),
          of(bridge, StructureBridgeDetail::deckType),
          of(bridge, StructureBridgeDetail::leftAbutment),
          of(bridge, StructureBridgeDetail::rightAbutment),
          format(line.designLoadRating(), WHOLE),
          format(line.currentLoadRating(), WHOLE),
          format(BigDecimal.valueOf(fact.spanCount()), WHOLE),
          format(fact.totalLength(), "###0.0"),
          number(line.yearBuilt()),
          of(bridge, StructureBridgeDetail::portableLocationDetail),
          blankIfNull(line.capitalRoadInd()),
          of(bridge, StructureBridgeDetail::curbType),
          blankIfNull(line.loadRatingUnknownIndicator()));
    }).toList();
  }

  /**
   * The CSV's rows, written as the Summary's CSV writes its values: plain numbers, codes as their
   * descriptions, Yes or No. Reads the names the PDF does not print — the maintainer's, the org
   * units', the code tables' — and the latest routine inspection's status.
   */
  private List<List<String>> csvRows(List<Facts> facts) {
    List<Long> ids = facts.stream().map(fact -> fact.line().crossingStructureId()).toList();
    Map<Long, String> statuses = support.latestRoutineStatuses(ids);
    Map<String, String> clientNames =
        names.clientNames(facts.stream().map(fact -> fact.line().clientNumber()).toList());
    Map<Long, String> orgUnitNames = names.orgUnitNames(facts.stream()
        .flatMap(fact -> Stream.of(fact.line().managementOrgUnitNo(),
            fact.line().businessAreaOrgUnitNo(), fact.line().regionOrgUnitNo()))
        .toList());
    StructureInventoryNames.Codes codes = names.codes();

    return facts.stream().map(fact -> {
      StructureInventoryDetailLine line = fact.line();
      StructureBridgeDetail bridge = fact.bridge();
      StructureCulvertDetail culvert = fact.culvert();
      return Arrays.asList(
          blankIfNull(line.crossingStructureName()),
          blankIfNull(line.crossingSiteId()),
          blankIfNull(line.siteStatusDescription()),
          formatDate(fact.lastInspected()),
          plain(line.pointOfCommencementDistance()),
          plain(line.userKm()),
          blankIfNull(line.typeClassDescription()),
          yesNo(line.portableStructureInd()),
          blankIfNull(line.forestFileId()),
          blankIfNull(line.roadSectionId()),
          orgUnit(orgUnitNames, line.businessAreaOrgUnitNo()),
          blankIfNull(line.roadSectName()),
          blankIfNull(line.crossingName()),
          of(bridge, StructureBridgeDetail::superstructureType),
          of(bridge, StructureBridgeDetail::deckType),
          of(bridge, StructureBridgeDetail::leftAbutment),
          of(bridge, StructureBridgeDetail::rightAbutment),
          plain(line.designLoadRating()),
          plain(fact.spanCount()),
          plain(fact.totalLength()),
          number(line.yearBuilt()),
          of(bridge, StructureBridgeDetail::portableLocationDetail),
          describe(codes.designVehicles(), line.designVehicleLoadCode()),
          of(bridge, b -> plain(b.deckWidth())),
          describe(codes.builtBy(), line.builtByCode()),
          formatDate(line.nextPlannedInspectionDate()),
          of(line.clientNumber(), clientNames::get),
          describe(codes.siteTypes(), line.crossingSiteTypeCode()),
          number(line.lightVehicleReplacementYear()),
          plain(line.estimatedReplacementCost()),
          number(line.estimatedClosureYear()),
          number(line.fullLogHaulReplacementYear()),
          plain(line.longitude()),
          plain(line.latitude()),
          describe(codes.sources(), line.structureSourceCode()),
          of(culvert, StructureCulvertDetail::culvertType),
          of(culvert, StructureCulvertDetail::culvertMaterial),
          of(culvert, c -> plain(c.openingHeight())),
          of(culvert, c -> plain(c.openingWidth())),
          of(culvert, c -> plain(c.culvertLength())),
          of(culvert, c -> plain(c.slope())),
          of(culvert, c -> plain(c.inletCoverDepth())),
          of(culvert, c -> plain(c.outletCoverDepth())),
          orgUnit(orgUnitNames, line.managementOrgUnitNo()),
          blankIfNull(line.districtName()),
          orgUnit(orgUnitNames, line.regionOrgUnitNo()),
          describe(codes.inspectionStatuses(), line.structureInspectionStatusCode()),
          blankIfNull(statuses.get(line.crossingStructureId())),
          plain(line.currentLoadRating()),
          yesNo(line.capitalRoadInd()),
          of(bridge, StructureBridgeDetail::curbType),
          yesNo(line.loadRatingUnknownIndicator()));
    }).toList();
  }

  /** A value of the bridge or culvert row — or the maintainer — or blank when there is none. */
  private static <T> String of(T row, Function<T, String> value) {
    return row == null ? "" : blankIfNull(value.apply(row));
  }

  private static String orgUnit(Map<Long, String> names, Long orgUnitNo) {
    return orgUnitNo == null ? "" : blankIfNull(names.get(orgUnitNo));
  }

  /** What the report knows of one structure. */
  private record Facts(
      StructureInventoryDetailLine line,
      StructureBridgeDetail bridge,
      StructureCulvertDetail culvert,
      LocalDate lastInspected) {

    /** Legacy counts the spans of no bridge as 0, so a culvert shows 0. */
    long spanCount() {
      return bridge == null || bridge.spanCount() == null ? 0 : bridge.spanCount();
    }

    /**
     * Legacy's CASE: a structure with a bridge row shows the bridge's length, even when the bridge
     * has none recorded; only one with no bridge shows the culvert's.
     */
    BigDecimal totalLength() {
      if (bridge != null) {
        return bridge.totalLength();
      }
      return culvert == null ? null : culvert.culvertLength();
    }
  }


  /** One structure's block, every value as it prints. */
  record Block(
      String structureName,
      String siteId,
      String siteStatus,
      String lastInspectionDate,
      String kilometres,
      String userKm,
      String structureType,
      String portable,
      String forestFileId,
      String roadSectionId,
      String forestDistrict,
      String roadName,
      String crossingName,
      String superstructureType,
      String deckType,
      String leftAbutment,
      String rightAbutment,
      String designLoadRating,
      String loadRating,
      String spanCount,
      String totalLength,
      String yearBuilt,
      String locationDetail,
      String capitalRoad,
      String curbType,
      String loadRatingReview) {

    /** Keyed by the template's field names. */
    Map<String, String> fields() {
      Map<String, String> fields = new LinkedHashMap<>();
      fields.put("CROSSING_STRUCTURE_NAME", structureName);
      fields.put("CROSSING_SITE_ID", siteId);
      fields.put("SITE_STATUS", siteStatus);
      fields.put("INSPECTION_DATE", lastInspectionDate);
      fields.put("KM", kilometres);
      fields.put("USER_KM", userKm);
      fields.put("STRUCTURE_TYPE", structureType);
      fields.put("PORTABLE", portable);
      fields.put("FOREST_FILE_ID", forestFileId);
      fields.put("ROAD_SECTION_ID", roadSectionId);
      fields.put("FOREST_DISTRICT", forestDistrict);
      fields.put("ROAD_SECT_NAME", roadName);
      fields.put("CROSSING_NAME", crossingName);
      fields.put("SUPERSTRUCTURE_TYPE", superstructureType);
      fields.put("DECK_TYPE", deckType);
      fields.put("LEFT_ABUTMENT", leftAbutment);
      fields.put("RIGHT_ABUTMENT", rightAbutment);
      fields.put("DESIGN_LOAD_RATING", designLoadRating);
      fields.put("LOAD_RATING", loadRating);
      fields.put("SPAN_COUNT", spanCount);
      fields.put("TOTAL_LENGTH", totalLength);
      fields.put("YEAR_BUILT", yearBuilt);
      fields.put("LOCATION_DETAIL", locationDetail);
      fields.put("CAPITAL_ROAD", capitalRoad);
      fields.put("CURB_TYPE", curbType);
      fields.put("LOAD_RATING_REVIEW", loadRatingReview);
      return fields;
    }
  }
}
