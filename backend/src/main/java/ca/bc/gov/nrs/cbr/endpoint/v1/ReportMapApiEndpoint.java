package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The reports as maps — Structure Inventory – Detailed, and the inspection reports as its KMZ of
 * the structures they list — beside their PDFs and CSVs, under the same path. Mappings of their
 * own, so the KMZ is not a format every other report would then have to refuse. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.ReportMapApiController}.
 *
 * <p>Gated on {@link CbrAuthorities#READ}, as every report.
 */
@RequestMapping("/api/v1/reports")
public interface ReportMapApiEndpoint {

  /**
   * Structure Inventory – Detailed (legacy CBR007) as a KMZ: a placemark per structure with a
   * latitude and longitude, for Google Earth or a GIS, downloaded as an attachment.
   *
   * <p>{@code @Valid} turns a kilometre or a year that is not one into a 400 naming the field.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/structure-inventory-detailed/kmz")
  ResponseEntity<byte[]> structureInventoryDetailedKmz(
      @Valid @ModelAttribute StructureInventoryDetailedCriteria criteria);

  /**
   * Inspections Conducted – Summary's structures as the Structure Inventory – Detailed KMZ.
   *
   * <p>{@code @Valid} turns an Inspection Date that is not a month into a 400 naming the field.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/inspections-conducted-summary/kmz")
  ResponseEntity<byte[]> inspectionsConductedSummaryKmz(
      @Valid @ModelAttribute InspectionsConductedCriteria criteria);

  /**
   * Inspections Conducted – Detail's structures as the Structure Inventory – Detailed KMZ.
   *
   * <p>{@code @Valid} turns an Inspection Date that is not a month into a 400 naming the field.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/inspections-conducted-detail/kmz")
  ResponseEntity<byte[]> inspectionsConductedDetailKmz(
      @Valid @ModelAttribute InspectionsConductedCriteria criteria);

  /**
   * Inspections Due – Summary's structures as the Structure Inventory – Detailed KMZ.
   *
   * <p>{@code @Valid} turns a Next Planned Inspection Date that is not {@code yyyy/mm/dd} into a 400
   * naming the field.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/inspections-due-summary/kmz")
  ResponseEntity<byte[]> inspectionsDueSummaryKmz(
      @Valid @ModelAttribute InspectionsDueCriteria criteria);

  /**
   * Inspections Due – Detailed's structures as the Structure Inventory – Detailed KMZ.
   *
   * <p>{@code @Valid} turns a Next Planned Inspection Date that is not {@code yyyy/mm/dd} into a 400
   * naming the field.
   */
  @PreAuthorize(CbrAuthorities.READ)
  @GetMapping("/inspections-due-detailed/kmz")
  ResponseEntity<byte[]> inspectionsDueDetailedKmz(
      @Valid @ModelAttribute InspectionsDueCriteria criteria);
}
