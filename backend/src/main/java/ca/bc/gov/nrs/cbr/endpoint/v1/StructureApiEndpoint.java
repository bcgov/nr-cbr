package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.BridgeDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.CloseProximityInspectionRequest;
import ca.bc.gov.nrs.cbr.struct.v1.CommentRequest;
import ca.bc.gov.nrs.cbr.struct.v1.CreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.CulvertDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.DocumentDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.DocumentUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionScheduleRequest;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.LoadRatingRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.PierRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.RepairCreatedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.RepairTypeOption;
import ca.bc.gov.nrs.cbr.struct.v1.RepairUpdateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.SitePreviewResponse;
import ca.bc.gov.nrs.cbr.struct.v1.SpanRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureCreateRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailsRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureNameSuggestion;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDocumentsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInformationRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionScheduleResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP contract for structures. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.StructureApiController}.
 */
@RequestMapping("/api/v1/structures")
public interface StructureApiEndpoint {

  /**
   * Server-side paginated structure search — one page plus the true total.
   *
   * <p>Gated on {@link CbrAuthorities#READ} — legacy's {@code /showStructureSearch}, which sits at
   * the GENERAL floor with the rest of the {@code /show*} privileges. An admission check, not a row
   * filter: CBR has no scoping, so a caller who passes it sees every structure in the province.
   *
   * <p>{@code @Valid} turns a kilometre or a year that is not one into a 400 naming the field.
   *
   * <p><b>Sorted by a results column</b> when {@code sortBy} names one
   * ({@link StructureSortColumn}), {@code sortDirection} {@code ASC} or {@code DESC}; legacy's fixed
   * order otherwise, which also breaks a chosen column's ties.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/search")
  ResponseEntity<PagedResponse<StructureSearchResult>> searchStructures(
      @Valid @ModelAttribute StructureSearchCriteria criteria,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "sortBy", required = false) StructureSortColumn sortBy,
      @RequestParam(name = "sortDirection", defaultValue = "ASC") Sort.Direction sortDirection);

  /**
   * Archives structures — legacy's "Archive All Selected" on Structure Search.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — legacy's {@code /archiveStructure}, a
   * {@code CBR_LEVEL_2} privilege that {@code CBR_PENG} inherits. The structure stays a row with
   * {@code ACTIVE_IND = 'N'}, hidden from searches unless archived structures are asked for.
   *
   * <p><b>200</b> with how many were archived. Ids with no structure are skipped and
   * already-archived ones archived again, as legacy does. <b>400</b> when no ids are given.
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PutMapping("/archive")
  StructureArchiveResponse archiveStructures(@Valid @RequestBody StructureArchiveRequest request);

  /**
   * Deletes a structure — called once per ticked structure by Structure Search's Delete.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — legacy's {@code /deleteStructure},
   * {@code CBR_LEVEL_2} and {@code CBR_PENG}.
   *
   * <p><b>204</b> on success. <b>404</b> if the structure is gone. <b>409</b> if inspections,
   * documents or photos, repairs, monitors, a close-proximity inspection or a replacement link
   * still belong to it, with a sentence naming which — see
   * {@link ca.bc.gov.nrs.cbr.service.v1.StructureService#delete(long)}.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{structureId}")
  ResponseEntity<Void> deleteStructure(@PathVariable("structureId") long structureId);

  /**
   * Sets the designated maintainer of the ticked structures' sites — legacy's "Update Repair
   * Responsibility for All Selected" on Structure Search.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — legacy's {@code /updateRepairResponsibility},
   * held from {@code CBR_LEVEL_1} up. <b>200</b> with how many structures and sites were updated;
   * <b>400</b> when nothing is ticked, a field is blank, or the maintainer does not exist.
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PutMapping("/repair-responsibility")
  StructureRepairResponsibilityResponse updateRepairResponsibility(
      @Valid @RequestBody StructureRepairResponsibilityRequest request);

  /**
   * One structure, for its page — the header and the Details tab of legacy's
   * {@code showStructure.do}.
   *
   * <p>Gated on {@link CbrAuthorities#READ} — legacy's {@code /showStructure}, which every role
   * that can read holds. <b>404</b> when there is no such structure; legacy showed a blank
   * new-structure form instead.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{structureId}")
  StructureDetailResponse getStructure(@PathVariable("structureId") long structureId);

  /**
   * A bridge's spans and piers — legacy's Spans &amp; Piers tab, fetched when that tab opens.
   *
   * <p>Gated on {@link CbrAuthorities#READ}, as the page is. Both lists are empty for a structure
   * with no bridge row. <b>404</b> when there is no such structure.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{structureId}/spans-and-piers")
  StructureSpansAndPiersResponse getSpansAndPiers(@PathVariable("structureId") long structureId);

  /**
   * Adds a structure to a site — Add Structure, reached from Site Detail.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — Level 2 and up: legacy showed the button at
   * Level 1, but only Level 2 could type the Structure # it needs. <b>201</b> with the new
   * structure's id; <b>400</b> with {@code fieldErrors} keyed by field ({@code structureName},
   * {@code bridge.deckWidth}, {@code spans}, …).
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PostMapping
  ResponseEntity<CreatedResponse> createStructure(@RequestBody StructureCreateRequest request);

  /**
   * A site a structure might be moved to, as the Information tab's Edit previews it under Site #:
   * the site as that tab shows one, and why a structure could not move there — it does not exist,
   * is incomplete, or is Proposed — or null when it could.
   *
   * <p>Gated on {@link CbrAuthorities#READ}: it reads what Site Search shows anyone.
   *
   * @param siteId the {@code CROSSING_SITE_ID} typed
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/site-preview")
  SitePreviewResponse previewSite(@RequestParam(name = "siteId") String siteId);


  /**
   * Saves the Information tab's Edit — the structure's name and site.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, as legacy's page Save, which
   * let anyone change Site #; a change of name is refused below Level 2, as legacy's page offered
   * the box to Level 2 only. <b>204</b> on success; <b>404</b> when there is no such structure;
   * <b>400</b> with {@code fieldErrors} keyed {@code structureName} or {@code siteId}.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PutMapping("/{structureId}/information")
  ResponseEntity<Void> updateInformation(
      @PathVariable("structureId") long structureId,
      @RequestBody StructureInformationRequest request);

  /**
   * Saves the Details tab's Structure card — Built For, the three years, Source, End of Design
   * Life, As Built Information on File and the two costs.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — Level 2 and up, legacy's
   * {@code /level2Access} on every one of these fields. <b>204</b> on success; <b>404</b> when
   * there is no such structure; <b>400</b> with {@code fieldErrors} keyed by the request's fields.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PutMapping("/{structureId}/structure-details")
  ResponseEntity<Void> updateStructureDetails(
      @PathVariable("structureId") long structureId,
      @RequestBody StructureDetailsRequest request);

  /**
   * Saves the Details tab's Bridge card — the bridge's fields, with the structure's Type/Class
   * (Durable or Non-Durable Bridge) and Portable Structure.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — Level 2 and up, legacy's
   * {@code /level2Access} on every one of these fields. <b>204</b> on success; <b>404</b> when
   * there is no such structure; <b>409</b> when it has no bridge record; <b>400</b> with
   * {@code fieldErrors} keyed by the request's fields.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PutMapping("/{structureId}/bridge-details")
  ResponseEntity<Void> updateBridgeDetails(
      @PathVariable("structureId") long structureId,
      @RequestBody BridgeDetailsRequest request);

  /**
   * Saves the fields of the Details tab's Load Rating Details.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, who change Load Rating
   * Review Required; the service refuses the other fields below Level 2, and the Other vehicle's
   * words to anyone but a P.Eng, as legacy's page. <b>204</b> on success; <b>404</b> when there is
   * no such structure; <b>400</b> with {@code fieldErrors} keyed by the request's fields.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PutMapping("/{structureId}/load-rating-details")
  ResponseEntity<Void> updateLoadRatingDetails(
      @PathVariable("structureId") long structureId,
      @RequestBody LoadRatingDetailsRequest request);

  /**
   * Adds a manual load rating — legacy's Add Load Rating.
   *
   * <p>Gated on {@link CbrAuthorities#APPROVE} — a P.Eng's, as legacy's {@code /pEngAccess}.
   * <b>201</b> with the new id; <b>404</b> when there is no such structure; <b>400</b> with
   * {@code fieldErrors} keyed {@code rating}, {@code reasonCode} or {@code reasonComment}.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.APPROVE)
  @PostMapping("/{structureId}/load-ratings")
  ResponseEntity<CreatedResponse> createLoadRating(
      @PathVariable("structureId") long structureId, @RequestBody LoadRatingRequest request);

  /**
   * Changes a manual load rating — legacy's Edit Manual Load Rating.
   *
   * <p>Gated on {@link CbrAuthorities#APPROVE}. <b>204</b> on success; <b>404</b> when the structure
   * has no such manual rating; <b>400</b> with {@code fieldErrors}.
   *
   * @param structureId  the {@code CROSSING_STRUCTURE_ID}
   * @param loadRatingId the rating's {@code STRUCTURE_LOAD_RATING_ID}
   */
  @PreAuthorize(CbrAuthorities.APPROVE)
  @PutMapping("/{structureId}/load-ratings/{loadRatingId}")
  ResponseEntity<Void> updateLoadRating(
      @PathVariable("structureId") long structureId,
      @PathVariable("loadRatingId") long loadRatingId,
      @RequestBody LoadRatingRequest request);

