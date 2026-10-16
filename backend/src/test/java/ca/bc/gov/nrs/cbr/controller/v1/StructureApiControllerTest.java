package ca.bc.gov.nrs.cbr.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.service.v1.StructureBridgeService;
import ca.bc.gov.nrs.cbr.service.v1.StructureCommentsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureCreateService;
import ca.bc.gov.nrs.cbr.service.v1.StructureCulvertService;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailService;
import ca.bc.gov.nrs.cbr.service.v1.StructureDetailsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureDocumentsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInformationService;
import ca.bc.gov.nrs.cbr.service.v1.StructureInspectionsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureLoadRatingsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureMonitorsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureRepairsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureReplacementsService;
import ca.bc.gov.nrs.cbr.service.v1.StructureSearchService;
import ca.bc.gov.nrs.cbr.service.v1.StructureService;
import ca.bc.gov.nrs.cbr.service.v1.StructureSpansAndPiersService;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * That the structure page's repairs, monitoring items and documents reach their services as the
 * structure's own — no inspection — and answer with the right status.
 */
class StructureApiControllerTest {

  private final StructureDocumentsService documents = mock(StructureDocumentsService.class);
  private final StructureRepairsService repairs = mock(StructureRepairsService.class);
  private final StructureMonitorsService monitors = mock(StructureMonitorsService.class);
  private final StructureApiController controller = new StructureApiController(
      mock(StructureSearchService.class), mock(StructureService.class),
      mock(StructureDetailService.class), mock(StructureSpansAndPiersService.class), documents,
      mock(StructureInspectionsService.class), repairs, monitors,
      mock(StructureCommentsService.class), mock(StructureInformationService.class),
      mock(StructureDetailsService.class), mock(StructureBridgeService.class),
      mock(StructureLoadRatingsService.class), mock(StructureCulvertService.class),
      mock(StructureReplacementsService.class), mock(StructureCreateService.class));

  @Test
  @DisplayName("lists repairs and monitoring items for the view and inspection asked")
  void lists() {
    controller.getRepairs(7L, StructureRepairsResponse.View.CURRENT, 41L, 0, 10, false);
    controller.getMonitors(7L, StructureMonitorsResponse.View.ALL, null, 1, 20, true);

    verify(repairs).repairs(7L, StructureRepairsResponse.View.CURRENT, 41L, 0, 10, false);
    verify(monitors).monitors(7L, StructureMonitorsResponse.View.ALL, null, 1, 20, true);
  }

  @Test
  @DisplayName("adds a repair, a monitoring item and a document as the structure's own: 201")
  void addsAsTheStructures() {
    when(repairs.create(7L, null, null)).thenReturn(new RepairCreatedResponse("5", 1L));
    when(monitors.create(7L, null, null)).thenReturn(new MonitorCreatedResponse("6", 1L));
    when(documents.upload(7L, null, null, null)).thenReturn("9");

    assertThat(controller.createRepair(7L, null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(controller.createMonitor(7L, null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var added = controller.addDocument(7L, null, null);

    assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(added.getBody().id()).isEqualTo("9");
  }

  @Test
  @DisplayName("edits and deletes them: 204")
  void editsAndDeletes() {
    assertThat(controller.updateRepair(7L, 5L, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(controller.deleteRepair(7L, 5L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(controller.updateMonitor(7L, 6L, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(controller.deleteMonitor(7L, 6L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(controller.updateDocument(7L, 9L, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(controller.deleteDocument(7L, 9L).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    verify(repairs).delete(7L, 5L);
    verify(monitors).delete(7L, 6L);
    verify(documents).delete(7L, 9L);
  }
}
