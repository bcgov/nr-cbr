package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.format;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.formatDate;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.yesNo;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The Structure Detail Report (legacy CBR006): the full record of one structure, as a PDF in
 * legacy's layout or a CSV of the same fields. Its rows are {@link StructureDetailRows}'.
 *
 * <p>Legacy had no CSV — its other formats were Word and Excel exports of the page. This one is a
 * header and a row of values, as the Structure Inventory – Detailed CSV is: the PDF's fields in
 * its order, a code and the comment that stands in for it in columns of their own, the abutments
 * left and right apart, and values as the other CSVs write them — plain numbers, descriptions,
 * Yes/No. A structure with more than one bridge or culvert record is a row per pair, as the PDF
 * prints a page per pair.
 */
@Service
public class StructureDetailReportService {

  private static final Logger log = LoggerFactory.getLogger(StructureDetailReportService.class);

  /** The template under {@code classpath:reports/}. */
  static final String TEMPLATE = "STRUCTURE_DETAIL";

  /** The legacy report number the header prints. */
  static final String REPORT_ID = "CBR006";

  /** The file's name before the structure and the date. */
  static final String FILENAME = "StructureDetailReport";

  /** Legacy's template patterns. */
  private static final String WHOLE = "#,##0";
  private static final String ONE_PLACE = "###0.0";
  private static final String TWO_PLACES = "###0.00";

  /** The pattern each number prints in on the PDF; a number not here prints as it is. */
  private static final Map<String, String> PDF_PATTERNS = Map.ofEntries(
      Map.entry("POINT_OF_COMMENCEMENT_DISTANCE", TWO_PLACES),
      Map.entry("LOAD_RATING", WHOLE),
      Map.entry("DESIGN_LOAD_RATING", WHOLE),
      Map.entry("INSTALLATION_COST", WHOLE),
      Map.entry("MATERIAL_COST", WHOLE),
      Map.entry("ESTIMATED_REPLACEMENT_COST", WHOLE),
      Map.entry("CULVERT_LENGTH", ONE_PLACE),
      Map.entry("SLOPE", ONE_PLACE),
      Map.entry("INLET_COVER_DEPTH", WHOLE),
      Map.entry("OUTLET_COVER_DEPTH", WHOLE),
      Map.entry("OPENING_HEIGHT", WHOLE),
      Map.entry("OPENING_WIDTH", WHOLE),
      Map.entry("TOTAL_BRIDGE_LENGTH", ONE_PLACE),
      Map.entry("DECK_WIDTH", TWO_PLACES));

  /** The CSV's columns, in the PDF's order, each with the field it writes. */
  private static final List<Column> CSV = List.of(
      text("Structure #", "CROSSING_STRUCTURE_NAME"),
      text("Site #", "CROSSING_SITE_ID"),
      text("Structure Type/Class", "STRUCTURE_TYPE_CLASS_DESC"),
      text("Crossing Name", "CROSSING_NAME"),
      text("Forest Service Road/Project Name", "FOREST_SERVICE_ROAD"),
      text("KM", "POINT_OF_COMMENCEMENT_DISTANCE"),
      text("Forest District", "ORG_UNIT_NAME"),
      text("Management Area", "MANAGEMENT_AREA"),
      text("Project File ID #", "FOREST_FILE_ID"),
      text("Br.", "ROAD_SECTION_ID"),
      text("Built For", "BUILT_FOR"),
      text("Year Superstructure Installed", "YEAR_BUILT"),
      text("Year Superstructure Fabricated", "YEAR_FABRICATED"),
      text("Original Replacement Date", "ORIGINAL_REPLACEMENT_DATE"),
      text("Year Added to Inventory", "INVENTORY_ADDED_YEAR"),
      text("Source", "STRUCTURE_SOURCE"),
      text("Current Load Rating GVW (tonnes)", "LOAD_RATING"),
      text("Design Load Rating GVW (tonnes)", "DESIGN_LOAD_RATING"),
      text("Design Vehicle Config.", "DESIGN_VEHICLE"),
      text("Primary User Name", "CLIENT_NAME"),
      indicator("As Built Information on File?", "AS_BUILT_INFO_PRESENT_IND"),
      text("Installation Cost ($)", "INSTALLATION_COST"),
      text("Material Cost ($)", "MATERIAL_COST"),
      indicator("Portable Structure?", "PORTABLE_STRUCTURE_IND"),
      text("Culvert Number", "CULVERT_NUMBER"),
      text("Culvert Length (metres)", "CULVERT_LENGTH"),
      text("Gradient (%)", "SLOPE"),
      text("Culvert Type", "CULVERT_TYPE"),
      text("Culvert Material", "CULVERT_MATERIAL"),
      text("Culvert Material Comment", "CULVERT_MATERIAL_COMMENT"),
      text("Inlet Cover Depth (mm)", "INLET_COVER_DEPTH"),
      text("Outlet Cover Depth (mm)", "OUTLET_COVER_DEPTH"),
      text("Opening Height (mm)", "OPENING_HEIGHT"),
      text("Opening Width (mm)", "OPENING_WIDTH"),
      text("Headwall Location", "HEADWALL_LOCATION"),
      text("Open Bottom Substructure", "OPN_BOTTOM_CLVRT_SUB"),
      text("Number of Spans", "SPAN_COUNT"),
      text("Span Lengths", "SPAN_LENGTHS"),
      indicator("Needle Beams?", "NEEDLE_BEAM_IND"),
      text("Bridge Length (metres)", "TOTAL_BRIDGE_LENGTH"),
      text("Superstructure", "SUPERSTRUCTURE"),
      text("Superstructure Comment", "SUPERSTRUCTURE_COMMENT"),
      text("Deck Width (metres)", "DECK_WIDTH"),
      text("Deck Type", "DECK_TYPE"),
      text("Deck Type Comment", "DECK_TYPE_COMMENT"),
      text("Running Surface", "RUNNING_SURFACE"),
      text("Abutment Left End", "LEFT_ABUTMENT"),
      text("Abutment Right End", "RIGHT_ABUTMENT"),
      text("Abutment Comment", "ABUTMENT_COMMENT"),
      text("Curb Type", "STRUCTURE_CURB_TYPE"),
      text("Curb Type Comment", "STRUCTURE_CURB_TYPE_CMT"),
      text("Comments", "STRUCT_COMMENT"),
      text("This structure replaced structure #", "REPLACES"),
      text("This structure was replaced by structure #", "REPLACED_BY"),
      text("Estimated Closure (year)", "ESTIMATED_CLOSURE_DATE"),
      text("Estimated Replacement (year)", "LIGHT_VEHICLE_TRFFC_RPLCMNT_DT"),
      text("Estimated Load Restriction (year)", "FULL_LOG_HAUL_TRFFC_RPLCMNT_DT"),
      text("Estimated Replacement Cost ($)", "ESTIMATED_REPLACEMENT_COST"),
      text("Replacement Cost Comments", "ESTIMATED_REPLACEMENT_COST_CMT"),
      text("Next Planned Inspection", "NEXT_PLANNED_INSPECTION_DATE"),
      text("Planned Inspection Comments", "PLANNED_COMMENT"));

