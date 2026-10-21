package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.blankIfNull;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.filename;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.format;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.formatDate;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;

import ca.bc.gov.nrs.cbr.model.v1.MonitoringStatusCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.RecreationProjectEntity;
import ca.bc.gov.nrs.cbr.repository.v1.MonitoringStatusCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.RecreationProjectRepository;
import ca.bc.gov.nrs.cbr.specification.v1.MonitorsSummaryLine;
import ca.bc.gov.nrs.cbr.specification.v1.MonitorsSummaryReportSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import jakarta.persistence.EntityManager;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The Monitors Summary report (legacy CBR011): the monitoring items recorded against structures,
 * a block per structure, grouped by the inspection that found them — as a PDF in legacy's layout,
 * or as a CSV of what the PDF prints, a row per item.
 *
 * <p>Which items, and in what order, is one query ({@link MonitorsSummaryReportSpecifications});
 * this class reads beside it the statuses' descriptions, the maintainers' names and, for a
 * recreation site, its project's name, which legacy shows in place of the road's.
 */
@Service
@Transactional(readOnly = true)
public class MonitorsSummaryService {

  private static final Logger log = LoggerFactory.getLogger(MonitorsSummaryService.class);

  /** The template under {@code classpath:reports/}. */
  static final String TEMPLATE = "MONITORS_SUMMARY";

  /** The legacy report number the header prints. */
  static final String REPORT_ID = "CBR011";

  /** The file's name before the date. */
  static final String FILENAME = "MonitorsSummaryReport";

  /**
   * The CSV's columns: the fields the PDF prints, in its order, a monitoring item a row. Legacy had
   * no CSV — its Excel was the PDF's layout exported — so these are the PDF's own.
   */
  static final List<String> CSV_COLUMNS = List.of(
      "Structure #", "Site #", "Primary User Name", "KM", "Crossing Name", "Project File ID #",
      "Br.", "Forest Service Road/Project Name", "Inspection Date", "Inspection Comment",
      "Monitor #", "Monitor Status", "Description");

  /** How the CSV writes a date, as the Repairs Summary's does. */
  private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** The site type whose road is a recreation project's name. */
  private static final String RECREATION = "REC";

  private final EntityManager entityManager;
  private final ReportRenderer renderer;
  private final StructureInventoryReportSupport support;
  private final StructureInventoryNames clients;
  private final MonitoringStatusCodeRepository statuses;
  private final RecreationProjectRepository projects;
  private final MonitorsSummaryCriteriaLine criteriaLine;

  public MonitorsSummaryService(
      EntityManager entityManager,
      ReportRenderer renderer,
      StructureInventoryReportSupport support,
      StructureInventoryNames clients,
      MonitoringStatusCodeRepository statuses,
      RecreationProjectRepository projects,
      MonitorsSummaryCriteriaLine criteriaLine) {
    this.entityManager = entityManager;
    this.renderer = renderer;
    this.support = support;
    this.clients = clients;
    this.statuses = statuses;
    this.projects = projects;
    this.criteriaLine = criteriaLine;
  }

  /** The report, as a PDF or a CSV. */
  public ReportFile generate(MonitorsSummaryCriteria criteria, ReportFormat format) {
    long started = System.nanoTime();
    List<Item> items = items(criteria);
    String printed = support.today();
    byte[] content = switch (format) {
      case PDF -> {
        Map<String, Object> parameters = support.parameters(REPORT_ID, printed);
        parameters.put("P_SELECTED_CRITERIA", criteriaLine.of(criteria));
        yield renderer.pdf(TEMPLATE, parameters,
            items.stream().<Map<String, ?>>map(MonitorsSummaryService::pdfRow).toList());
      }
      case CSV -> StructureInventoryReportSupport.csv(CSV_COLUMNS,
          items.stream().map(MonitorsSummaryService::csvRow).toList());
    };
    log.info("Monitors Summary: {} items as {} ({} bytes) in {} ms",
        items.size(), format, content.length, millisSince(started));
    return new ReportFile(content, filename(FILENAME, printed, format), format.mediaType());
  }

  /** The PDF's rows, an item each, in order, every value as the template prints it. */
  List<Map<String, String>> rows(MonitorsSummaryCriteria criteria) {
    return items(criteria).stream().map(MonitorsSummaryService::pdfRow).toList();
  }

