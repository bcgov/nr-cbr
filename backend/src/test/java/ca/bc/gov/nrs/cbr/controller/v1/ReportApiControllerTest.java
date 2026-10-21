package ca.bc.gov.nrs.cbr.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.service.v1.MonitorsSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.RepairsSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.ReportFile;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailReportService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventorySummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ReportApiControllerTest {

  private final StructureInventorySummaryService service =
      mock(StructureInventorySummaryService.class);
  private final StructureDetailReportService structureDetail =
      mock(StructureDetailReportService.class);
  private final ReportApiController controller = new ReportApiController(service,
      mock(StructureInventoryDetailedService.class), structureDetail,
      mock(RepairsSummaryService.class), mock(MonitorsSummaryService.class));

  @Test
  @DisplayName("sends the report as a typed attachment under its own name")
  void downloadsTheReport() {
    StructureInventorySummaryCriteria criteria = StructureInventorySummaryCriteria.builder().build();
    byte[] csv = "a,b".getBytes();
    when(service.generate(criteria, ReportFormat.CSV)).thenReturn(new ReportFile(
        csv, "StructureInventorySummaryReport_2026-10-20.csv", new MediaType("text", "csv")));

    ResponseEntity<byte[]> response = controller.structureInventorySummary(criteria, ReportFormat.CSV);

    assertThat(response.getBody()).isEqualTo(csv);
    assertThat(response.getHeaders().getContentType()).isEqualTo(new MediaType("text", "csv"));
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment;")
        .contains("StructureInventorySummaryReport_2026-10-20.csv");
    assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  @Test
  @DisplayName("sends a structure's detail report as a PDF attachment")
  void downloadsTheStructureDetailReport() {
    byte[] pdf = "%PDF".getBytes();
    when(structureDetail.generate(42L, ReportFormat.PDF)).thenReturn(new ReportFile(
        pdf, "StructureDetailReport_B1_2026-10-20.pdf", MediaType.APPLICATION_PDF));

    ResponseEntity<byte[]> response = controller.structureDetail(42L, ReportFormat.PDF);

    assertThat(response.getBody()).isEqualTo(pdf);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment;")
        .contains("StructureDetailReport_B1_2026-10-20.pdf");
  }
}
