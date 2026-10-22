package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.blankIfNull;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.yesNo;

import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.StructureBridgeDetail;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCulvertDetail;
import ca.bc.gov.nrs.cbr.service.v1.InspectionDetailRows.Loaded;
import ca.bc.gov.nrs.cbr.service.v1.InspectionDetailRows.StatusSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Inspections Conducted – Detail's CSV. Legacy had none — its Excel was the PDF's layout — so
 * this is the PDF's content as data: a row per inspection item (subsection, span, pier, stringer,
 * question, answer and comment), each with every field of its inspection the PDF prints, and the
 * inspection's repairs and monitoring items as text. An inspection with no items is one row, its
 * item columns blank.
 *
 * <p>The rows are the PDF's inspections, in its order, each inspection's items in the order its
 * PDF lists them — but a stringer's questions are rows of their own, not folded into one. Values
 * are written as the other CSVs write them: plain numbers, {@code yyyy-MM-dd} dates, Yes/No, and a
 * blank where the PDF says "See footnote". The report status is split into what it is, who set it
 * and when; Year Superstructure Installed is a bridge's only, as on the PDF.
 */
final class InspectionDetailCsv {

  /** The CSV's columns: the PDF's labels, top to bottom, then the item's. */
  static final List<String> COLUMNS = List.of(
      "Site #", "Structure #", "Structure Type", "Road Name", "Inspection Date", "Inspection Type",
      "KM", "User KM", "Crossing Name", "Next Inspection Date", "Project File ID #", "Br.",
      "Forest District",
      "Deck Type", "Running Surface", "Curb Type", "Superstructure", "Left Abutment",
      "Right Abutment", "Total Length (metres)", "# Spans", "Span Length(s) (metres)",
      "Culvert Type", "Culvert Material", "Inlet Cover (mm)", "Outlet Cover (mm)", "Footing Type",
      "Gradient (%)", "Culvert Length (metres)", "Opening Height (mm)", "Opening Width (mm)",
      "UTM Zone", "UTM Easting", "UTM Northing", "Inspector Name", "Year Superstructure Installed",
      "Site Details",
      "Estimated Present Water Level Depth", "Estimated Present Water Level Width",
      "Estimated High Water Level Clearance",
      "High Water", "Scour", "Ice", "Debris", "Aggradation", "Hazard Comment",
      "Repairs", "Monitors", "Inspector's Comment", "PEng's Comment",
      "Design Load GVW (tonnes)", "Design Vehicle Config.",
      "Current Inspection Load Rating (tonnes)", "Professional Inspection Required?",
      "Estimated Load Restriction (year)", "Estimated Replacement (year)",
      "Report Status", "Status Set By", "Status Set On",
      "Subsection", "Span", "Pier", "Stringer", "Question", "Answer", "Item Comment");

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /** Between the parts of a repair or monitoring item; each item is a line of its own. */
  private static final String PART = " - ";

  /** An inspection with no items: one row, its item columns blank. */
  private static final List<List<String>> NO_ITEMS =
      Collections.singletonList(Collections.nCopies(7, ""));

  private InspectionDetailCsv() {}

  /**
   * The rows of these inspections, in their order.
   *
   * @param answers each inspection's items, as {@link InspectionReportItems#answers} gives them
   */
  static List<List<String>> rows(Loaded loaded, Map<Long, List<List<String>>> answers) {
    List<List<String>> rows = new ArrayList<>();
    for (Long id : loaded.ids()) {
      List<String> inspection = inspection(loaded, loaded.inspections().get(id));
      List<List<String>> items = answers.getOrDefault(id, List.of());
      for (List<String> item : items.isEmpty() ? NO_ITEMS : items) {
        rows.add(Stream.concat(inspection.stream(), item.stream()).toList());
      }
    }
    return rows;
  }

