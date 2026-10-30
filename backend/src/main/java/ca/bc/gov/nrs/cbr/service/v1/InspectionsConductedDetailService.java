package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.filename;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;

import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedSort;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Inspections Conducted – Detail (legacy CBR001): each inspection in full — its structure and
 * site, its items, channel and hazards, repairs, monitoring items, comments, load rating and
 * status — a page of its own, as a PDF in legacy's layout; or as a CSV, a row per inspection item
 * ({@link InspectionDetailCsv}). Its rows are {@link InspectionDetailRows}'.
 */
@Service
public class InspectionsConductedDetailService {

  private static final Logger log = LoggerFactory.getLogger(InspectionsConductedDetailService.class);

  /** The template under {@code classpath:reports/}, and its three subreports. */
  static final String TEMPLATE = "INSPECTIONS_CONDUCTED_DETAIL";
  static final String ITEMS = "INSPECTIONS_CONDUCTED_DETAIL_ITEMS";
  static final String REPAIRS = "INSPECTIONS_CONDUCTED_DETAIL_REPAIRS";
  static final String MONITORS = "INSPECTIONS_CONDUCTED_DETAIL_MONITORS";

  /** The file's name before the date. */
  static final String FILENAME = "InspectionsConductedDetailReport";

  /** Legacy's default order: by project, branch and user km — within Structure #. */
  static final InspectionsConductedSort DEFAULT_SORT =
      InspectionsConductedSort.PROJECT_BRANCH_USER_KM;

  private final InspectionDetailRows rows;
  private final ReportRenderer renderer;
  private final StructureInventoryReportSupport support;
  private final InspectionsConductedCriteriaLine criteriaLine;

  public InspectionsConductedDetailService(
      InspectionDetailRows rows,
      ReportRenderer renderer,
      StructureInventoryReportSupport support,
      InspectionsConductedCriteriaLine criteriaLine) {
    this.rows = rows;
    this.renderer = renderer;
    this.support = support;
    this.criteriaLine = criteriaLine;
  }

  /** The structures the report lists, an inspection's each, in its order — for its KMZ. */
  List<Long> structureIds(InspectionsConductedCriteria criteria) {
    return rows.structureIds(criteria, criteria.sortOr(DEFAULT_SORT));
  }

  /** The report, as a PDF or a CSV. */
  public ReportFile generate(InspectionsConductedCriteria criteria, ReportFormat format) {
    long started = System.nanoTime();
    InspectionsConductedSort sort = criteria.sortOr(DEFAULT_SORT);
    String printed = support.today();
    int count;
    byte[] content;
    if (format == ReportFormat.CSV) {
      List<List<String>> csvRows = rows.csvRows(criteria, sort);
      count = csvRows.size();
      content = StructureInventoryReportSupport.csv(InspectionDetailCsv.COLUMNS, csvRows);
    } else {
      List<Map<String, Object>> detail = rows.rows(criteria, sort);
      count = detail.size();
      Map<String, Object> parameters = support.parameters("CBR001", printed);
      parameters.put("P_SELECTED_CRITERIA", criteriaLine.of(criteria, sort));
      parameters.put("P_ITEMS_REPORT", renderer.compiled(ITEMS));
      parameters.put("P_REPAIRS_REPORT", renderer.compiled(REPAIRS));
      parameters.put("P_MONITORS_REPORT", renderer.compiled(MONITORS));
      content = renderer.pdf(TEMPLATE, parameters,
          detail.stream().<Map<String, ?>>map(row -> row).toList());
    }
    log.info("Inspections Conducted - Detail: {} rows as {} ({} bytes) in {} ms",
        count, format, content.length, millisSince(started));
    return new ReportFile(content, filename(FILENAME, printed, format), format.mediaType());
  }

  /**
   * One inspection's report as its audit record ({@link InspectionAuditRecorder}): the same PDF,
   * its criteria line saying which copy it is, and Date Printed when it was saved, to the minute.
   *
   * @param printed   e.g. {@code 2026/10/26 14:05}
   * @param auditLine e.g. {@code Audit record - Submitted - saved by IDIR\JSMITH on 2026-10-26 14:05}
   */
  public byte[] auditPdf(long inspectionId, String printed, String auditLine) {
    Map<String, Object> parameters = support.parameters("CBR001", printed);
    parameters.put("P_SELECTED_CRITERIA", auditLine);
    parameters.put("P_ITEMS_REPORT", renderer.compiled(ITEMS));
    parameters.put("P_REPAIRS_REPORT", renderer.compiled(REPAIRS));
    parameters.put("P_MONITORS_REPORT", renderer.compiled(MONITORS));
    return renderer.pdf(TEMPLATE, parameters,
        rows.rows(inspectionId).stream().<Map<String, ?>>map(row -> row).toList());
  }
}