  /** The CSV's header. */
  static final List<String> CSV_COLUMNS = CSV.stream().map(Column::heading).toList();

  private final StructureDetailRows rows;
  private final ReportRenderer renderer;
  private final StructureInventoryReportSupport support;

  public StructureDetailReportService(
      StructureDetailRows rows, ReportRenderer renderer, StructureInventoryReportSupport support) {
    this.rows = rows;
    this.renderer = renderer;
    this.support = support;
  }

  /**
   * The structure's report, named for the structure: {@code
   * StructureDetailReport_<Structure #>_<date>.pdf} or {@code .csv}.
   *
   * @throws StructureNotFoundException when there is no such structure, or it is archived
   */
  public ReportFile generate(long structureId, ReportFormat format) {
    long started = System.nanoTime();
    List<Map<String, Object>> detail = rows.rows(structureId);
    String printed = support.today();
    byte[] content = switch (format) {
      case PDF -> renderer.pdf(TEMPLATE, support.parameters(REPORT_ID, printed),
          detail.stream().<Map<String, ?>>map(StructureDetailReportService::pdfRow).toList());
      case CSV -> StructureInventoryReportSupport.csv(CSV_COLUMNS,
          detail.stream().map(StructureDetailReportService::csvRow).toList());
    };
    String structureName = detail.isEmpty()
        ? Long.toString(structureId)
        : Objects.toString(detail.getFirst().get("CROSSING_STRUCTURE_NAME"), "");
    log.info("Structure Detail Report: structure {} as {} ({} rows, {} bytes) in {} ms",
        structureId, format, detail.size(), content.length, millisSince(started));
    return new ReportFile(content,
        StructureInventoryReportSupport.filename(FILENAME + "_" + structureName, printed, format),
        format.mediaType());
  }

  /** A row as the PDF prints it: each number in legacy's pattern, each date as the reports do. */
  static Map<String, String> pdfRow(Map<String, Object> row) {
    Map<String, String> printed = new HashMap<>();
    row.forEach((field, value) -> printed.put(field, pdf(field, value)));
    return printed;
  }

  /** A row as the CSV writes it, in {@link #CSV_COLUMNS} order. */
  static List<String> csvRow(Map<String, Object> row) {
    return CSV.stream().map(column -> column.value().apply(row.get(column.field()))).toList();
  }

  private static String pdf(String field, Object value) {
    String pattern = PDF_PATTERNS.get(field);
    if (pattern != null && value != null) {
      return format(decimal(value), pattern);
    }
    if (value instanceof LocalDate date) {
      return formatDate(date);
    }
    return value == null ? null : value.toString();
  }

  /** A value as the CSVs write it: a number plain, without grouping or trailing zeros. */
  private static String csv(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof BigDecimal || value instanceof Long) {
      return StructureInventoryReportSupport.plain(decimal(value));
    }
    if (value instanceof LocalDate date) {
      return formatDate(date);
    }
    return value.toString();
  }

  private static BigDecimal decimal(Object value) {
    return value instanceof BigDecimal decimal
        ? decimal
        : new BigDecimal(value.toString());
  }

  private static Column text(String heading, String field) {
    return new Column(heading, field, StructureDetailReportService::csv);
  }

  private static Column indicator(String heading, String field) {
    return new Column(heading, field, value -> yesNo((String) value));
  }

  /** A CSV column: its heading, the row's field it writes, and how. */
  private record Column(String heading, String field, Function<Object, String> value) {}
}