  /** The inspection's columns: everything before the item's. */
  private static List<String> inspection(Loaded loaded, StructureInspectionEntity inspection) {
    Long id = inspection.getInspectionId();
    CrossingStructureEntity structure = inspection.getStructure();
    CrossingSiteEntity site = structure.getSite();
    StructureBridgeDetail bridge = loaded.bridges().get(structure.getCrossingStructureId());
    StructureCulvertDetail culvert = loaded.culverts().get(structure.getCrossingStructureId());
    InspectionReportRecords.Records records = loaded.records();
    StatusSet status = InspectionDetailRows.statusSet(records.status(id),
        loaded.codes().statuses(), records.reviewers().get(inspection.getInspectionReviewerId()),
        inspection.getPengReviewerDate());
    List<String> row = new ArrayList<>(List.of(
        blankIfNull(site.getCrossingSiteId()),
        blankIfNull(structure.getCrossingStructureName()),
        blankIfNull(InspectionDetailRows.structureType(structure.getStructureTypeClassCode())),
        site.getRoadSection() == null ? "" : blankIfNull(site.getRoadSection().getRoadSectName()),
        date(inspection.getInspectionDate()),
        blankIfNull(loaded.codes().types().get(inspection.getStrctreInspectionTypeCode())),
        plain(site.getPointOfCommencementDistance()),
        plain(site.getUserKm()),
        blankIfNull(site.getCrossingName()),
        date(structure.getNextPlannedInspectionDate()),
        blankIfNull(site.getForestFileId()),
        blankIfNull(site.getRoadSectionId()),
        site.getOrgUnit() == null ? "" : blankIfNull(site.getOrgUnit().getOrgUnitName())));
    row.addAll(bridge == null ? Collections.nCopies(9, "") : List.of(
        blankIfNull(bridge.deckType()),
        blankIfNull(bridge.runningSurface()),
        blankIfNull(bridge.curbType()),
        blankIfNull(bridge.superstructureType()),
        blankIfNull(bridge.leftAbutment()),
        blankIfNull(bridge.rightAbutment()),
        plain(bridge.totalLength()),
        text(bridge.spanCount()),
        blankIfNull(loaded.spanLengths().get(structure.getCrossingStructureId()))));
    row.addAll(culvert == null ? Collections.nCopies(9, "") : List.of(
        blankIfNull(culvert.culvertType()),
        blankIfNull(culvert.culvertMaterial()),
        plain(culvert.inletCoverDepth()),
        plain(culvert.outletCoverDepth()),
        blankIfNull(culvert.openBottomSubstructure()),
        plain(culvert.slope()),
        plain(culvert.culvertLength()),
        plain(culvert.openingHeight()),
        plain(culvert.openingWidth())));
    row.addAll(List.of(
        text(site.getUtmZone()),
        text(site.getUtmEasting()),
        text(site.getUtmNorthing()),
        blankIfNull(inspection.getInspectorName()),
        bridge == null ? "" : text(structure.getYearBuilt()),
        blankIfNull(site.getPointOfAccessDesc()),
        plain(inspection.getChannelPwlDepth()),
        plain(inspection.getChannelPwlWidth()),
        plain(inspection.getChannelHwlClearance()),
        yesNo(inspection.getHighWaterHazardInd()),
        yesNo(inspection.getScourHazardInd()),
        yesNo(inspection.getIceHazardInd()),
        yesNo(inspection.getDebrisHazardInd()),
        yesNo(inspection.getAggradationHazardInd()),
        blankIfNull(inspection.getHazardComment()),
        lines(records.repairs().get(id), "NUMBER", "STATUS", "PRIORITY", "TYPE", "DESCRIPTION"),
        lines(records.monitors().get(id), "NUMBER", "DESCRIPTION"),
        blankIfNull(inspection.getInspectionComment()),
        blankIfNull(inspection.getPengComment()),
        plain(structure.getDesignLoadRating()),
        InspectionDetailRows.designVehicle(structure.getDesignVehicleLoadCode()),
        plain(records.loadRatings().get(id)),
        yesNo(inspection.getPengInspectionReqInd()),
        text(structure.getFullLogHaulReplacementYear()),
        text(structure.getLightVehicleReplacementYear()),
        status == null ? "" : blankIfNull(status.name()),
        status == null ? "" : blankIfNull(status.by()),
        status == null ? "" : time(status.on())));
    return row;
  }

  /** Repairs or monitoring items as text: a line each, its parts joined, blanks left out. */
  private static String lines(List<Map<String, String>> records, String... parts) {
    if (records == null) {
      return "";
    }
    return records.stream()
        .map(fields -> Stream.of(parts).map(fields::get)
            .filter(part -> part != null && !part.isBlank())
            .collect(Collectors.joining(PART)))
        .collect(Collectors.joining("\n"));
  }

  private static String text(Object value) {
    return Objects.toString(value, "");
  }

  private static String date(LocalDate value) {
    return value == null ? "" : value.format(DATE);
  }

  private static String time(LocalDateTime value) {
    return value == null ? "" : value.format(TIME);
  }
}
