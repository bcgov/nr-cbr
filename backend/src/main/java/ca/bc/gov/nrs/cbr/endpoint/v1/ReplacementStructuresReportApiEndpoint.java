package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.ReplacementStructuresCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The Reports menu's Replacement Structures Summary, beside {@link ReportApiEndpoint}'s reports,
 * under the same path. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.ReplacementStructuresReportApiController}.
 *
 * <p>Gated on {@link CbrAuthorities#READ}, as every report: legacy's {@code /show…Report}
 * privileges all sit at the GENERAL floor.
 */
@RequestMapping("/api/v1/reports")
public interface ReplacementStructuresReportApiEndpoint {

  /**
   * Replacement Structures Summary (legacy CBR009): the structures due a load restriction, a
   * replacement or a closure, by district and year, as a PDF in legacy's landscape layout or a
   * CSV of its rows, downloaded as an attachment.
   *
   * <p>{@code @Valid} turns a year that is not {@code yyyy} into a 400 naming the field.
   *
   * @param format {@code PDF} (the default) or {@code CSV}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/replacement-structures-summary")
  ResponseEntity<byte[]> replacementStructuresSummary(
      @Valid @ModelAttribute ReplacementStructuresCriteria criteria,
      @RequestParam(name = "format", defaultValue = "PDF") ReportFormat format);
}
