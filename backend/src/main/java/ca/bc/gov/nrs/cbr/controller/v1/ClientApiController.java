package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.ClientApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.ClientLookupService;
import ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult;
import ca.bc.gov.nrs.cbr.struct.v1.ClientScope;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Forest Client lookup behind the Designated Maintainer field. Mappings and authorization are
 * declared on {@link ClientApiEndpoint}.
 */
@RestController
public class ClientApiController implements ClientApiEndpoint {

  private final ClientLookupService clientLookupService;

  public ClientApiController(ClientLookupService clientLookupService) {
    this.clientLookupService = clientLookupService;
  }

  @Override
  public ResponseEntity<List<ClientLookupResult>> searchClients(String term, ClientScope scope) {
    return ResponseEntity.ok(clientLookupService.suggest(term, scope));
  }

  @Override
  public ResponseEntity<List<ClientLookupResult>> clientLocations(String clientNumber) {
    return ResponseEntity.ok(clientLookupService.locationsOf(clientNumber));
  }
}
