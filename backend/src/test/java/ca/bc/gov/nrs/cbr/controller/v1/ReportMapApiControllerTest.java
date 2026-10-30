package ca.bc.gov.nrs.cbr.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.service.v1.InspectionReportMaps;
import ca.bc.gov.nrs.cbr.service.v1.ReportFile;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ReportMapApiControllerTest {

  private static final MediaType KMZ = MediaType.parseMediaType("application/vnd.google-earth.kmz");

  private final InspectionReportMaps inspections = mock(InspectionReportMaps.class);
  private final ReportMapApiController controller = new ReportMapApiController(
      mock(StructureInventoryDetailedService.class), inspections);

  @Test
  @DisplayName("sends an inspection report's map as a KMZ attachment under its own name")
  void downloadsAnInspectionReportMap() {
    InspectionsDueCriteria criteria = InspectionsDueCriteria.builder().build();
    byte[] kmz = {80, 75};
    when(inspections.inspectionsDueSummary(criteria)).thenReturn(
        new ReportFile(kmz, "InspectionsDueSummaryReport_2026-10-30.kmz", KMZ));

    ResponseEntity<byte[]> response = controller.inspectionsDueSummaryKmz(criteria);

    assertThat(response.getBody()).isEqualTo(kmz);
    assertThat(response.getHeaders().getContentType()).isEqualTo(KMZ);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment;")
        .contains("InspectionsDueSummaryReport_2026-10-30.kmz");
  }
}
