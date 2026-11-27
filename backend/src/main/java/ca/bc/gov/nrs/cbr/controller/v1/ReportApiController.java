package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.ReportApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsConductedDetailService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsConductedSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.MonitorsSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.RepairsSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.ReportFile;
import ca.bc.gov.nrs.cbr.service.v1.ReportTarget;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailReportService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventorySummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RepairsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
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
      StructureInventorySummaryCriteria criteria, ReportFormat format, HttpServletResponse response) {
    if (format == ReportFormat.CSV) {
      return streamed(response, target -> structureInventorySummary.writeCsv(criteria, target));
    }
    return download(structureInventorySummary.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> structureInventoryDetailed(
      StructureInventoryDetailedCriteria criteria, ReportFormat format, HttpServletResponse response) {
    if (format == ReportFormat.CSV) {
      return streamed(response, target -> structureInventoryDetailed.writeCsv(criteria, target));
    }
    return download(structureInventoryDetailed.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> structureDetail(
      long structureId, ReportFormat format, HttpServletResponse response) {
    if (format == ReportFormat.CSV) {
      return streamed(response, target -> structureDetail.writeCsv(structureId, target));
    }
    return download(structureDetail.generate(structureId, format));
  }

  @Override
  public ResponseEntity<byte[]> repairsSummary(
      RepairsSummaryCriteria criteria, ReportFormat format, HttpServletResponse response) {
    if (format == ReportFormat.CSV) {
      return streamed(response, target -> repairsSummary.writeCsv(criteria, target));
    }
    return download(repairsSummary.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> monitorsSummary(
      MonitorsSummaryCriteria criteria, ReportFormat format, HttpServletResponse response) {
    if (format == ReportFormat.CSV) {
      return streamed(response, target -> monitorsSummary.writeCsv(criteria, target));
    }
    return download(monitorsSummary.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsConductedSummary(
      InspectionsConductedCriteria criteria, ReportFormat format, HttpServletResponse response) {
    if (format == ReportFormat.CSV) {
      return streamed(response, target -> inspectionsConductedSummary.writeCsv(criteria, target));
    }
    return download(inspectionsConductedSummary.generate(criteria, format));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsConductedDetail(
      InspectionsConductedCriteria criteria, ReportFormat format, HttpServletResponse response) {
    ReportDistricts.require(criteria.areaOrgUnit(), criteria.orgUnit());
    // Both formats streamed: a province's is the largest report CBR makes, 171 MB as a PDF.
    return format == ReportFormat.CSV
        ? streamed(response, target -> inspectionsConductedDetail.writeCsv(criteria, target))
        : streamed(response, target -> inspectionsConductedDetail.writePdf(criteria, target));
  }

  /**
   * A report written straight to the response as it is made, rather than assembled in memory and
   * returned: a province-wide CSV runs to hundreds of thousands of rows and over 100 MB, more than
   * the pod's heap can hold several copies of; the largest PDFs, likewise. The headers are set when the report opens the
   * response, once it is ready to write, so a failure before then is still an ordinary error.
   *
   * <p>Returns null: the response is written, and Spring has nothing more to send.
   */
  static ResponseEntity<byte[]> streamed(
      HttpServletResponse response, Consumer<ReportTarget> report) {
    report.accept((filename, mediaType) -> {
      response.setContentType(mediaType.toString());
      response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
          .filename(filename, StandardCharsets.UTF_8).build().toString());
      response.setHeader("X-Content-Type-Options", "nosniff");
      return response.getOutputStream();
    });
    return null;
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
