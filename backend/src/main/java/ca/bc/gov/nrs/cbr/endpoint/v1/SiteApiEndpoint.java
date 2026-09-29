package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SiteCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.SiteCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SiteDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for crossing sites. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.SiteApiController}.
 *
 * <p>The mappings and the authorization live here rather than on the controller, following nr-frep:
 * the interface is the contract, the controller is one way of satisfying it. It also means the
 * {@code @PreAuthorize} cannot be dropped by someone editing the implementation, because removing it
 * from the interface changes the published contract.
 *
 * <p><b>The two operations are gated very differently</b> — reading is open to every role that can
 * read at all, deleting to two — which is the thing to keep in view when adding a third.
 */
@RequestMapping("/api/v1/sites")
public interface SiteApiEndpoint {

  /**
   * Server-side paginated site search — one page plus the true total.
   *
   * <p>Paginated on the server because the legacy screen is: the result of an unfiltered search is
   * every site in the province, and CBR has no scoping that would narrow it.
   *
   * <p>{@code @Valid} is what turns a non-numeric kilometre bound into a 400 naming the field,
   * rather than a value that reaches the {@code WHERE} clause and matches nothing.
   *
   * <p>Gated on {@link CbrAuthorities#READ}, the legacy {@code /showSiteSearch} privilege — every
   * role that can read holds it, and {@code CBR_ADMIN} now does too. This is an admission check, not
   * a row filter: CBR has no region, district or client scoping, so a caller who passes it sees
   * every site in the province. There is no narrower gate to apply.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/search")
  ResponseEntity<PagedResponse<SiteSearchResult>> searchSites(
      @Valid @ModelAttribute SiteSearchCriteria criteria,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize);

  /**
   * One site, for the detail screen.
   *
   * <p>Gated on {@link CbrAuthorities#READ} — legacy's {@code /showSite}, which sits at the
   * GENERAL floor alongside the search that leads here. A user who can find a site can open it.
   *
   * <p>Answers with the stored values: codes as codes, org units as numbers. The screen holds the
   * code tables it needs to decode them, and is the only thing that knows how they should read.
   * The road name and the maintainer are the exceptions — both sit on tables the screen has no
   * other reason to fetch, and legacy goes after both itself.
   *
   * <p>404 when there is no such site. The link that leads here comes from a results table that
   * may have been on screen for some time, so a site deleted in the meantime is an ordinary case
   * rather than a broken link.
   *
   * @param siteId the {@code CROSSING_SITE_ID}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{siteId}")
  ResponseEntity<SiteDetailResponse> getSite(@PathVariable("siteId") String siteId);

  /**
   * Creates a site.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — legacy's {@code /addSite} privilege, which is
   * a {@code LEVEL_2} one. Adding a site is grouped with the destructive actions there rather than
   * with ordinary editing, because a site is the root every structure and inspection hangs off and
   * a wrong one cannot simply be tidied away.
   *
   * <p><b>200 with the new site's number, and nothing else.</b> The caller already holds what it
   * sent; the one thing it does not know is the key, because the server upper-cases it. Returning
   * the stored record meant reading the row straight back to answer with what had just been said.
   *
   * <p>The payload type rather than a {@code ResponseEntity}, which is how a create answers in
   * EDUC-STUDENT-ASSESSMENT-API: Spring gives 200 on its own, and {@code ResponseEntity} is kept
   * for the replies that carry no body — {@link #deleteSite} below.
   *
   * <p><b>400 with a message per field when a rule fails.</b> Every check {@link
   * ca.bc.gov.nrs.cbr.service.v1.SiteValidator} makes is reported at once and keyed by the field it
   * belongs to, so the form can mark the boxes rather than print a paragraph. See
   * {@link SiteValidationAdvice} for the shape.
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PostMapping
  SiteCreatedResponse createSite(@RequestBody SiteCreateRequest request);

  /**
   * Deletes a site.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — legacy's {@code /deleteSite} privilege, held by
   * {@code CBR_LEVEL_2} and {@code CBR_PENG} and by nobody else. Notably <b>not</b> by
   * {@code CBR_ADMIN}: administrators can read everything and write nothing
   * (cbr-auth-and-roles.local.md §3.2).
   *
   * <p>204 on success, because there is nothing left to return. 404 if the site is already gone,
   * 409 if something still references it — see
   * {@link ca.bc.gov.nrs.cbr.service.v1.SiteService#delete(String)}, where those are decided.
   *
   * @param siteId the {@code CROSSING_SITE_ID}, a 14-character natural key
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{siteId}")
  ResponseEntity<Void> deleteSite(@PathVariable("siteId") String siteId);
}
