package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.filename;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;

import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import net.sf.jasperreports.engine.JasperPrint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inspections – Blank Report (legacy CBR010): a blank routine inspection form per structure, its
 * pages numbered by structure, as a PDF; or a CSV of the structures the forms are for. Its lines
 * are {@link InspectionsBlankRows}'; the criteria line is printed, where legacy's template left out
 * the criteria it was passed.
 */
@Service
public class InspectionsBlankService {

  private static final Logger log = LoggerFactory.getLogger(InspectionsBlankService.class);

  /** The template under {@code classpath:reports/}. */
  static final String TEMPLATE = "INSPECTIONS_BLANK";

  /** The legacy report number the header prints. */
  static final String REPORT_ID = "CBR010";

  /** The file's name before the date. */
  static final String FILENAME = "InspectionsBlankReport";

  private final InspectionsBlankRows rows;
  private final ReportRenderer renderer;
  private final StructureInventoryReportSupport support;
  private final InspectionsDueCriteriaLine criteriaLine;

  public InspectionsBlankService(
      InspectionsBlankRows rows,
      ReportRenderer renderer,
      StructureInventoryReportSupport support,
      InspectionsDueCriteriaLine criteriaLine) {
    this.rows = rows;
    this.renderer = renderer;
    this.support = support;
    this.criteriaLine = criteriaLine;
  }

  /** The report, as a PDF or a CSV. */
  @Transactional(readOnly = true)
  public ReportFile generate(InspectionsDueCriteria criteria, ReportFormat format) {
    return format == ReportFormat.CSV
        ? ReportTarget.inMemory(target -> csv(criteria, target))
        : ReportTarget.inMemory(target -> pdf(criteria, target));
  }

  /**
   * The PDF, written to {@code target} as it is exported. The structures are handed to Jasper a
   * batch at a time as it fills ({@link InspectionsBlankRows#rowSource}), so a province's forms are
   * never all loaded at once; the filled pages are, until exported. The target is opened once the
   * report is filled, so a failure while filling is still an ordinary error.
   *
   * <p>One transaction for the whole fill: the forms are loaded as Jasper reads them.
   */
  @Transactional(readOnly = true)
  public void writePdf(InspectionsDueCriteria criteria, ReportTarget target) {
    pdf(criteria, target);
  }

  private void pdf(InspectionsDueCriteria criteria, ReportTarget target) {
    long started = System.nanoTime();
    String printed = support.today();
    Map<String, Object> parameters = support.parameters(REPORT_ID, printed);
    parameters.put("P_SELECTED_CRITERIA", criteriaLine.of(criteria, null));
    BatchedRowSource<Long> source = rows.rowSource(criteria);
    JasperPrint print = renderer.fill(TEMPLATE, parameters, source);
    long filled = System.nanoTime();
    try (OutputStream out = target.open(
        filename(FILENAME, printed, ReportFormat.PDF), ReportFormat.PDF.mediaType())) {
      renderer.export(print, out);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    log.info("Inspections - Blank Report: {} lines as PDF ({} pages), streamed — "
            + "fill {} ms, export {} ms",
        source.count(), print.getPages().size(), (filled - started) / 1_000_000,
        millisSince(filled));
  }

  /**
   * The CSV, written to {@code target} as it is made rather than assembled in memory first — the
   * download's response, or {@link ReportTarget#inMemory}. The target is opened once the rows are
   * ready.
   */
  public void writeCsv(InspectionsDueCriteria criteria, ReportTarget target) {
    csv(criteria, target);
  }

  private void csv(InspectionsDueCriteria criteria, ReportTarget target) {
    long started = System.nanoTime();
    int count;
    try (CsvStream csv = StructureInventoryReportSupport.csv(target,
        filename(FILENAME, support.today(), ReportFormat.CSV), InspectionsBlankRows.CSV_COLUMNS)) {
      // A batch at a time, written as made: the rows of a province never all in memory at once.
      rows.writeCsv(criteria, csv);
      count = csv.count();
    }
    log.info("Inspections - Blank Report: {} rows as CSV, streamed, in {} ms", count, millisSince(started));
  }
}
