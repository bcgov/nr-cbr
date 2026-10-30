package ca.bc.gov.nrs.cbr.controller.v1;

import ca.bc.gov.nrs.cbr.endpoint.v1.ReportMapApiEndpoint;
import ca.bc.gov.nrs.cbr.service.v1.InspectionReportMaps;
import ca.bc.gov.nrs.cbr.service.v1.StructureInventoryDetailedService;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryDetailedCriteria;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reports' KMZs. Mappings and authorization are declared on {@link ReportMapApiEndpoint}; each
 * file goes out as {@link ReportApiController}'s do.
 */
@RestController
public class ReportMapApiController implements ReportMapApiEndpoint {

  private final StructureInventoryDetailedService detailed;
  private final InspectionReportMaps inspections;

  public ReportMapApiController(
      StructureInventoryDetailedService detailed, InspectionReportMaps inspections) {
    this.detailed = detailed;
    this.inspections = inspections;
  }

  @Override
  public ResponseEntity<byte[]> structureInventoryDetailedKmz(
      StructureInventoryDetailedCriteria criteria) {
    return ReportApiController.download(detailed.kmz(criteria));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsConductedSummaryKmz(
      InspectionsConductedCriteria criteria) {
    return ReportApiController.download(inspections.inspectionsConductedSummary(criteria));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsConductedDetailKmz(
      InspectionsConductedCriteria criteria) {
    return ReportApiController.download(inspections.inspectionsConductedDetail(criteria));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsDueSummaryKmz(InspectionsDueCriteria criteria) {
    return ReportApiController.download(inspections.inspectionsDueSummary(criteria));
  }

  @Override
  public ResponseEntity<byte[]> inspectionsDueDetailedKmz(InspectionsDueCriteria criteria) {
    return ReportApiController.download(inspections.inspectionsDueDetailed(criteria));
  }
}