  /**
   * Deletes a manual load rating.
   *
   * <p>Gated on {@link CbrAuthorities#APPROVE}. <b>204</b> on success; <b>404</b> when the structure
   * has no such manual rating.
   *
   * @param structureId  the {@code CROSSING_STRUCTURE_ID}
   * @param loadRatingId the rating's {@code STRUCTURE_LOAD_RATING_ID}
   */
  @PreAuthorize(CbrAuthorities.APPROVE)
  @DeleteMapping("/{structureId}/load-ratings/{loadRatingId}")
  ResponseEntity<Void> deleteLoadRating(
      @PathVariable("structureId") long structureId,
      @PathVariable("loadRatingId") long loadRatingId);

  /**
   * Structures whose Structure # starts with the term — Replacement History's type-ahead. At most
   * 20, by name, archived ones included; none for fewer than 2 characters.
   *
   * <p>Gated on {@link CbrAuthorities#READ}: it reads what Structure Search shows anyone.
   *
   * @param term the start of a Structure #, any case
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/name-suggestions")
  List<StructureNameSuggestion> suggestNames(@RequestParam(name = "term") String term);

  /**
   * Records that this structure replaced another — legacy's Replacement History pop-up.
   *
   * <p>Gated on {@link CbrAuthorities#APPROVE} — a P.Eng's, as legacy. <b>201</b> on success;
   * <b>404</b> when there is no such structure; <b>400</b> with {@code fieldErrors} keyed
   * {@code replacedName}.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}, the replacing structure
   */
  @PreAuthorize(CbrAuthorities.APPROVE)
  @PostMapping("/{structureId}/replacements")
  ResponseEntity<Void> createReplacement(
      @PathVariable("structureId") long structureId, @RequestBody ReplacementRequest request);

