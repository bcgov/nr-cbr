package ca.bc.gov.nrs.cbr.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.service.v1.InspectionsConductedDetailService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionsConductedSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.MonitorsSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.RepairsSummaryService;
import ca.bc.gov.nrs.cbr.service.v1.ReportFile;
import ca.bc.gov.nrs.cbr.service.v1.ReportTarget;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailReportService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventorySummaryService;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

class ReportApiControllerTest {

  private final StructureInventorySummaryService service =
      mock(StructureInventorySummaryService.class);
  private final StructureDetailReportService structureDetail =
      mock(StructureDetailReportService.class);
  private final ReportApiController controller = new ReportApiController(service,
      mock(StructureInventoryDetailedService.class), structureDetail,
      mock(RepairsSummaryService.class), mock(MonitorsSummaryService.class),
      mock(InspectionsConductedSummaryService.class),
      mock(InspectionsConductedDetailService.class));

  @Test
  @DisplayName("streams a CSV to the response as a typed attachment under its own name")
  void streamsTheCsv() throws Exception {
    StructureInventorySummaryCriteria criteria = StructureInventorySummaryCriteria.builder().build();
    doAnswer(call -> {
      ReportTarget target = call.getArgument(1);
      try (OutputStream out = target.open(
          "StructureInventorySummaryReport_2026-10-20.csv", new MediaType("text", "csv"))) {
        out.write("a,b".getBytes(StandardCharsets.UTF_8));
      }
      return null;
    }).when(service).writeCsv(eq(criteria), any());
    MockHttpServletResponse http = new MockHttpServletResponse();

    ResponseEntity<byte[]> response =
        controller.structureInventorySummary(criteria, ReportFormat.CSV, http);

    // Written to the response, not returned: Spring sends nothing more.
    assertThat(response).isNull();
    assertThat(http.getContentAsString()).isEqualTo("a,b");
    assertThat(http.getContentType()).isEqualTo("text/csv");
    assertThat(http.getHeader(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment;")
        .contains("StructureInventorySummaryReport_2026-10-20.csv");
    assertThat(http.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    verify(service, never()).generate(any(), any());
  }

  @Test
  @DisplayName("sends a structure's detail report as a PDF attachment")
  void downloadsTheStructureDetailReport() {
    byte[] pdf = "%PDF".getBytes();
    when(structureDetail.generate(42L, ReportFormat.PDF)).thenReturn(new ReportFile(
        pdf, "StructureDetailReport_B1_2026-10-20.pdf", MediaType.APPLICATION_PDF));

    ResponseEntity<byte[]> response = controller.structureDetail(42L, ReportFormat.PDF,
        new MockHttpServletResponse());

    assertThat(response.getBody()).isEqualTo(pdf);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment;")
        .contains("StructureDetailReport_B1_2026-10-20.pdf");
  }
}
