package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.filename;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;

import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedSort;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JasperPrint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  /** The report, as a PDF or a CSV, in memory — for a caller that needs the whole file. */
  @Transactional(readOnly = true)
  public ReportFile generate(InspectionsConductedCriteria criteria, ReportFormat format) {
    return format == ReportFormat.CSV
        ? ReportTarget.inMemory(target -> csv(criteria, target))
        : ReportTarget.inMemory(target -> pdf(criteria, target));
  }

  /**
   * The PDF, written to {@code target} as it is exported. The inspections are handed to Jasper a
   * batch at a time as it fills ({@link InspectionDetailRows#rowSource}), so a province's are
   * never all loaded at once; the filled pages are, until exported. The target is opened once the
   * report is filled, so a failure while filling is still an ordinary error.
   *
   * <p>One transaction for the whole fill: the rows are loaded as Jasper reads them.
   */
  @Transactional(readOnly = true)
  public void writePdf(InspectionsConductedCriteria criteria, ReportTarget target) {
    pdf(criteria, target);
  }

  private void pdf(InspectionsConductedCriteria criteria, ReportTarget target) {
    long started = System.nanoTime();
    InspectionsConductedSort sort = criteria.sortOr(DEFAULT_SORT);
    String printed = support.today();
    Map<String, Object> parameters = support.parameters("CBR001", printed);
    parameters.put("P_SELECTED_CRITERIA", criteriaLine.of(criteria, sort));
    parameters.put("P_ITEMS_REPORT", renderer.compiled(ITEMS));
    parameters.put("P_REPAIRS_REPORT", renderer.compiled(REPAIRS));
    parameters.put("P_MONITORS_REPORT", renderer.compiled(MONITORS));
    BatchedRowSource<Long> source = rows.rowSource(criteria, sort);
    JasperPrint print = renderer.fill(TEMPLATE, parameters, source);
    long filled = System.nanoTime();
    try (OutputStream out = target.open(
        filename(FILENAME, printed, ReportFormat.PDF), ReportFormat.PDF.mediaType())) {
      renderer.export(print, out);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    log.info("Inspections Conducted - Detail: {} inspections as PDF ({} pages), streamed — "
            + "fill {} ms, export {} ms",
        source.count(), print.getPages().size(), (filled - started) / 1_000_000,
        millisSince(filled));
  }

  /**
   * The CSV, written to {@code target} as it is made rather than assembled in memory first — the
   * download's response, or {@link ReportTarget#inMemory}. The target is opened once the rows are
   * ready.
   */
  public void writeCsv(InspectionsConductedCriteria criteria, ReportTarget target) {
    csv(criteria, target);
  }

  private void csv(InspectionsConductedCriteria criteria, ReportTarget target) {
    long started = System.nanoTime();
    int count;
    try (CsvStream csv = StructureInventoryReportSupport.csv(target,
        filename(FILENAME, support.today(), ReportFormat.CSV), InspectionDetailCsv.COLUMNS)) {
      // A batch at a time, written as made: the rows of a province never all in memory at once.
      rows.writeCsv(criteria, criteria.sortOr(DEFAULT_SORT), csv);
      count = csv.count();
    }
    log.info("Inspections Conducted - Detail: {} rows as CSV, streamed, in {} ms",
        count, millisSince(started));
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
