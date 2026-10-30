package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.blankIfNull;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.filename;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.format;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.formatDate;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;

import ca.bc.gov.nrs.cbr.specification.v1.InspectionsConductedLine;
import ca.bc.gov.nrs.cbr.specification.v1.InspectionsConductedReportSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedSort;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inspections Conducted – Summary (legacy CBR002): a line per inspection — its structure, site,
 * type, date and current report status — as a PDF in legacy's landscape layout, or a CSV.
 *
 * <p>Which inspections, and in what order, is one query
 * ({@link InspectionsConductedReportSpecifications}); this class names the codes and formats every
 * value as the template prints it. Legacy had no CSV — its Excel was the PDF's layout — so the CSV
 * is the PDF's nine columns, in its order, its values written as the other CSVs write them: plain
 * numbers and {@code yyyy-MM-dd} dates.
 */
@Service
@Transactional(readOnly = true)
public class InspectionsConductedSummaryService {

  private static final Logger log =
      LoggerFactory.getLogger(InspectionsConductedSummaryService.class);

  /** The template under {@code classpath:reports/}. */
  static final String TEMPLATE = "INSPECTIONS_CONDUCTED_SUMMARY";

  /** The legacy report number the header prints. */
  static final String REPORT_ID = "CBR002";

  /** The file's name before the date. */
  static final String FILENAME = "InspectionsConductedSummaryReport";

  /** Legacy's default order: by Forest Service Road. */
  static final InspectionsConductedSort DEFAULT_SORT = InspectionsConductedSort.FOREST_SERVICE_ROAD;

  /** The CSV's columns: the PDF's, left to right. */
  static final List<String> CSV_COLUMNS = List.of(
      "Site #", "Structure #", "Inspection Type", "Inspection Date", "Inspection Status",
      "Road Name", "KM", "User KM", "Crossing Name");

  /** How the CSV writes a date, as the other CSVs do. */
  private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** Legacy's pattern for a kilometre: {@code ###0.00}. */
  private static final String KILOMETRES = "###0.00";

  private final EntityManager entityManager;
  private final ReportRenderer renderer;
  private final StructureInventoryReportSupport support;
  private final ConfigurationService codes;
  private final InspectionsConductedCriteriaLine criteriaLine;

  public InspectionsConductedSummaryService(
      EntityManager entityManager,
      ReportRenderer renderer,
      StructureInventoryReportSupport support,
      ConfigurationService codes,
      InspectionsConductedCriteriaLine criteriaLine) {
    this.entityManager = entityManager;
    this.renderer = renderer;
    this.support = support;
    this.codes = codes;
    this.criteriaLine = criteriaLine;
  }

  /** The report, as a PDF or a CSV. */
  public ReportFile generate(InspectionsConductedCriteria criteria, ReportFormat format) {
    long started = System.nanoTime();
    List<Inspection> inspections = inspections(criteria);
    String printed = support.today();
    byte[] content = switch (format) {
      case PDF -> {
        Map<String, Object> parameters = support.parameters(REPORT_ID, printed);
        parameters.put("P_SELECTED_CRITERIA",
            criteriaLine.of(criteria, criteria.sortOr(DEFAULT_SORT)));
        yield renderer.pdf(TEMPLATE, parameters, inspections.stream()
            .<Map<String, ?>>map(InspectionsConductedSummaryService::pdfRow).toList());
      }
      case CSV -> StructureInventoryReportSupport.csv(CSV_COLUMNS,
          inspections.stream().map(InspectionsConductedSummaryService::csvRow).toList());
    };
    log.info("Inspections Conducted - Summary: {} inspections as {} ({} bytes) in {} ms",
        inspections.size(), format, content.length, millisSince(started));
    return new ReportFile(content, filename(FILENAME, printed, format), format.mediaType());
  }

  /** The PDF's rows, an inspection each, in order, every value as the template prints it. */
  List<Map<String, String>> rows(InspectionsConductedCriteria criteria) {
    return inspections(criteria).stream().map(InspectionsConductedSummaryService::pdfRow).toList();
  }

  /** The CSV's rows, in order, in {@link #CSV_COLUMNS} order. */
  List<List<String>> csvRows(InspectionsConductedCriteria criteria) {
    return inspections(criteria).stream().map(InspectionsConductedSummaryService::csvRow).toList();
  }

  /** The structures the report lists, an inspection's each, in its order — for its KMZ. */
  List<Long> structureIds(InspectionsConductedCriteria criteria) {
    return lines(criteria).stream().map(InspectionsConductedLine::structureId).toList();
  }

  /** The inspections for these criteria, in order, with their type and status named. */
  private List<Inspection> inspections(InspectionsConductedCriteria criteria) {
    List<InspectionsConductedLine> lines = lines(criteria);
    Map<String, String> types = byCode(codes.getInspectionTypeCodes());
    Map<String, String> statuses = byCode(codes.getInspectionReportStatusCodes());
    return lines.stream().map(line -> new Inspection(line,
        types.get(line.inspectionTypeCode()), statuses.get(line.reportStatusCode()))).toList();
  }

  private List<InspectionsConductedLine> lines(InspectionsConductedCriteria criteria) {
    return entityManager
        .createQuery(InspectionsConductedReportSpecifications.lines(
            entityManager.getCriteriaBuilder(), criteria, criteria.sortOr(DEFAULT_SORT)))
        .getResultList();
  }

  private static Map<String, String> pdfRow(Inspection inspection) {
    InspectionsConductedLine line = inspection.line();
    Map<String, String> row = new HashMap<>();
    row.put("CROSSING_SITE_ID", line.siteId());
    row.put("CROSSING_STRUCTURE_NAME", line.structureName());
    row.put("INSPECTION_TYPE", inspection.type());
    row.put("INSPECTION_DATE", formatDate(line.inspectionDate()));
    row.put("INSPECTION_STATUS", inspection.status());
    row.put("ROAD_SECT_NAME", line.roadSectionName());
    row.put("POINT_OF_COMMENCEMENT_DISTANCE", kilometres(line.kilometres()));
    row.put("USER_KM", kilometres(line.userKm()));
    row.put("CROSSING_NAME", line.crossingName());
    return row;
  }

  private static List<String> csvRow(Inspection inspection) {
    InspectionsConductedLine line = inspection.line();
    return List.of(
        blankIfNull(line.siteId()),
        blankIfNull(line.structureName()),
        blankIfNull(inspection.type()),
        date(line.inspectionDate()),
        blankIfNull(inspection.status()),
        blankIfNull(line.roadSectionName()),
        plain(line.kilometres()),
        plain(line.userKm()),
        blankIfNull(line.crossingName()));
  }

  private static String date(LocalDate value) {
    return value == null ? "" : value.format(CSV_DATE);
  }

  private static String kilometres(BigDecimal value) {
    return value == null ? null : format(value, KILOMETRES);
  }

  private static Map<String, String> byCode(List<CodeOptionResponse> options) {
    Map<String, String> names = new HashMap<>();
    options.forEach(option -> names.put(option.code(), option.description()));
    return names;
  }

  /** An inspection as selected, with its type's and its current status's names. */
  private record Inspection(InspectionsConductedLine line, String type, String status) {}
}
