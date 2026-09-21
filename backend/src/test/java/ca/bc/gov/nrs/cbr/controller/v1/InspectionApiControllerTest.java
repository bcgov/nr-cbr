package ca.bc.gov.nrs.cbr.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ca.bc.gov.nrs.cbr.service.v1.InspectionSearchService;
import ca.bc.gov.nrs.cbr.service.v1.InspectionService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * That each operation reaches its own service, and that deleting answers 204.
 *
 * <p>Two services behind one controller is where a delegation slip lives: both halves compile
 * against either service, and a delete routed through the search service would fail as a 500 rather
 * than as anything a reader of the code would notice.
 */
class InspectionApiControllerTest {

  private final InspectionSearchService searchService = mock(InspectionSearchService.class);
  private final InspectionService inspectionService = mock(InspectionService.class);
  private final InspectionApiController controller =
      new InspectionApiController(searchService, inspectionService);

  @Test
  @DisplayName("deleting answers 204 with no body, because nothing is left to return")
  void deleteAnswersNoContent() {
    assertThat(controller.deleteInspection(42L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(controller.deleteInspection(42L).getBody()).isNull();
  }

  @Test
  @DisplayName("deleting reaches the inspection service, not the search service")
  void deleteDelegates() {
    controller.deleteInspection(42L);

    verify(inspectionService).delete(42L);
    verifyNoInteractions(searchService);
  }

  @Test
  @DisplayName("searching reaches the search service, not the inspection service")
  void searchDelegates() {
    InspectionSearchCriteria criteria = InspectionSearchCriteria.builder().siteId("S").build();

    controller.searchInspections(criteria, 2, 50);

    verify(searchService).search(criteria, 2, 50);
    verifyNoInteractions(inspectionService);
  }
}
