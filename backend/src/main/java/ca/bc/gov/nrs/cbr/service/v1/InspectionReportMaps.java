package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inspection reports as maps: Inspections Conducted – Summary and – Detail, and Inspections
 * Due – Summary and – Detailed, each as the Structure Inventory – Detailed KMZ of the structures it
 * lists — "the same information as the Structure Inventory – Detailed Report", as asked. The
 * report's criteria choose the structures and their order; every placemark and its pop-up are the
 * Detailed KMZ's ({@link StructureInventoryDetailedService#kmzOf}).
 *
 * <p>Beyond legacy, which offered the KMZ on the Detailed report alone. A structure an
 * Inspections Conducted report lists for several inspections, or an Inspections Due report for
 * several bridge and culvert records, is one placemark.
 */
@Service
@Transactional(readOnly = true)
public class InspectionReportMaps {

  private final StructureInventoryDetailedService detailed;
  private final InspectionsConductedSummaryService conductedSummary;
  private final InspectionsConductedDetailService conductedDetail;
  private final InspectionsDueSummaryService dueSummary;
  private final InspectionsDueDetailedService dueDetailed;

  public InspectionReportMaps(
      StructureInventoryDetailedService detailed,
      InspectionsConductedSummaryService conductedSummary,
      InspectionsConductedDetailService conductedDetail,
      InspectionsDueSummaryService dueSummary,
      InspectionsDueDetailedService dueDetailed) {
    this.detailed = detailed;
    this.conductedSummary = conductedSummary;
    this.conductedDetail = conductedDetail;
    this.dueSummary = dueSummary;
    this.dueDetailed = dueDetailed;
  }

  /** Inspections Conducted – Summary as a KMZ. */
  public ReportFile inspectionsConductedSummary(InspectionsConductedCriteria criteria) {
    return detailed.kmzOf(conductedSummary.structureIds(criteria),
        "Inspections Conducted - Summary", InspectionsConductedSummaryService.FILENAME);
  }

  /** Inspections Conducted – Detail as a KMZ. */
  public ReportFile inspectionsConductedDetail(InspectionsConductedCriteria criteria) {
    return detailed.kmzOf(conductedDetail.structureIds(criteria),
        "Inspections Conducted - Detail", InspectionsConductedDetailService.FILENAME);
  }

  /** Inspections Due – Summary as a KMZ. */
  public ReportFile inspectionsDueSummary(InspectionsDueCriteria criteria) {
    return detailed.kmzOf(dueSummary.structureIds(criteria),
        "Inspections Due - Summary", InspectionsDueSummaryService.FILENAME);
  }

  /** Inspections Due – Detailed as a KMZ. */
  public ReportFile inspectionsDueDetailed(InspectionsDueCriteria criteria) {
    return detailed.kmzOf(dueDetailed.structureIds(criteria),
        "Inspections Due - Detailed", InspectionsDueDetailedService.FILENAME);
  }
}
