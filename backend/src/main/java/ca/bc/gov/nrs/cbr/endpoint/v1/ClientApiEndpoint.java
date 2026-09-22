package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.ClientLookupResult;
import ca.bc.gov.nrs.cbr.struct.v1.ClientScope;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the Forest Client lookup. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.ClientApiController}.
 *
 * <p>Mappings and authorization live here rather than on the controller, as on
 * {@link SiteApiEndpoint} and {@link InspectionApiEndpoint}.
 *
 * <p><b>Separate from {@link ConfigurationApiEndpoint}, though it is also a lookup.</b> Everything
 * there is a bounded code table fetched once and cached for a whole session. This is unbounded and
 * parameterised — it runs per keystroke and its answer depends on the term — so it neither caches
 * like those nor belongs in a bundle with them.
 */
@RequestMapping("/api/v1/clients")
public interface ClientApiEndpoint {

  /**
   * Designated Maintainer suggestions for a term.
   *
   * <p>Gated on {@link CbrAuthorities#READ}, matching the configuration lookups: this exists to
   * fill in a READ-gated search form, so a token holder with no CBR role has nothing to do with it.
   *
   * <p><b>Only maintainers already recorded on a site are returned</b>, which is legacy's "Only
   * search existing clients within the system?" mode; see
   * {@link ca.bc.gov.nrs.cbr.repository.v1.ClientLocationRepository} for why that is the only mode
   * here. Each suggestion is a client <em>location</em>, because the pair
   * {@code (CLIENT_NUMBER, CLIENT_LOCN_CODE)} is what a site records and what the search filters
   * on — so one client may appear more than once.
   *
   * <p>Always 200. A term that cannot match — blank, under three characters, more than eight digits
   * — returns an empty list rather than a 400, because this is called while the user is still
   * typing and a rejection mid-word describes a mistake they have not finished making.
   *
   * <p><b>{@code scope} says which clients the calling screen can act on</b>, and there is no
   * unscoped option — see {@link ClientScope}. It defaults to the maintainers the site searches
   * want, so the parameter is only named by the screen that wants the other set.
   *
   * @param term  the text typed into the client field
   * @param scope which clients to offer
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping
  ResponseEntity<List<ClientLookupResult>> searchClients(
      @RequestParam(name = "term", defaultValue = "") String term,
      @RequestParam(name = "scope", defaultValue = "MAINTAINERS") ClientScope scope);
}
