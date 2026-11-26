package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.filename;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.firstOf;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.format;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;

import ca.bc.gov.nrs.cbr.repository.v1.ReportQueryHints;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.service.v1.RepairsSummaryNames.Names;
import ca.bc.gov.nrs.cbr.specification.v1.RepairsSummaryLine;
import ca.bc.gov.nrs.cbr.specification.v1.RepairsSummaryReportSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.RepairsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionKey;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.hibernate.jpa.HibernateHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The Repairs Summary report (legacy CBR005): the repairs recorded against structures, a page per
 * structure, grouped by the inspection that found them — as a PDF in legacy's layout, or as the
 * CSV legacy offered as "Excel" ({@link RepairsSummaryCsv}).
 *
 * <p>Which repairs, and in what order, is one query ({@link RepairsSummaryReportSpecifications});
 * this class reads beside it the code tables' names and each structure's last routine inspection,
 * and formats every value as the template prints it.
 */
@Service
@Transactional(readOnly = true)
public class RepairsSummaryService {

  private static final Logger log = LoggerFactory.getLogger(RepairsSummaryService.class);

  /** The template under {@code classpath:reports/}. */
  static final String TEMPLATE = "REPAIRS_SUMMARY";

  /** The legacy report number the header prints. */
  static final String REPORT_ID = "CBR005";

  /** The file's name before the date. */
  static final String FILENAME = "RepairsSummaryReport";

  /** What the structure header says when it has had no routine inspection — legacy's words. */
  static final String NO_ROUTINE_INSPECTION = "Not attached to inspection";

  /** What a group of repairs says when they were recorded on no inspection. */
  static final String NO_INSPECTION = "Not attached to an inspection";

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** Tenths of a second in a degree. */
  private static final BigDecimal TENTHS_PER_DEGREE = BigDecimal.valueOf(36_000);

  private final EntityManager entityManager;
  private final StructureInspectionRepository inspections;
  private final ReportRenderer renderer;
  private final StructureInventoryReportSupport support;
  private final RepairsSummaryNames names;
  private final RepairsSummaryCsv csv;
  private final RoadNameService roadNames;

  public RepairsSummaryService(
      EntityManager entityManager,
      StructureInspectionRepository inspections,
      ReportRenderer renderer,
      StructureInventoryReportSupport support,
      RepairsSummaryNames names,
      RepairsSummaryCsv csv,
      RoadNameService roadNames) {
    this.entityManager = entityManager;
    this.inspections = inspections;
    this.renderer = renderer;
    this.support = support;
    this.names = names;
    this.csv = csv;
    this.roadNames = roadNames;
  }

  /** The report, as a PDF or a CSV. */
  public ReportFile generate(RepairsSummaryCriteria criteria, ReportFormat format) {
    if (format == ReportFormat.CSV) {
      return ReportTarget.inMemory(target -> writeCsv(criteria, target));
    }
    long started = System.nanoTime();
    Repairs repairs = repairs(criteria);
    String printed = support.today();
    Map<String, Object> parameters = support.parameters(REPORT_ID, printed);
    parameters.put("P_SELECTED_CRITERIA", names.selectedCriteria(criteria));
    byte[] content = renderer.pdf(TEMPLATE, parameters,
        pdfRows(repairs).stream().<Map<String, ?>>map(row -> row).toList());
    log.info("Repairs Summary: {} repairs as {} ({} bytes) in {} ms",
        repairs.lines().size(), format, content.length, millisSince(started));
    return new ReportFile(content, filename(FILENAME, printed, format), format.mediaType());
  }

  /** The PDF's rows, a repair each, in order, every value as the template prints it. */
  List<Map<String, String>> rows(RepairsSummaryCriteria criteria) {
    return pdfRows(repairs(criteria));
  }

  /**
   * The CSV, written to {@code target} as it is made rather than assembled in memory first — the
   * download's response, or {@link ReportTarget#inMemory}. The target is opened once the rows are
   * ready.
   */
  public void writeCsv(RepairsSummaryCriteria criteria, ReportTarget target) {
    long started = System.nanoTime();
    List<List<String>> csvRows = csvRows(criteria);
    try (CsvStream csv = StructureInventoryReportSupport.csv(target,
        filename(FILENAME, support.today(), ReportFormat.CSV), RepairsSummaryCsv.COLUMNS)) {
      csv.rows(csvRows);
    }
    log.info("Repairs Summary: {} rows as CSV, streamed, in {} ms",
        csvRows.size(), millisSince(started));
  }

  /** The CSV's rows, in order, in {@link RepairsSummaryCsv#COLUMNS} order. */
  List<List<String>> csvRows(RepairsSummaryCriteria criteria) {
    Repairs repairs = repairs(criteria);
    return csv.rows(repairs.lines(), repairs.lastRoutine(), repairs.names(), repairs.roads());
  }

