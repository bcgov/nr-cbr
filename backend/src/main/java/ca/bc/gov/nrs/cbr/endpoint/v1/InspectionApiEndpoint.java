package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.CreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.DocumentDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSortColumn;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for structure inspections. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.InspectionApiController}.
 *
 * <p>The mappings and the authorization live here rather than on the controller, as in
 * {@link SiteApiEndpoint}: the interface is the contract, the controller is one way of
 * satisfying it. It also means the {@code @PreAuthorize} cannot be dropped by someone editing the
 * implementation, because removing it from the interface changes the published contract.
 *
 * <p><b>The two operations here are gated differently, and a third will be different again.</b>
 * Searching is {@link CbrAuthorities#READ}; deleting an offline inspection is
 * {@link CbrAuthorities#DESTRUCTIVE}. Moving an inspection to {@code RVD} is
 * {@code /approveInspection} — {@link CbrAuthorities#APPROVE}, a narrower set than either — and it
 * carries a second, non-role condition on top: the saving user must also hold a row in
 * {@code STRUCTURE_INSPECTION_REVIEWER} (cbr-inspection-reviewer.local.md §4). Whoever adds it
 * should not assume the gate on either of these.
 */
@RequestMapping("/api/v1/inspections")
public interface InspectionApiEndpoint {

  /**
   * Server-side paginated inspection search — one page plus the true total.
   *
   * <p>Paginated on the server because the legacy screen is: {@code FIND_INSPECTIONS_BY_CRITERIA}
   * takes a page and a page size, and an unfiltered search would otherwise return every inspection
   * ever recorded.
   *
   * <p><b>A request with no criteria returns everything, a page at a time.</b> Legacy refuses it
   * outright, for a reason that no longer applies once the query is paged — see
   * {@link ca.bc.gov.nrs.cbr.service.v1.InspectionSearchService#search}. {@code /api/v1/sites/search}
   * behaves the same way.
   *
   * <p>{@code @Valid} is what turns a misformatted month into a 400 naming the field, rather than a
   * value that silently drops out of the {@code WHERE} clause and widens the search.
   *
   * <p>Gated on {@link CbrAuthorities#READ}, the legacy {@code /showInspectionSearch} privilege —
   * every role that can read holds it. This is an admission check, not a row filter: CBR has no
   * region, district or client scoping, so a caller who passes it can search every inspection in the
   * province. There is no narrower gate to apply (cbr-auth-and-roles.local.md §3.3).
   *
   * <p><b>Sorted by a results column</b> when {@code sortBy} names one
   * ({@link InspectionSortColumn}), {@code sortDirection} {@code ASC} or {@code DESC}; legacy's
   * default order — structure name, then newest first — otherwise. On the server, so it orders
   * every match rather than the page on screen. An unknown value is a 400.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/search")
  ResponseEntity<PagedResponse<InspectionSearchResult>> searchInspections(
      @Valid @ModelAttribute InspectionSearchCriteria criteria,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "sortBy", required = false) InspectionSortColumn sortBy,
      @RequestParam(name = "sortDirection", defaultValue = "ASC") Sort.Direction sortDirection);

  /**
   * Deletes an offline inspection.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — legacy's {@code /deleteInspection} privilege,
   * one of the fifteen held by {@code CBR_LEVEL_2} and inherited by {@code CBR_PENG}, and by nobody
   * else. Notably <b>not</b> by {@code CBR_ADMIN}: administrators can read everything and write
   * nothing (cbr-auth-and-roles.local.md §3.2). The same gate as {@code deleteSite}, because legacy
   * puts both deletes in the same privilege set.
   *
   * <p><b>Being allowed to call this is not the same as this being allowed.</b> The privilege says
   * who may delete <em>an</em> inspection; the service decides whether <em>this</em> one may be
   * deleted, and refuses anything that is not offline. Legacy enforces only the first half — its
   * {@code OFL} rule lives in a JSP conditional and its action never re-checks — so a URL typed by
   * hand could delete a reviewed inspection and its whole audit trail. See
   * {@link ca.bc.gov.nrs.cbr.service.v1.InspectionService#delete(Long)}.
   *
   * <p>204 on success, because there is nothing left to return. 404 if the inspection is already
   * gone, 409 if it exists but is not offline — the body carries a sentence naming the status it
   * holds instead, which the caller should show rather than replace with wording of its own.
   *
   * @param inspectionId the {@code STRUCTURE_INSPECTION.INSPECTION_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{inspectionId}")
  ResponseEntity<Void> deleteInspection(@PathVariable("inspectionId") Long inspectionId);

  /**
   * The questions an inspection of the structure asks — Add Inspection's Condition tab — and the
   * spans and piers to answer them for.
   *
   * <p>Gated on {@link CbrAuthorities#READ}: an inspection's page shows every question asked, to
   * whoever may read it, as Add Inspection asks them. <b>404</b> when there is no such structure.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/form")
  InspectionFormResponse inspectionForm(@RequestParam(name = "structureId") long structureId);

  /**
   * Adds an inspection, In Progress or Submitted — Add Inspection.
   *
   * <p>Gated on {@link CbrAuthorities#INSPECTION_WRITE} — Level 0 and up, legacy's
   * {@code /saveInspection}; a Routine inspection needs Level 1, checked by the service. <b>201</b>
   * with the new inspection's id; <b>404</b> when there is no such structure; <b>400</b> with
   * {@code fieldErrors} keyed by field ({@code inspectionDate}, {@code items.DECK.Q1.span-12}, …).
   */
  @PreAuthorize(CbrAuthorities.INSPECTION_WRITE)
  @PostMapping
  ResponseEntity<CreatedResponse> createInspection(@RequestBody InspectionCreateRequest request);

  /**
   * One inspection — its page, read only: the inspection, its status history, its review and load
   * rating, its hazards and its answers.
   *
   * <p>Gated on {@link CbrAuthorities#READ}. <b>404</b> when there is no such inspection.
   *
   * @param inspectionId the {@code STRUCTURE_INSPECTION.INSPECTION_ID}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{inspectionId}")
  InspectionDetailResponse getInspection(@PathVariable("inspectionId") long inspectionId);

  /**
   * Adds a repair from the inspection's page — legacy's Add Repair on its Repairs & Monitoring
   * tab. Suggested, the structure's next number, raised by this inspection.
   *
   * <p>Gated on {@link CbrAuthorities#INSPECTION_WRITE} — Level 0 and up: an inspector records
   * repairs while inspecting. <b>201</b>; <b>404</b> when there is no such inspection; <b>409</b>
   * once it is Reviewed or Rejected; <b>400</b> with {@code fieldErrors} when a field is refused.
   */
  @PreAuthorize(CbrAuthorities.INSPECTION_WRITE)
  @PostMapping("/{inspectionId}/repairs")
  ResponseEntity<RepairCreatedResponse> addRepair(
      @PathVariable("inspectionId") long inspectionId, @RequestBody RepairCreateRequest request);

  /**
   * Saves an edit to one of the structure's repairs, from the inspection's page — any of them, as
   * legacy. Level 0 and up; <b>204</b>, <b>404</b>, <b>409</b> or <b>400</b> as adding.
   */
  @PreAuthorize(CbrAuthorities.INSPECTION_WRITE)
  @PutMapping("/{inspectionId}/repairs/{repairId}")
  ResponseEntity<Void> updateRepair(@PathVariable("inspectionId") long inspectionId,
      @PathVariable("repairId") long repairId, @RequestBody RepairUpdateRequest request);

  /**
   * Deletes one of the structure's repairs, from the inspection's page. Gated on
   * {@link CbrAuthorities#DESTRUCTIVE} — Level 2 and up, legacy's regional engineer.
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{inspectionId}/repairs/{repairId}")
  ResponseEntity<Void> deleteRepair(@PathVariable("inspectionId") long inspectionId,
      @PathVariable("repairId") long repairId);

  /**
   * Adds a monitoring item from the inspection's page — legacy's Add Monitor. Suggested, the
   * structure's next number, raised by this inspection. Level 0 and up; as adding a repair.
   */
  @PreAuthorize(CbrAuthorities.INSPECTION_WRITE)
  @PostMapping("/{inspectionId}/monitors")
  ResponseEntity<MonitorCreatedResponse> addMonitor(
      @PathVariable("inspectionId") long inspectionId, @RequestBody MonitorCreateRequest request);

  /** Saves an edit to one of the structure's monitoring items, from the inspection's page. */
  @PreAuthorize(CbrAuthorities.INSPECTION_WRITE)
  @PutMapping("/{inspectionId}/monitors/{monitorId}")
  ResponseEntity<Void> updateMonitor(@PathVariable("inspectionId") long inspectionId,
      @PathVariable("monitorId") long monitorId, @RequestBody MonitorUpdateRequest request);

  /** Deletes one of the structure's monitoring items, from the inspection's page. Level 2 and up. */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{inspectionId}/monitors/{monitorId}")
  ResponseEntity<Void> deleteMonitor(@PathVariable("inspectionId") long inspectionId,
      @PathVariable("monitorId") long monitorId);

  /**
   * Adds a document or photo from the inspection's page — legacy's Add on its Documents &amp;
   * Photos tab, the inspection's. Multipart: {@code file}, and {@code details} (type, date,
   * description), as the structure page's.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, legacy's multi-file uploader.
   * <b>201</b> with the new id; <b>404</b> when there is no such inspection; <b>409</b> once it is
   * Reviewed or Accepted (legacy disables Add then); <b>400</b> with {@code fieldErrors}.
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PostMapping(value = "/{inspectionId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<CreatedResponse> addDocument(
      @PathVariable("inspectionId") long inspectionId,
      @RequestPart("file") MultipartFile file,
      @RequestPart("details") DocumentDetailsRequest details);

  /**
   * Deletes one of the structure's documents from the inspection's page — any, at any status, as
   * legacy's delete icon. Gated on {@link CbrAuthorities#DESTRUCTIVE} — Level 2, legacy's regional
   * engineer. <b>204</b>; <b>404</b> when there is no such inspection or document.
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{inspectionId}/documents/{fileId}")
  ResponseEntity<Void> deleteDocument(@PathVariable("inspectionId") long inspectionId,
      @PathVariable("fileId") long fileId);
}