  /** The CSV's rows, in order, in {@link #CSV_COLUMNS} order. */
  List<List<String>> csvRows(MonitorsSummaryCriteria criteria) {
    return items(criteria).stream().map(MonitorsSummaryService::csvRow).toList();
  }

  /** The items for these criteria, each with the names read beside the query. */
  private List<Item> items(MonitorsSummaryCriteria criteria) {
    List<MonitorsSummaryLine> lines = entityManager
        .createQuery(MonitorsSummaryReportSpecifications.lines(
            entityManager.getCriteriaBuilder(), criteria))
        .getResultList();
    Map<String, String> statusNames = new HashMap<>();
    for (MonitoringStatusCodeEntity status : statuses.findAll()) {
      statusNames.put(status.getMonitoringStatusCode(), status.getDescription());
    }
    Map<String, String> clientNames = clients.clientNames(
        lines.stream().map(MonitorsSummaryLine::clientNumber).toList());
    Map<String, String> projectNames = projectNames(lines);
    return lines.stream().map(line -> new Item(line,
        clientNames.get(line.clientNumber()),
        RECREATION.equals(line.siteTypeCode())
            ? projectNames.get(line.forestFileId())
            : line.roadSectionName(),
        statusNames.get(line.monitoringStatusCode()))).toList();
  }

  private static Map<String, String> pdfRow(Item item) {
    MonitorsSummaryLine line = item.line();
    Map<String, String> row = new HashMap<>();
    row.put("CROSSING_STRUCTURE_ID", line.structureId().toString());
    row.put("CROSSING_STRUCTURE_NAME", line.structureName());
    row.put("CROSSING_SITE_ID", line.siteId());
    row.put("CROSSING_SITE_TYPE_CODE", line.siteTypeCode());
    row.put("FOREST_FILE_ID", line.forestFileId());
    row.put("ROAD_SECTION_ID", line.roadSectionId());
    row.put("CLIENT_NAME", item.clientName());
    row.put("CROSSING_NAME", line.crossingName());
    row.put("FOREST_SERVICE_ROAD", item.road());
    row.put("POINT_OF_COMMENCEMENT_DISTANCE", line.kilometres() == null
        ? null
        : format(line.kilometres(), "#,##0.00"));
    if (line.inspectionId() != null) {
      row.put("INSPECTION_ID", line.inspectionId().toString());
      row.put("INSPECTION_DATE", formatDate(line.inspectionDate()));
      row.put("INSPECTION_COMMENT", StringUtils.hasText(line.inspectionComment())
          ? line.inspectionComment()
          : null);
    }
    row.put("MONITOR_NUMBER", plain(line.monitorNumber()));
    row.put("MONITORING_STATUS_DESC", item.status());
    row.put("MONITORING_DESCRIPTION", line.description());
    return row;
  }

  private static List<String> csvRow(Item item) {
    MonitorsSummaryLine line = item.line();
    return List.of(
        blankIfNull(line.structureName()),
        blankIfNull(line.siteId()),
        blankIfNull(item.clientName()),
        plain(line.kilometres()),
        blankIfNull(line.crossingName()),
        blankIfNull(line.forestFileId()),
        blankIfNull(line.roadSectionId()),
        blankIfNull(item.road()),
        line.inspectionDate() == null ? "" : line.inspectionDate().format(CSV_DATE),
        blankIfNull(line.inspectionComment()),
        plain(line.monitorNumber()),
        blankIfNull(item.status()),
        blankIfNull(line.description()));
  }

  /** An item as selected, with its maintainer's name, its road's (or project's) and its status's. */
  private record Item(MonitorsSummaryLine line, String clientName, String road, String status) {}

  /** The project names of the recreation sites listed, by their file — legacy's {@code P.PROJECT_NAME}. */
  private Map<String, String> projectNames(List<MonitorsSummaryLine> lines) {
    List<String> files = lines.stream()
        .filter(line -> RECREATION.equals(line.siteTypeCode()))
        .map(MonitorsSummaryLine::forestFileId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    Map<String, String> names = new HashMap<>();
    for (int from = 0; from < files.size(); from += StructureInventoryReportSupport.BATCH) {
      for (RecreationProjectEntity project : projects.findAllById(
          files.subList(from, Math.min(files.size(), from + StructureInventoryReportSupport.BATCH)))) {
        names.put(project.getForestFileId(), project.getProjectName());
      }
    }
    return names;
  }
}
