package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.InspectionsDueReportApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsBlankService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsDueDetailedService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsDueSummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
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
      InspectionsDueCriteria criteria, ReportFormat format) {
    return ReportApiController.download(summary.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsDueDetailed(
      InspectionsDueCriteria criteria, ReportFormat format) {
    return ReportApiController.download(detailed.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsBlank(InspectionsDueCriteria criteria, ReportFormat format) {
    return ReportApiController.download(blank.generate(criteria, format));
  }
}
