package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDocumentsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionScheduleResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInspectionsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureMonitorsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairsResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureRepairResponsibilityResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSearchResult;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSortColumn;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
   * @param view        {@code OUTSTANDING} or {@code ALL} — legacy's "Choose Viewing Option"
   * @param pageNumber  zero-based
   * @param pageSize    held to between 1 and 100
   * @param includeBeforeInstall legacy's "Show Inspections before the Superstructure Install Date"
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/{structureId}/repairs")
  StructureRepairsResponse.Listing getRepairs(
      @PathVariable("structureId") long structureId,
      @RequestParam(name = "view", defaultValue = "OUTSTANDING") StructureRepairsResponse.View view,
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
      @RequestParam(name = "includeBeforeInstall", defaultValue = "false")
      boolean includeBeforeInstall);

  /**
   * A page of the structure's monitoring items — legacy's Monitoring tab, paged here. Outstanding
   * items by default, as legacy opens; {@code view=ALL} for every one.
   *
   * <p>Gated on {@link CbrAuthorities#READ}. <b>404</b> when there is no such structure.
   *
   * @param structureId the {@code CROSSING_STRUCTURE_ID}
   * @param view        {@code OUTSTANDING} or {@code ALL} — legacy's "Choose Viewing Option"
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
      @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
      @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
      @RequestParam(name = "includeBeforeInstall", defaultValue = "false")
      boolean includeBeforeInstall);

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
