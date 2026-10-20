package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.blankIfNull;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.formatDate;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.filename;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.firstOf;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.number;

import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.specification.v1.StructureInventoryLine;
import ca.bc.gov.nrs.cbr.specification.v1.StructureInventoryReportSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The Structure Inventory – Summary report (legacy CBR008): one line per active structure, filtered
 * by the criteria, as a PDF in legacy's layout or as a CSV of the same columns.
 *
 * <p>Legacy filled its template from {@code CBR_REPORTING.STRCTRE_INVNTRY_SUMMRY_RPT}. Which
 * structures, in what order, and the structure's and site's values each line prints, are now one
 * query ({@link StructureInventoryReportSpecifications}); this class reads the rest — the bridge or
 * culvert length, the last routine inspection and its status — a batch of structures at a time,
 * and formats every value once, as legacy's template printed it, for both outputs.
 *
 * <p><b>Current Load Rating</b> is the structure's {@code CURRENT_LOAD_RATING}. Legacy's summary
 * worked it out per structure with {@code FIND_CRNT_LD_RATING_BY_STRC_ID}; the column holds the same
 * answer, kept by the review ({@link LoadRatingService}), and is what legacy's Detailed report
 * already reads. Working it out again would cost several queries a structure.
 */
@Service
@Transactional(readOnly = true)
public class StructureInventorySummaryService {

  private static final Logger log = LoggerFactory.getLogger(StructureInventorySummaryService.class);

  /** The template under {@code classpath:reports/}. */
  static final String TEMPLATE = "STRUCTURE_INVENTORY_SUMMARY";

  /** The legacy report number the header prints. */
  static final String REPORT_ID = "CBR008";

  /** The file's name before the date, in legacy's pattern for its reports' files. */
  static final String FILENAME = "StructureInventorySummaryReport";

  /** The columns, in order: the CSV's header, and what the PDF's column headings say. */
  static final List<String> COLUMNS = List.of(
      "Site #", "Structure #", "Project File ID # / Br.", "Road Name", "Crossing Name",
      "Portable?", "KM", "Total Length (metres)", "Structure Type/Class",
      "Current Load Rating GVW (tonnes)", "Last Inspection Date", "Year Superstructure Installed",
      "Inspection Status", "Estimated Load Restriction Date", "Capital Road");

  private static final String YES = "Y";

  private final EntityManager entityManager;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final StructureInspectionRepository inspections;
  private final ConfigurationService codes;
  private final ReportRenderer renderer;
  private final StructureInventoryReportSupport support;

  public StructureInventorySummaryService(
      EntityManager entityManager,
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      StructureInspectionRepository inspections,
      ConfigurationService codes,
      ReportRenderer renderer,
      StructureInventoryReportSupport support) {
    this.entityManager = entityManager;
    this.bridges = bridges;
    this.culverts = culverts;
    this.inspections = inspections;
    this.codes = codes;
    this.renderer = renderer;
    this.support = support;
  }

  /** The report, as asked for. */
  public ReportFile generate(StructureInventorySummaryCriteria criteria, ReportFormat format) {
    long started = System.nanoTime();
    List<Row> rows = rows(criteria, format == ReportFormat.CSV ? dataStyle() : Style.PRINTED);
    long built = System.nanoTime();
    String printed = support.today();
    byte[] content = switch (format) {
      case PDF -> renderer.pdf(TEMPLATE, support.parameters(criteria, REPORT_ID, printed),
          rows.stream().<Map<String, ?>>map(Row::fields).toList());
      case CSV -> csv(rows);
    };
    log.info("Structure Inventory - Summary: {} rows as {} ({} bytes) — rows {} ms, {} {} ms",
        rows.size(), format, content.length, (built - started) / 1_000_000, format,
        millisSince(built));
    return new ReportFile(content, filename(FILENAME, printed, format), format.mediaType());
  }

  /** The lines of the report, in order, every value as the PDF prints it. */
  List<Row> rows(StructureInventorySummaryCriteria criteria) {
    return rows(criteria, Style.PRINTED);
  }

  /** The lines of the report, in order, every value written in this style. */
  List<Row> rows(StructureInventorySummaryCriteria criteria, Style style) {
    List<StructureInventoryLine> lines = entityManager.createQuery(
        StructureInventoryReportSpecifications.summary(
            entityManager.getCriteriaBuilder(), criteria)).getResultList();
    List<Long> ids = lines.stream().map(StructureInventoryLine::crossingStructureId).toList();
    Map<Long, BigDecimal> bridgeLengths = firstOf(ids, bridges::findLengths);
    Map<Long, BigDecimal> culvertLengths = firstOf(ids, culverts::findLengths);
    Map<Long, LocalDate> lastInspected = firstOf(ids, inspections::findLastRoutineInspectionDates);
    Map<Long, String> statuses = support.latestRoutineStatuses(ids);

    return lines.stream().map(line -> {
      Long id = line.crossingStructureId();
      // Legacy's CASE: a structure with a bridge row shows the bridge's length, even when the
      // bridge has none recorded; only one with no bridge shows the culvert's.
      BigDecimal length = bridgeLengths.containsKey(id)
          ? bridgeLengths.get(id) : culvertLengths.get(id);
      return new Row(
          blankIfNull(line.crossingSiteId()),
          blankIfNull(line.crossingStructureName()),
          projectFile(line.forestFileId(), line.roadSectionId()),
          blankIfNull(line.roadSectName()),
          blankIfNull(line.crossingName()),
          YES.equalsIgnoreCase(line.portableStructureInd()) ? "Yes" : "No",
          line.pointOfCommencementDistance() == null ? ""
              : style.kilometres().apply(line.pointOfCommencementDistance()),
          length == null ? "" : style.length().apply(length),
          style.typeClass().apply(line.structureTypeClassCode()),
          line.currentLoadRating() == null ? ""
              : line.currentLoadRating().setScale(0, RoundingMode.HALF_EVEN).toPlainString(),
          // Present but empty when every routine inspection is undated — MAX of nothing.
          formatDate(lastInspected.get(id)),
          number(line.yearBuilt()),
          blankIfNull(statuses.get(id)),
          number(line.fullLogHaulReplacementYear()),
          style.capitalRoad().apply(line.capitalRoadInd()));
    }).toList();
  }

