package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class InspectionReportMapsTest {

  private final StructureInventoryDetailedService detailed =
      mock(StructureInventoryDetailedService.class);
  private final InspectionsConductedSummaryService conductedSummary =
      mock(InspectionsConductedSummaryService.class);
  private final InspectionsConductedDetailService conductedDetail =
      mock(InspectionsConductedDetailService.class);
  private final InspectionsDueSummaryService dueSummary = mock(InspectionsDueSummaryService.class);
  private final InspectionsDueDetailedService dueDetailed = mock(InspectionsDueDetailedService.class);
  private final InspectionReportMaps maps = new InspectionReportMaps(
      detailed, conductedSummary, conductedDetail, dueSummary, dueDetailed);

  private final InspectionsConductedCriteria conducted = InspectionsConductedCriteria.builder().build();
  private final InspectionsDueCriteria due = InspectionsDueCriteria.builder().build();

  @Test
  @DisplayName("maps each inspection report's structures as the Detailed KMZ, under its own name")
  void mapsEachReport() {
    when(conductedSummary.structureIds(conducted)).thenReturn(List.of(1L, 2L));
    when(conductedDetail.structureIds(conducted)).thenReturn(List.of(3L));
    when(dueSummary.structureIds(due)).thenReturn(List.of(4L, 4L));
    when(dueDetailed.structureIds(due)).thenReturn(List.of(5L));
    ReportFile conductedSummaryFile = file("InspectionsConductedSummaryReport");
    ReportFile conductedDetailFile = file("InspectionsConductedDetailReport");
    ReportFile dueSummaryFile = file("InspectionsDueSummaryReport");
    ReportFile dueDetailedFile = file("InspectionsDueDetailedReport");
    when(detailed.kmzOf(List.of(1L, 2L), "Inspections Conducted - Summary",
        "InspectionsConductedSummaryReport")).thenReturn(conductedSummaryFile);
    when(detailed.kmzOf(List.of(3L), "Inspections Conducted - Detail",
        "InspectionsConductedDetailReport")).thenReturn(conductedDetailFile);
    when(detailed.kmzOf(List.of(4L, 4L), "Inspections Due - Summary",
        "InspectionsDueSummaryReport")).thenReturn(dueSummaryFile);
    when(detailed.kmzOf(List.of(5L), "Inspections Due - Detailed",
        "InspectionsDueDetailedReport")).thenReturn(dueDetailedFile);

    assertThat(maps.inspectionsConductedSummary(conducted)).isSameAs(conductedSummaryFile);
    assertThat(maps.inspectionsConductedDetail(conducted)).isSameAs(conductedDetailFile);
    assertThat(maps.inspectionsDueSummary(due)).isSameAs(dueSummaryFile);
    assertThat(maps.inspectionsDueDetailed(due)).isSameAs(dueDetailedFile);
  }

  private static ReportFile file(String name) {
    return new ReportFile(new byte[] {1}, name + "_2026-10-30.kmz",
        MediaType.parseMediaType("application/vnd.google-earth.kmz"));
  }
}