  /**
   * Deletes that this structure replaced another.
   *
   * <p>Gated on {@link CbrAuthorities#APPROVE}. <b>204</b> on success; <b>404</b> when this
   * structure did not replace that one.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}, the replacing structure
   * @param replacedId  the replaced structure's id, {@code REPLACED_STRUCTURE_NUMBER}
   */
  @PreAuthorize(CbrAuthorities.APPROVE)
  @DeleteMapping("/{structureId}/replacements/{replacedId}")
  ResponseEntity<Void> deleteReplacement(
      @PathVariable("structureId") long structureId,
      @PathVariable("replacedId") long replacedId);

  /**
   * Saves the Details tab's Replacement Details.
   *
   * <p>Gated on {@link CbrAuthorities#APPROVE} — a P.Eng's, legacy's {@code /pEngAccess} on every
   * one of these fields. <b>204</b> on success; <b>404</b> when there is no such structure;
   * <b>400</b> with {@code fieldErrors} keyed by the request's fields.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.APPROVE)
  @PutMapping("/{structureId}/replacement-details")
  ResponseEntity<Void> updateReplacementDetails(
      @PathVariable("structureId") long structureId,
      @RequestBody ReplacementDetailsRequest request);

  /**
   * Saves the Details tab's Culvert card — the culvert's fields, with the structure's Portable
   * Structure.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — Level 2 and up, legacy's
   * {@code /level2Access} on every one of these fields. <b>204</b> on success; <b>404</b> when
   * there is no such structure; <b>409</b> when it has no culvert record; <b>400</b> with
   * {@code fieldErrors} keyed by the request's fields.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PutMapping("/{structureId}/culvert-details")
  ResponseEntity<Void> updateCulvertDetails(
      @PathVariable("structureId") long structureId,
      @RequestBody CulvertDetailsRequest request);

  /**
   * Adds a span to the structure's bridge — legacy's Add on the Spans &amp; Piers tab.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — the Level 2 floor, legacy's
   * {@code /level2Access} on the Add button; it deletes nothing. <b>201</b> with the new id;
   * <b>404</b> when there is no such structure; <b>409</b> when it has no bridge record; <b>400</b>
   * with {@code fieldErrors} keyed {@code number} or {@code lengthMetres} when a field is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PostMapping("/{structureId}/spans")
  ResponseEntity<CreatedResponse> createSpan(
      @PathVariable("structureId") long structureId, @RequestBody SpanRequest request);

  /**
   * Changes a span's number and length — legacy's Update on the Spans &amp; Piers tab.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE}, as legacy's Level 2 Update button. <b>204</b>
   * on success; <b>404</b> when the structure has no such span; <b>400</b> with {@code fieldErrors}
   * when a field is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   * @param spanId      the span's {@code FOREST_SERVICE_BRIDGE_SPAN_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PutMapping("/{structureId}/spans/{spanId}")
  ResponseEntity<Void> updateSpan(
      @PathVariable("structureId") long structureId,
      @PathVariable("spanId") long spanId,
      @RequestBody SpanRequest request);

  /**
   * Deletes a span — legacy's delete icon on the Spans &amp; Piers tab.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE}, as legacy's Level 2 icon. <b>204</b> on
   * success; <b>404</b> when the structure has no such span; <b>409</b> with legacy's sentence
   * when the span has been inspected, is the last, or the spans left would not outnumber the piers.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   * @param spanId      the span's {@code FOREST_SERVICE_BRIDGE_SPAN_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{structureId}/spans/{spanId}")
  ResponseEntity<Void> deleteSpan(
      @PathVariable("structureId") long structureId, @PathVariable("spanId") long spanId);

  /**
   * Adds a pier to the structure's bridge — legacy's Add on the Spans &amp; Piers tab.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE}, as legacy's Level 2 Add button. <b>201</b>
   * with the new id; <b>404</b> when there is no such structure; <b>409</b> when it has no bridge
   * record; <b>400</b> with {@code fieldErrors} keyed {@code number} or {@code typeCode}, or
   * {@code pier} when the bridge has too few spans for another pier.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PostMapping("/{structureId}/piers")
  ResponseEntity<CreatedResponse> createPier(
      @PathVariable("structureId") long structureId, @RequestBody PierRequest request);

  /**
   * Changes a pier's number and type — legacy's Update on the Spans &amp; Piers tab.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE}, as legacy's Level 2 Update button. <b>204</b>
   * on success; <b>404</b> when the structure has no such pier; <b>400</b> with {@code fieldErrors}
   * when a field is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   * @param pierId      the pier's {@code FOREST_SERVICE_BRIDGE_PIER_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PutMapping("/{structureId}/piers/{pierId}")
  ResponseEntity<Void> updatePier(
      @PathVariable("structureId") long structureId,
      @PathVariable("pierId") long pierId,
      @RequestBody PierRequest request);

  /**
   * Deletes a pier — legacy's delete icon on the Spans &amp; Piers tab.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE}, as legacy's Level 2 icon. <b>204</b> on
   * success; <b>404</b> when the structure has no such pier; <b>409</b> with legacy's sentence
   * when the pier has been inspected.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   * @param pierId      the pier's {@code FOREST_SERVICE_BRIDGE_PIER_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{structureId}/piers/{pierId}")
  ResponseEntity<Void> deletePier(
      @PathVariable("structureId") long structureId, @PathVariable("pierId") long pierId);

  /**
   * A structure's documents and photos — legacy's Documents &amp; Photos tab, fetched when that tab
   * opens. Details only; each file's bytes come from {@link #getDocumentFile}.
   *
   * <p>Gated on {@link CbrAuthorities#READ}, as the page is. <b>404</b> when there is no such
   * structure.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{structureId}/documents")
  StructureDocumentsResponse getDocuments(@PathVariable("structureId") long structureId);

  /**
   * One document's file. Images and PDFs are sent {@code inline} under their own type, for the
   * browser to show; anything else as {@code application/octet-stream} to download.
   *
   * <p>Gated on {@link CbrAuthorities#READ}. <b>404</b> when the structure has no such document.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID} the document belongs to
   * @param fileId      the document's {@code FILE_ID}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{structureId}/documents/{fileId}/file")
  ResponseEntity<byte[]> getDocumentFile(
      @PathVariable("structureId") long structureId, @PathVariable("fileId") long fileId);

  /**
   * Adds one document or photo to the structure — legacy's Add on the Documents &amp; Photos tab,
   * which the page calls once for each file chosen. Multipart: {@code file}, and {@code details}, a
   * JSON part holding its type, date and description.
   *
   * <p>One file a request so each request stays under the frontend WAF's body limit
   * ({@code frontend/coraza.conf}) and the backend holds one upload at a time.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — the Level 2 floor, legacy's
   * {@code /level2Access} on the Add button; it deletes nothing. <b>201</b> with the new id;
   * <b>404</b> when there is no such structure; <b>400</b> with {@code fieldErrors} keyed
   * {@code file}, {@code attachmentTypeCode}, {@code createdDate} or {@code description} when the
   * file or its details are refused — then nothing is stored.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PostMapping(value = "/{structureId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<CreatedResponse> addDocument(
      @PathVariable("structureId") long structureId,
      @RequestPart("file") MultipartFile file,
      @RequestPart("details") DocumentDetailsRequest details);

  /**
   * Changes a document's type and description — legacy's edit in its viewer.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, legacy's
   * {@code /level1Access} on the edit. <b>204</b> on success; <b>404</b> when the structure has no
   * such document; <b>400</b> with {@code fieldErrors} when a field is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   * @param fileId      the document's {@code FILE_ID}
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PutMapping("/{structureId}/documents/{fileId}")
  ResponseEntity<Void> updateDocument(
      @PathVariable("structureId") long structureId,
      @PathVariable("fileId") long fileId,
      @RequestBody DocumentUpdateRequest request);

  /**
   * Deletes a document and its file — legacy's delete icon on the Documents &amp; Photos tab.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — legacy's {@code /deleteStructureDocument},
   * Level 2. <b>204</b> on success; <b>404</b> when the structure has no such document.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   * @param fileId      the document's {@code FILE_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{structureId}/documents/{fileId}")
  ResponseEntity<Void> deleteDocument(
      @PathVariable("structureId") long structureId,
      @PathVariable("fileId") long fileId);

  /**
   * The top of legacy's Inspections tab: planned-inspection comments, the next planned
   * inspections and their frequency, and the completed close proximity inspections.
   *
   * <p>Gated on {@link CbrAuthorities#READ}. <b>404</b> when there is no such structure.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{structureId}/inspection-schedule")
  StructureInspectionScheduleResponse getInspectionSchedule(
      @PathVariable("structureId") long structureId);

  /**
   * Saves the structure's inspection schedule — the Inspections tab's Inspection Schedule card.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, as legacy's page Save; what a
   * Level 1 user may change within it (the frequency alone) the service decides. <b>204</b> on
   * success; <b>404</b> when there is no such structure; <b>400</b> with {@code fieldErrors} when a
   * field is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PutMapping("/{structureId}/inspection-schedule")
  ResponseEntity<Void> updateInspectionSchedule(
      @PathVariable("structureId") long structureId,
      @RequestBody InspectionScheduleRequest request);

  /**
   * Records a completed close proximity inspection — the Inspections tab's Completed Close
   * Proximity Inspections.
   *
   * <p>Gated on {@link CbrAuthorities#APPROVE} — P.Eng only, legacy's {@code /pEngAccess} on the
   * add. <b>201</b> with the new row's id; <b>404</b> when there is no such structure; <b>400</b>
   * with {@code fieldErrors} when there is no date; <b>409</b> when the structure stands on no
   * site.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.APPROVE)
  @PostMapping("/{structureId}/close-proximity-inspections")
  ResponseEntity<CreatedResponse> addCloseProximityInspection(
      @PathVariable("structureId") long structureId,
      @RequestBody CloseProximityInspectionRequest request);

  /**
   * Adds a general comment — the Details tab's Comments.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, legacy's
   * {@code /level1Access} on the add box. <b>201</b> with the new comment's id; <b>404</b> when
   * there is no such structure; <b>400</b> with {@code fieldErrors} when the text is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PostMapping("/{structureId}/comments")
  ResponseEntity<CreatedResponse> addComment(
      @PathVariable("structureId") long structureId,
      @RequestBody CommentRequest request);

  /**
   * Adds a planned-inspection comment — the Inspections tab's Planned Inspection Comments.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — the Level 2 floor, legacy's
   * {@code /level2Access} on the add box; it deletes nothing. <b>201</b> with the new comment's
   * id; <b>404</b> when there is no such structure; <b>400</b> with {@code fieldErrors} when the
   * text is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @PostMapping("/{structureId}/planned-inspection-comments")
  ResponseEntity<CreatedResponse> addPlannedInspectionComment(
      @PathVariable("structureId") long structureId,
      @RequestBody CommentRequest request);

  /**
   * Changes a comment's text — either kind, as legacy's edit pencil.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, legacy's
   * {@code /saveStructure}; as legacy, any comment, whoever wrote it. <b>204</b> on success;
   * <b>404</b> when the structure has no such comment; <b>400</b> with {@code fieldErrors} when the
   * text is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID} the comment belongs to
   * @param commentId   the comment's {@code STRUCTURE_COMMENT_ID}
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PutMapping("/{structureId}/comments/{commentId}")
  ResponseEntity<Void> updateComment(
      @PathVariable("structureId") long structureId,
      @PathVariable("commentId") long commentId,
      @RequestBody CommentRequest request);

  /**
   * A page of the structure's inspections, newest first — legacy's inspection table, paged here.
   * Those dated before the superstructure was installed only with {@code includeBeforeInstall}.
   *
   * <p>Gated on {@link CbrAuthorities#READ}. <b>404</b> when there is no such structure.
   *
   * @param structureId          the {@code CROSSING_STRUCTURE_ID}
   * @param pageNumber           zero-based
   * @param pageSize             held to between 1 and 100
   * @param includeBeforeInstall legacy's "Show Inspections before the Superstructure Install"
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{structureId}/inspections")
  StructureInspectionsResponse getInspections(
      @PathVariable("structureId") long structureId,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
      @RequestParam(name = "includeBeforeInstall", defaultValue = "false")
      boolean includeBeforeInstall);

  /**
   * A page of the structure's repairs — legacy's Repairs tab, paged here. Outstanding repairs by
   * default, as legacy opens; {@code view=ALL} for every one.
   *
   * <p>Gated on {@link CbrAuthorities#READ}. <b>404</b> when there is no such structure.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   * @param view        {@code OUTSTANDING}, {@code ALL}, or — from an inspection's page —
   *                    {@code CURRENT}: legacy's "Choose Viewing Option"
   * @param inspectionId the inspection whose page asks; needed for {@code CURRENT} (400 without)
   * @param pageNumber  zero-based
   * @param pageSize    held to between 1 and 100
   * @param includeBeforeInstall legacy's "Show Inspections before the Superstructure Install Date"
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{structureId}/repairs")
  StructureRepairsResponse.Listing getRepairs(
      @PathVariable("structureId") long structureId,
      @RequestParam(name = "view", defaultValue = "OUTSTANDING") StructureRepairsResponse.View view,
      @RequestParam(name = "inspectionId", required = false) Long inspectionId,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
      @RequestParam(name = "includeBeforeInstall", defaultValue = "false")
      boolean includeBeforeInstall);

  /**
   * The repair types that apply to the structure, each with its group — the Repair Item dialog's
   * Repair Type list, which its group checkboxes narrow.
   *
   * <p>Gated on {@link CbrAuthorities#READ}. <b>404</b> when there is no such structure.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{structureId}/repair-types")
  List<RepairTypeOption> getRepairTypes(@PathVariable("structureId") long structureId);

  /**
   * Adds a repair to the structure — legacy's Add Repair. Always Suggested; the structure's next
   * number.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, as legacy's button.
   * <b>201</b> with the new repair's id and number; <b>404</b> when there is no such structure;
   * <b>400</b> with {@code fieldErrors} when a field is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PostMapping("/{structureId}/repairs")
  ResponseEntity<RepairCreatedResponse> createRepair(
      @PathVariable("structureId") long structureId,
      @RequestBody RepairCreateRequest request);

  /**
   * Saves an edit to one repair — legacy's "Repair Item" dialog.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, as legacy offers the edit
   * link; legacy itself checked nothing on an edit. <b>204</b> on success; <b>404</b> when the
   * structure has no such repair; <b>400</b> with {@code fieldErrors} when a field is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID} the repair belongs to
   * @param repairId    the repair's {@code REPAIR_ID}
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PutMapping("/{structureId}/repairs/{repairId}")
  ResponseEntity<Void> updateRepair(
      @PathVariable("structureId") long structureId,
      @PathVariable("repairId") long repairId,
      @RequestBody RepairUpdateRequest request);

  /**
   * Deletes one repair — legacy's delete icon on the Repairs tab.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — legacy's {@code /deleteStructureRepair}.
   * <b>204</b> on success; <b>404</b> when the structure has no such repair.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID} the repair belongs to
   * @param repairId    the repair's {@code REPAIR_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{structureId}/repairs/{repairId}")
  ResponseEntity<Void> deleteRepair(
      @PathVariable("structureId") long structureId,
      @PathVariable("repairId") long repairId);

  /**
   * A page of the structure's monitoring items — legacy's Monitoring tab, paged here. Outstanding
   * items by default, as legacy opens; {@code view=ALL} for every one.
   *
   * <p>Gated on {@link CbrAuthorities#READ}. <b>404</b> when there is no such structure.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   * @param view        {@code OUTSTANDING}, {@code ALL}, or — from an inspection's page —
   *                    {@code CURRENT}: legacy's "Choose Viewing Option"
   * @param inspectionId the inspection whose page asks; needed for {@code CURRENT} (400 without)
   * @param pageNumber  zero-based
   * @param pageSize    held to between 1 and 100
   * @param includeBeforeInstall legacy's "Show Inspections before the Superstructure Install Date"
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{structureId}/monitors")
  StructureMonitorsResponse.Listing getMonitors(
      @PathVariable("structureId") long structureId,
      @RequestParam(name = "view", defaultValue = "OUTSTANDING")
      StructureMonitorsResponse.View view,
      @RequestParam(name = "inspectionId", required = false) Long inspectionId,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
      @RequestParam(name = "includeBeforeInstall", defaultValue = "false")
      boolean includeBeforeInstall);

  /**
   * Adds a monitoring item to the structure — legacy's Add Monitor. Always Suggested; the
   * structure's next number.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, as legacy's button.
   * <b>201</b> with the new item's id and number; <b>404</b> when there is no such structure;
   * <b>400</b> with {@code fieldErrors} when a field is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PostMapping("/{structureId}/monitors")
  ResponseEntity<MonitorCreatedResponse> createMonitor(
      @PathVariable("structureId") long structureId,
      @RequestBody MonitorCreateRequest request);

  /**
   * Saves an edit to one monitoring item — legacy's "Monitoring Item" dialog.
   *
   * <p>Gated on {@link CbrAuthorities#CONTENT_EDIT} — Level 1 and up, as legacy offers Add Monitor;
   * legacy itself checked nothing on an edit. <b>204</b> on success; <b>404</b> when the structure
   * has no such item; <b>400</b> with {@code fieldErrors} when a field is refused.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID} the item belongs to
   * @param monitorId   the item's {@code MONITOR_ID}
   */
  @PreAuthorize(CbrAuthorities.CONTENT_EDIT)
  @PutMapping("/{structureId}/monitors/{monitorId}")
  ResponseEntity<Void> updateMonitor(
      @PathVariable("structureId") long structureId,
      @PathVariable("monitorId") long monitorId,
      @RequestBody MonitorUpdateRequest request);

  /**
   * Deletes one monitoring item — legacy's delete icon on the Monitoring tab.
   *
   * <p>Gated on {@link CbrAuthorities#DESTRUCTIVE} — legacy's {@code /deleteStructureMonitor},
   * {@code REGIONAL_ENGINEER}. <b>204</b> on success; <b>404</b> when the structure has no such
   * item.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID} the item belongs to
   * @param monitorId   the item's {@code MONITOR_ID}
   */
  @PreAuthorize(CbrAuthorities.DESTRUCTIVE)
  @DeleteMapping("/{structureId}/monitors/{monitorId}")
  ResponseEntity<Void> deleteMonitor(
      @PathVariable("structureId") long structureId,
      @PathVariable("monitorId") long monitorId);
}
