package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.filename;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;

import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Inspections Due – Summary (legacy CBR004): a line per structure — when it was last inspected and
 * is next due, what it is and where — under its forest district, as a PDF in legacy's landscape
 * layout, or a CSV of its columns. Its rows are {@link InspectionsDueRows}'.
 */
@Service
public class InspectionsDueSummaryService {

  private static final Logger log = LoggerFactory.getLogger(InspectionsDueSummaryService.class);

  /** The template under {@code classpath:reports/}. */
  static final String TEMPLATE = "INSPECTIONS_DUE_SUMMARY";

  /** The legacy report number the header prints. */
  static final String REPORT_ID = "CBR004";

  /** The file's name before the date. */
  static final String FILENAME = "InspectionsDueSummaryReport";

  private final InspectionsDueRows rows;
  private final ReportRenderer renderer;
  private final StructureInventoryReportSupport support;
  private final InspectionsDueCriteriaLine criteriaLine;

  public InspectionsDueSummaryService(
      InspectionsDueRows rows,
      ReportRenderer renderer,
      StructureInventoryReportSupport support,
      InspectionsDueCriteriaLine criteriaLine) {
    this.rows = rows;
    this.renderer = renderer;
    this.support = support;
    this.criteriaLine = criteriaLine;
  }

  /** The report, as a PDF or a CSV. */
  public ReportFile generate(InspectionsDueCriteria criteria, ReportFormat format) {
    long started = System.nanoTime();
    String printed = support.today();
    int count;
    byte[] content;
    if (format == ReportFormat.CSV) {
      List<List<String>> csvRows = rows.summaryCsv(criteria);
      count = csvRows.size();
      content = StructureInventoryReportSupport.csv(InspectionsDueRows.SUMMARY_COLUMNS, csvRows);
    } else {
      List<Map<String, String>> pdfRows = rows.summary(criteria);
      count = pdfRows.size();
      Map<String, Object> parameters = support.parameters(REPORT_ID, printed);
      parameters.put("P_SELECTED_CRITERIA", criteriaLine.of(criteria, criteria.sortOrDefault()));
      content = renderer.pdf(TEMPLATE, parameters,
          pdfRows.stream().<Map<String, ?>>map(row -> row).toList());
    }
    log.info("Inspections Due - Summary: {} rows as {} ({} bytes) in {} ms",
        count, format, content.length, millisSince(started));
    return new ReportFile(content, filename(FILENAME, printed, format), format.mediaType());
  }
}
