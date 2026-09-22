package ca.bc.gov.nrs.cbr.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.service.v1.ClientLookupService;
import ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult;
import ca.bc.gov.nrs.cbr.struct.v1.ClientScope;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.http.HttpStatus;

/** That the controller passes the term through and hands back what the service found. */
class ClientApiControllerTest {

  private final ClientLookupService service = mock(ClientLookupService.class);
  private final ClientApiController controller = new ClientApiController(service);

  private static final ClientLookupResult CANFOR =
      new ClientLookupResult("00001012", "00", "CANFOR CORPORATION", null, "Vancouver");

  @Test
  @DisplayName("passes the term to the service and returns 200 with what it found")
  void delegates() {
    when(service.suggest("canfor", ClientScope.MAINTAINERS)).thenReturn(List.of(CANFOR));

    var response = controller.searchClients("canfor", ClientScope.MAINTAINERS);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsExactly(CANFOR);
    verify(service).suggest("canfor", ClientScope.MAINTAINERS);
  }

  @Test
  @DisplayName("passes the scope through, so each screen gets the clients it can act on")
  void passesTheScope() {
    when(service.suggest("canfor", ClientScope.ROAD_FILE_HOLDERS)).thenReturn(List.of(CANFOR));

    controller.searchClients("canfor", ClientScope.ROAD_FILE_HOLDERS);

    verify(service).suggest("canfor", ClientScope.ROAD_FILE_HOLDERS);
  }

  @Nested
  @DisplayName("a term that matches nothing")
  class NoMatches {

    /**
     * 200 with an empty body, not 404. The collection exists and was searched; that nothing in it
     * matched is an answer, and a combo box asking on every keystroke would otherwise spend most
     * of a word reading failures.
     */
    @Test
    @DisplayName("is still a 200, with an empty list")
    void returnsAnEmptyList() {
      when(service.suggest("zzz", ClientScope.MAINTAINERS)).thenReturn(List.of());

      var response = controller.searchClients("zzz", ClientScope.MAINTAINERS);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isEmpty();
    }
  }
}
