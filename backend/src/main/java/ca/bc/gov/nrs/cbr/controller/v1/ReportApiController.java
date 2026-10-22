package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.ReportApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsConductedDetailService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsConductedSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.MonitorsSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.RepairsSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.ReportFile;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailReportService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventorySummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RepairsSummaryCriteria;
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
  private final RepairsSummaryService repairsSummary;
  private final MonitorsSummaryService monitorsSummary;
  private final InspectionsConductedSummaryService inspectionsConductedSummary;
  private final InspectionsConductedDetailService inspectionsConductedDetail;

  public ReportApiController(
      StructureInventorySummaryService structureInventorySummary,
      StructureInventoryDetailedService structureInventoryDetailed,
      StructureDetailReportService structureDetail,
      RepairsSummaryService repairsSummary,
      MonitorsSummaryService monitorsSummary,
      InspectionsConductedSummaryService inspectionsConductedSummary,
      InspectionsConductedDetailService inspectionsConductedDetail) {
    this.structureInventorySummary = structureInventorySummary;
    this.structureInventoryDetailed = structureInventoryDetailed;
    this.structureDetail = structureDetail;
    this.repairsSummary = repairsSummary;
    this.monitorsSummary = monitorsSummary;
    this.inspectionsConductedSummary = inspectionsConductedSummary;
    this.inspectionsConductedDetail = inspectionsConductedDetail;
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

  @Override
  public ResponseEntity<byte[]> repairsSummary(RepairsSummaryCriteria criteria, ReportFormat format) {
    return download(repairsSummary.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> monitorsSummary(
      MonitorsSummaryCriteria criteria, ReportFormat format) {
    return download(monitorsSummary.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsConductedSummary(
      InspectionsConductedCriteria criteria, ReportFormat format) {
    return download(inspectionsConductedSummary.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsConductedDetail(
      InspectionsConductedCriteria criteria, ReportFormat format) {
    return download(inspectionsConductedDetail.generate(criteria, format));
  }

  /** Always an attachment: a report is saved, and the page asks for it to be. */
  static ResponseEntity<byte[]> download(ReportFile file) {
    return ResponseEntity.ok()
        .contentType(file.mediaType())
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(file.content());
  }
}
