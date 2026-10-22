package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The Reports menu's inspections-due reports — Inspections Due – Summary and – Detailed, and the
 * Inspections – Blank Report — beside {@link ReportApiEndpoint}'s, under the same path. Implemented
 * by {@link ca.bc.gov.nrs.cbr.controller.v1.InspectionsDueReportApiController}.
 *
 * <p>Gated on {@link CbrAuthorities#READ}, as every report: legacy's {@code /show…Report}
 * privileges all sit at the GENERAL floor.
 */
@RequestMapping("/api/v1/reports")
public interface InspectionsDueReportApiEndpoint {

  /**
   * Inspections Due – Summary (legacy CBR004): a line per structure under its forest district, as
   * a PDF in legacy's landscape layout or a CSV of its columns, downloaded as an attachment.
   *
   * <p>{@code @Valid} turns a Next Planned Inspection Date that is not {@code yyyy/mm/dd} into a 400
   * naming the field.
   *
   * @param format {@code PDF} (the default) or {@code CSV}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/inspections-due-summary")
  ResponseEntity<byte[]> inspectionsDueSummary(
      @Valid @ModelAttribute InspectionsDueCriteria criteria,
      @RequestParam(name = "format", defaultValue = "PDF") ReportFormat format);

  /**
   * Inspections Due – Detailed (legacy CBR003): a box per structure under its forest district, as
   * a PDF on legacy's portrait page or a CSV of its fields, downloaded as an attachment.
   *
   * <p>{@code @Valid} turns a Next Planned Inspection Date that is not {@code yyyy/mm/dd} into a 400
   * naming the field.
   *
   * @param format {@code PDF} (the default) or {@code CSV}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/inspections-due-detailed")
  ResponseEntity<byte[]> inspectionsDueDetailed(
      @Valid @ModelAttribute InspectionsDueCriteria criteria,
      @RequestParam(name = "format", defaultValue = "PDF") ReportFormat format);

  /**
   * Inspections – Blank Report (legacy CBR010): a blank routine inspection form per structure, as a
   * PDF, or a CSV of the structures the forms are for, downloaded as an attachment.
   *
   * <p>{@code @Valid} turns a Next Planned Inspection Date that is not {@code yyyy/mm/dd} into a 400
   * naming the field.
   *
   * @param format {@code PDF} (the default) or {@code CSV}
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/inspections-blank")
  ResponseEntity<byte[]> inspectionsBlank(
      @Valid @ModelAttribute InspectionsDueCriteria criteria,
      @RequestParam(name = "format", defaultValue = "PDF") ReportFormat format);
}
