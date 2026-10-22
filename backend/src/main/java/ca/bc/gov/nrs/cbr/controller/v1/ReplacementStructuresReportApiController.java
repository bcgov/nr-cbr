package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.ReplacementStructuresReportApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.ReplacementStructuresSummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementStructuresCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Replacement Structures Summary. Mapping and authorization are declared on
 * {@link ReplacementStructuresReportApiEndpoint}; the file goes out as
 * {@link ReportApiController}'s do.
 */
@RestController
public class ReplacementStructuresReportApiController
    implements ReplacementStructuresReportApiEndpoint {

  private final ReplacementStructuresSummaryService summary;

  public ReplacementStructuresReportApiController(ReplacementStructuresSummaryService summary) {
    this.summary = summary;
  }

  @Override
  public ResponseEntity<byte[]> replacementStructuresSummary(
      ReplacementStructuresCriteria criteria, ReportFormat format) {
    return ReportApiController.download(summary.generate(criteria, format));
  }
}
