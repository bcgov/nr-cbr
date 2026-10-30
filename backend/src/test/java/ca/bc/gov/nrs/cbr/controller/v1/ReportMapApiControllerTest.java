package ca.bc.gov.nrs.cbr.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.service.v1.InspectionReportMaps;
import ca.bc.gov.nrs.cbr.service.v1.ReportFile;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ReportMapApiControllerTest {

  private static final MediaType KMZ = MediaType.parseMediaType("application/vnd.google-earth.kmz");

  private final StructureInventoryDetailedService detailed =
      mock(StructureInventoryDetailedService.class);
  private final InspectionReportMaps inspections = mock(InspectionReportMaps.class);
  private final ReportMapApiController controller =
      new ReportMapApiController(detailed, inspections);

  private final InspectionsConductedCriteria conducted = InspectionsConductedCriteria.builder().build();
  private final InspectionsDueCriteria due = InspectionsDueCriteria.builder().build();

  @Test
  @DisplayName("sends an inspection report's map as a KMZ attachment under its own name")
  void downloadsAnInspectionReportMap() {
    ReportFile file = file("InspectionsDueSummaryReport");
    when(inspections.inspectionsDueSummary(due)).thenReturn(file);

    ResponseEntity<byte[]> response = controller.inspectionsDueSummaryKmz(due);

    assertThat(response.getBody()).isEqualTo(file.content());
    assertThat(response.getHeaders().getContentType()).isEqualTo(KMZ);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment;")
        .contains("InspectionsDueSummaryReport_2026-10-30.kmz");
  }

  @Test
  @DisplayName("sends each report's own map")
  void downloadsEachReportsMap() {
    StructureInventoryDetailedCriteria inventory = StructureInventoryDetailedCriteria.builder().build();
    when(detailed.kmz(inventory)).thenReturn(file("StructureInventoryDetailReport"));
    when(inspections.inspectionsConductedSummary(conducted))
        .thenReturn(file("InspectionsConductedSummaryReport"));
    when(inspections.inspectionsConductedDetail(conducted))
        .thenReturn(file("InspectionsConductedDetailReport"));
    when(inspections.inspectionsDueDetailed(due)).thenReturn(file("InspectionsDueDetailedReport"));

    assertThat(disposition(controller.structureInventoryDetailedKmz(inventory)))
        .contains("StructureInventoryDetailReport_");
    assertThat(disposition(controller.inspectionsConductedSummaryKmz(conducted)))
        .contains("InspectionsConductedSummaryReport_");
    assertThat(disposition(controller.inspectionsConductedDetailKmz(conducted)))
        .contains("InspectionsConductedDetailReport_");
    assertThat(disposition(controller.inspectionsDueDetailedKmz(due)))
        .contains("InspectionsDueDetailedReport_");
  }

  private static ReportFile file(String name) {
    return new ReportFile(new byte[] {80, 75}, name + "_2026-10-30.kmz", KMZ);
  }

  private static String disposition(ResponseEntity<byte[]> response) {
    return response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
  }
}