  /** The CSV's style, with the type/class descriptions it names in full. */
  private Style dataStyle() {
    Map<String, String> typeClasses = new HashMap<>();
    codes.getStructureTypeClassCodes().forEach(option ->
        typeClasses.put(option.code(), option.description()));
    return Style.data(typeClasses);
  }

  /**
   * How a line's values are written. The PDF prints them as legacy's template did — a thousands
   * separator, two decimals of kilometres, the one-letter type/class, Y or N. The CSV is data to
   * sort, sum and filter: plain numbers, and codes named in full.
   */
  record Style(
      Function<BigDecimal, String> kilometres,
      Function<BigDecimal, String> length,
      Function<String, String> typeClass,
      Function<String, String> capitalRoad) {

    static final Style PRINTED = new Style(
        km -> km.setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
        length -> lengthFormat().format(length),
        StructureInventorySummaryService::typeClass,
        StructureInventoryReportSupport::blankIfNull);

    /** Plain numbers, the type/class's description, and Yes or No. */
    static Style data(Map<String, String> typeClasses) {
      return new Style(
          StructureInventoryReportSupport::plain,
          StructureInventoryReportSupport::plain,
          code -> code == null ? "" : typeClasses.getOrDefault(code, code),
          StructureInventoryReportSupport::yesNo);
    }
  }

  private static byte[] csv(List<Row> rows) {
    return StructureInventoryReportSupport.csv(COLUMNS, rows.stream().map(Row::values).toList());
  }

  /** "R00123-01": the project, and its branch after a dash when there is one. */
  private static String projectFile(String forestFileId, String roadSectionId) {
    String file = blankIfNull(forestFileId);
    return StringUtils.hasLength(roadSectionId) ? file + "-" + roadSectionId : file;
  }

  /** Legacy's one-letter type/class: P, T, MC (a culvert), W (a wood-log culvert). */
  private static String typeClass(String code) {
    if (code == null) {
      return "";
    }
    return switch (code.toUpperCase(Locale.ROOT)) {
      case "PB" -> "P";
      case "TB" -> "T";
      case "CUL" -> "MC";
      case "WLC" -> "W";
      default -> "";
    };
  }

  /** Legacy's {@code #,##0.0}. Made per call: {@link DecimalFormat} is not thread-safe. */
  private static DecimalFormat lengthFormat() {
    DecimalFormat format = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.CANADA));
    format.setRoundingMode(RoundingMode.HALF_EVEN);
    return format;
  }
  /** One line of the report, every value as it prints. */
  record Row(
      String siteId,
      String structureName,
      String projectFile,
      String roadName,
      String crossingName,
      String portable,
      String kilometres,
      String totalLength,
      String typeClass,
      String loadRating,
      String lastInspectionDate,
      String yearBuilt,
      String inspectionStatus,
      String loadRestrictionYear,
      String capitalRoad) {

    /** In {@link #COLUMNS} order, for the CSV. */
    List<String> values() {
      return List.of(siteId, structureName, projectFile, roadName, crossingName, portable,
          kilometres, totalLength, typeClass, loadRating, lastInspectionDate, yearBuilt,
          inspectionStatus, loadRestrictionYear, capitalRoad);
    }

    /** Keyed by the template's field names, for the PDF. */
    Map<String, String> fields() {
      Map<String, String> fields = new LinkedHashMap<>();
      fields.put("CROSSING_SITE_ID", siteId);
      fields.put("CROSSING_STRUCTURE_NAME", structureName);
      fields.put("PROJECT_FILE", projectFile);
      fields.put("ROAD_SECT_NAME", roadName);
      fields.put("CROSSING_NAME", crossingName);
      fields.put("PORTABLE", portable);
      fields.put("KM", kilometres);
      fields.put("TOTAL_LENGTH", totalLength);
      fields.put("TYPE_CLASS", typeClass);
      fields.put("LOAD_RATING", loadRating);
      fields.put("INSPECTION_DATE", lastInspectionDate);
      fields.put("YEAR_BUILT", yearBuilt);
      fields.put("INSPECTION_STATUS", inspectionStatus);
      fields.put("LOAD_RESTRICTION_YEAR", loadRestrictionYear);
      fields.put("CAPITAL_ROAD", capitalRoad);
      return fields;
    }
  }
}
