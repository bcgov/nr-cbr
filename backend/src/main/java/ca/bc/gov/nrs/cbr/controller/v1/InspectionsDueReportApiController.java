package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.InspectionsDueReportApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsBlankService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsDueDetailedService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsDueSummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inspections-due reports. Mappings and authorization are declared on
 * {@link InspectionsDueReportApiEndpoint}; each file goes out as {@link ReportApiController}'s do.
 */
@RestController
public class InspectionsDueReportApiController implements InspectionsDueReportApiEndpoint {

  private final InspectionsDueSummaryService summary;
  private final InspectionsDueDetailedService detailed;
  private final InspectionsBlankService blank;

  public InspectionsDueReportApiController(
      InspectionsDueSummaryService summary,
      InspectionsDueDetailedService detailed,
      InspectionsBlankService blank) {
    this.summary = summary;
    this.detailed = detailed;
    this.blank = blank;
  }

  @Override
  public ResponseEntity<byte[]> inspectionsDueSummary(
      InspectionsDueCriteria criteria, ReportFormat format, HttpServletResponse response) {
    if (format == ReportFormat.CSV) {
      return ReportApiController.streamed(response, target -> summary.writeCsv(criteria, target));
    }
    return ReportApiController.download(summary.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsDueDetailed(
      InspectionsDueCriteria criteria, ReportFormat format, HttpServletResponse response) {
    if (format == ReportFormat.CSV) {
      return ReportApiController.streamed(response, target -> detailed.writeCsv(criteria, target));
    }
    return ReportApiController.download(detailed.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsBlank(
      InspectionsDueCriteria criteria, ReportFormat format, HttpServletResponse response) {
    // Both formats streamed: a province's forms run to 81 MB as a PDF.
    return format == ReportFormat.CSV
        ? ReportApiController.streamed(response, target -> blank.writeCsv(criteria, target))
        : ReportApiController.streamed(response, target -> blank.writePdf(criteria, target));
  }
}
