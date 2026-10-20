package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.ReportApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.ReportFile;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailReportService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventorySummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Reports. Mappings and authorization are declared on {@link ReportApiEndpoint}. */
@RestController
public class ReportApiController implements ReportApiEndpoint {

  private final StructureInventorySummaryService structureInventorySummary;
  private final StructureInventoryDetailedService structureInventoryDetailed;
  private final StructureDetailReportService structureDetail;

  public ReportApiController(
      StructureInventorySummaryService structureInventorySummary,
      StructureInventoryDetailedService structureInventoryDetailed,
      StructureDetailReportService structureDetail) {
    this.structureInventorySummary = structureInventorySummary;
    this.structureInventoryDetailed = structureInventoryDetailed;
    this.structureDetail = structureDetail;
  }

  @Override
  public ResponseEntity<byte[]> structureInventorySummary(
      StructureInventorySummaryCriteria criteria, ReportFormat format) {
    return download(structureInventorySummary.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> structureInventoryDetailed(
      StructureInventoryDetailedCriteria criteria, ReportFormat format) {
    return download(structureInventoryDetailed.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> structureDetail(long structureId, ReportFormat format) {
    return download(structureDetail.generate(structureId, format));
  }

  /** Always an attachment: a report is saved, and the page asks for it to be. */
  private static ResponseEntity<byte[]> download(ReportFile file) {
    return ResponseEntity.ok()
        .contentType(file.mediaType())
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(file.content());
  }
}
