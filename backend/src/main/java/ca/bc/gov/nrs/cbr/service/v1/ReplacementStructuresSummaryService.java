package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.filename;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.millisSince;

import ca.bc.gov.nrs.cbr.struct.v1.ReplacementStructuresCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Replacement Structures Summary (legacy CBR009): the structures due a load restriction, a
 * replacement or a closure, a page to a district and management area and a group to a year, as a
 * PDF in legacy's landscape layout, or a CSV of its rows. Its rows are
 * {@link ReplacementStructuresRows}'.
 */
@Service
public class ReplacementStructuresSummaryService {

  private static final Logger log =
      LoggerFactory.getLogger(ReplacementStructuresSummaryService.class);

  /** The template under {@code classpath:reports/}. */
  static final String TEMPLATE = "REPLACEMENT_STRUCTURES_SUMMARY";

  /** The legacy report number the header prints. */
  static final String REPORT_ID = "CBR009";

  /** The file's name before the date. */
  static final String FILENAME = "ReplacementStructuresSummaryReport";

  private final ReplacementStructuresRows rows;
  private final ReportRenderer renderer;
  private final StructureInventoryReportSupport support;
  private final ReplacementStructuresCriteriaLine criteriaLine;

  public ReplacementStructuresSummaryService(
      ReplacementStructuresRows rows,
      ReportRenderer renderer,
      StructureInventoryReportSupport support,
      ReplacementStructuresCriteriaLine criteriaLine) {
    this.rows = rows;
    this.renderer = renderer;
    this.support = support;
    this.criteriaLine = criteriaLine;
  }

  /** The report, as a PDF or a CSV. */
  public ReportFile generate(ReplacementStructuresCriteria criteria, ReportFormat format) {
    long started = System.nanoTime();
    String printed = support.today();
    int count;
    byte[] content;
    if (format == ReportFormat.CSV) {
      List<List<String>> csvRows = rows.csvRows(criteria);
      count = csvRows.size();
      content = StructureInventoryReportSupport.csv(ReplacementStructuresRows.CSV_COLUMNS, csvRows);
    } else {
      List<Map<String, String>> pdfRows = rows.rows(criteria);
      count = pdfRows.size();
      Map<String, Object> parameters = support.parameters(REPORT_ID, printed);
      parameters.put("P_SELECTED_CRITERIA", criteriaLine.of(criteria));
      content = renderer.pdf(TEMPLATE, parameters,
          pdfRows.stream().<Map<String, ?>>map(row -> row).toList());
    }
    log.info("Replacement Structures Summary: {} rows as {} ({} bytes) in {} ms",
        count, format, content.length, millisSince(started));
    return new ReportFile(content, filename(FILENAME, printed, format), format.mediaType());
  }
}
