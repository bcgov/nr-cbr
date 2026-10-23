package ca.bc.gov.nrs.cbr.endpoint.v1;

import ca.bc.gov.nrs.cbr.security.CbrAuthorities;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The Structure Inventory – Detailed report as a map, beside {@link ReportApiEndpoint}'s PDF and
 * CSV, under the same path. A mapping of its own, so the KMZ is not a format every other report
 * would then have to refuse. Implemented by
 * {@link ca.bc.gov.nrs.cbr.controller.v1.StructureInventoryMapApiController}.
 *
 * <p>Gated on {@link CbrAuthorities#READ}, as every report.
 */
@RequestMapping("/api/v1/reports")
public interface StructureInventoryMapApiEndpoint {

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
}