  /**
   * The repairs for these criteria, with what both formats read beside them — the road names
   * LRMOPS's, whose sections for the Forest Service Road picked also filter the query.
   */
  private Repairs repairs(RepairsSummaryCriteria criteria) {
    List<RoadSectionKey> roadSections =
        roadNames.sectionsNamed(criteria.forestServiceRoad()).orElse(null);
    List<RepairsSummaryLine> lines = entityManager
        .createQuery(RepairsSummaryReportSpecifications.lines(
            entityManager.getCriteriaBuilder(), criteria, roadSections))
        .setHint(HibernateHints.HINT_FETCH_SIZE, ReportQueryHints.FETCH_ROWS).getResultList();
    List<Long> structureIds = lines.stream().map(RepairsSummaryLine::structureId).distinct().toList();
    return new Repairs(lines,
        firstOf(structureIds, inspections::findLastRoutineInspectionDatesAnyStatus),
        names.names(),
        roadNames.namesFor(lines, RepairsSummaryLine::forestFileId,
            RepairsSummaryLine::roadSectionId));
  }

  private static List<Map<String, String>> pdfRows(Repairs repairs) {
    return repairs.lines().stream()
        .map(line -> row(line, repairs.lastRoutine().get(line.structureId()), repairs.names(),
            repairs.roads().apply(line)))
        .toList();
  }

  /**
   * The repairs listed, each structure's last routine inspection, the code tables' names, and each
   * repair's road name — LRMOPS's.
   */
  private record Repairs(
      List<RepairsSummaryLine> lines, Map<Long, LocalDate> lastRoutine, Names names,
      Function<RepairsSummaryLine, String> roads) {}

  private static Map<String, String> row(
      RepairsSummaryLine line, LocalDate lastRoutine, Names codes, String roadName) {
    Map<String, String> row = new HashMap<>();
    row.put("CROSSING_STRUCTURE_ID", line.structureId().toString());
    row.put("CROSSING_STRUCTURE_NAME", line.structureName());
    row.put("CROSSING_SITE_ID", line.siteId());
    row.put("FOREST_FILE_ID", line.forestFileId());
    row.put("ROAD_SECTION_ID", line.roadSectionId());
    row.put("CROSSING_NAME", line.crossingName());
    row.put("DISTRICT", line.districtName());
    row.put("FOREST_SERVICE_ROAD", roadName);
    row.put("USER_KM", plain(line.userKm()));
    row.put("STRUCTURE_LOAD_RATING", plain(line.currentLoadRating()));
    row.put("LONGITUDE", degreesMinutesSeconds(line.longitude()));
    row.put("LATITUDE", degreesMinutesSeconds(line.latitude()));
    row.put("LAST_INSPECTION_DATE",
        lastRoutine == null ? NO_ROUTINE_INSPECTION : lastRoutine.format(DATE));
    if (line.inspectionId() == null) {
      row.put("INSPECTION_DATE", NO_INSPECTION);
    } else {
      row.put("INSPECTION_ID", line.inspectionId().toString());
      row.put("INSPECTION_DATE",
          line.inspectionDate() == null ? null : line.inspectionDate().format(DATE));
      row.put("INSPECTION_COMMENT", textOrNull(line.inspectionComment()));
      row.put("PENG_COMMENT", textOrNull(line.pengComment()));
    }
    row.put("REPAIR_NUMBER", plain(line.repairNumber()));
    row.put("REPAIR_STATUS", codes.statuses().get(line.repairStatusCode()));
    row.put("REPAIR_PRIORITY", codes.priorities().get(line.repairPriorityCode()));
    row.put("ESTIMATE", line.estimate() == null
        ? null
        : format(BigDecimal.valueOf(line.estimate()), "#,##0"));
    row.put("REPAIR_TYPE", codes.types().get(line.repairTypeCode()));
    row.put("REPAIR_QTY", plain(line.repairQuantity()));
    row.put("REPAIR_UNITS", codes.units().get(line.repairTypeCode()));
    row.put("REPAIR_DESCRIPTION", line.description());
    return row;
  }

  /**
   * Decimal degrees as degrees, minutes and seconds — {@code -122° 45' 55.4"} — each truncated,
   * the seconds to a tenth. Legacy rounded the degrees and minutes instead, so 53.92° printed as 54.
   * Worked in whole tenths of a second, so a value just short of a minute carries rather than
   * printing 60.0 seconds.
   */
  static String degreesMinutesSeconds(BigDecimal degrees) {
    if (degrees == null) {
      return null;
    }
    long tenths = degrees.abs().multiply(TENTHS_PER_DEGREE)
        .setScale(0, RoundingMode.HALF_UP).longValueExact();
    long whole = tenths / 36_000;
    long minutes = tenths % 36_000 / 600;
    long seconds = tenths % 600;
    String sign = degrees.signum() < 0 && tenths > 0 ? "-" : "";
    return String.format(Locale.ROOT, "%s%d° %02d' %02d.%d\"",
        sign, whole, minutes, seconds / 10, seconds % 10);
  }

  private static String textOrNull(String value) {
    return StringUtils.hasText(value) ? value : null;
  }
}
