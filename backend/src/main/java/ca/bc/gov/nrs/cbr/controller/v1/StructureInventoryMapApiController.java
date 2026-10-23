package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.StructureInventoryMapApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Structure Inventory – Detailed KMZ. Mapping and authorization are declared on
 * {@link StructureInventoryMapApiEndpoint}; the file goes out as {@link ReportApiController}'s do.
 */
@RestController
public class StructureInventoryMapApiController implements StructureInventoryMapApiEndpoint {

  private final StructureInventoryDetailedService detailed;

  public StructureInventoryMapApiController(StructureInventoryDetailedService detailed) {
    this.detailed = detailed;
  }

  @Override
  public ResponseEntity<byte[]> structureInventoryDetailedKmz(
      StructureInventoryDetailedCriteria criteria) {
    return ReportApiController.download(detailed.kmz(criteria));
  }
}
