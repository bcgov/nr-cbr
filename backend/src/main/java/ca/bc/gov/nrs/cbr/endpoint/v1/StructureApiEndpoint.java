package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.PagedResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureArchiveResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse;
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
}
