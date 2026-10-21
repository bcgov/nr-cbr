package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.MonitorsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RepairsSummaryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The Reports menu — legacy's eleven reports, each produced as a file to download. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.ReportApiController}.
 *
 * <p>Every report is gated on {@link CbrAuthorities#READ}: legacy's {@code /show…Report} privileges
 * all sit at the GENERAL floor.
 */
@RequestMapping("/api/v1/reports")
public interface ReportApiEndpoint {

  /**
   * Structure Inventory – Summary (legacy CBR008), as a PDF in legacy's layout or a CSV of the same
   * columns, downloaded as an attachment.
   *
   * <p>{@code @Valid} turns a kilometre, a year or an opening width that is not one into a 400
   * naming the field. A GET, as the report changes nothing; the criteria are query parameters.
   *
   * @param format {@code PDF} (the default) or {@code CSV}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/structure-inventory-summary")
  ResponseEntity<byte[]> structureInventorySummary(
      @Valid @ModelAttribute StructureInventorySummaryCriteria criteria,
      @RequestParam(name = "format", defaultValue = "PDF") ReportFormat format);

  /**
   * Structure Inventory – Detailed (legacy CBR007), as a PDF in legacy's layout — a block of
   * labelled fields per structure — or a CSV of legacy's fuller export, downloaded as an
   * attachment. Its KMZ is still to come.
   *
   * <p>{@code @Valid} turns a kilometre or a year that is not one into a 400 naming the field.
   *
   * @param format {@code PDF} (the default) or {@code CSV}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/structure-inventory-detailed")
  ResponseEntity<byte[]> structureInventoryDetailed(
      @Valid @ModelAttribute StructureInventoryDetailedCriteria criteria,
      @RequestParam(name = "format", defaultValue = "PDF") ReportFormat format);

  /**
   * Structure Detail Report (legacy CBR006): the full record of one structure, as a PDF in
   * legacy's layout or a CSV of the same fields, downloaded as an attachment. A 404 when there is
   * no such structure, or it is archived.
   *
   * @param structureId the structure's {@code CROSSING_STRUCTURE_ID}
   * @param format      {@code PDF} (the default) or {@code CSV}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/structure-detail/{structureId}")
  ResponseEntity<byte[]> structureDetail(
      @PathVariable("structureId") long structureId,
      @RequestParam(name = "format", defaultValue = "PDF") ReportFormat format);

  /**
   * Repairs Summary (legacy CBR005): the repairs recorded against structures, a page per structure
   * grouped by inspection, as a PDF in legacy's layout or a CSV of legacy's "Excel" export,
   * downloaded as an attachment.
   *
   * @param format {@code PDF} (the default) or {@code CSV}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/repairs-summary")
  ResponseEntity<byte[]> repairsSummary(
      @Valid @ModelAttribute RepairsSummaryCriteria criteria,
      @RequestParam(name = "format", defaultValue = "PDF") ReportFormat format);

  /**
   * Monitors Summary (legacy CBR011): the monitoring items recorded against structures, a block
   * per structure grouped by inspection, as a PDF in legacy's layout or a CSV of what it prints,
   * downloaded as an attachment.
   *
   * @param format {@code PDF} (the default) or {@code CSV}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/monitors-summary")
  ResponseEntity<byte[]> monitorsSummary(
      @Valid @ModelAttribute MonitorsSummaryCriteria criteria,
      @RequestParam(name = "format", defaultValue = "PDF") ReportFormat format);
}
