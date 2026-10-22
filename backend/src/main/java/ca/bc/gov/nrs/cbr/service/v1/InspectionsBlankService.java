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
  public ReportFile generate(InspectionsDueCriteria criteria, ReportFormat format) {
    long started = System.nanoTime();
    String printed = support.today();
    int count;
    byte[] content;
    if (format == ReportFormat.CSV) {
      List<List<String>> csvRows = rows.csvRows(criteria);
      count = csvRows.size();
      content = StructureInventoryReportSupport.csv(InspectionsBlankRows.CSV_COLUMNS, csvRows);
    } else {
      List<Map<String, String>> pdfRows = rows.rows(criteria);
      count = pdfRows.size();
      Map<String, Object> parameters = support.parameters(REPORT_ID, printed);
      parameters.put("P_SELECTED_CRITERIA", criteriaLine.of(criteria, null));
      content = renderer.pdf(TEMPLATE, parameters,
          pdfRows.stream().<Map<String, ?>>map(row -> row).toList());
    }
    log.info("Inspections - Blank Report: {} rows as {} ({} bytes) in {} ms",
        count, format, content.length, millisSince(started));
    return new ReportFile(content, filename(FILENAME, printed, format), format.mediaType());
  }
}
