package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.blankIfNull;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.firstOf;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;

import ca.bc.gov.nrs.cbr.model.v1.StructureRepairClassCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairGroupCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairTypeXrefEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureBridgeDetail;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCulvertDetail;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairClassCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairGroupCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairTypeXrefRepository;
import ca.bc.gov.nrs.cbr.service.v1.RepairsSummaryNames.Names;
import ca.bc.gov.nrs.cbr.specification.v1.RepairsSummaryLine;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The Repairs Summary's CSV: legacy's "Excel" export, which {@code RepairSummaryReportAction} wrote
 * out by hand from the PDF's query — a row per repair, with its structure's bridge and culvert, and
 * its type's repair class and group.
 *
 * <p>Legacy's columns, its two misspelt headings corrected, always in the default sort's order —
 * legacy moved the first five with the sort. Its values written as the other CSVs write them: plain
 * numbers, descriptions, decimal latitude and longitude, and the client number without the
 * {@code ~} legacy put before it. Dates are {@code yyyy-MM-dd}, as legacy's were.
 *
 * <p>The rows are the PDF's, in its order, but for one thing kept from legacy: a repair whose type
 * is in more than one repair group for its structure's type/class — {@code STRUCTURE_REPAIR_TYPE_XREF}
 * — is a row per group, as legacy's join made it. Legacy also repeated every row for a second
 * culvert record; here a structure's first bridge and first culvert are read.
 */
@Component
public class RepairsSummaryCsv {

  /** The CSV's columns: legacy's, in its default order, its "Inpspection" corrected. */
  static final List<String> COLUMNS = List.of(
      "Forest File Id", "Branch", "Road Name", "KM User", "Site Id", "District", "KM",
      "Latitude", "Longitude", "Structure #", "Primary User Name", "Client Number",
      "Client Location", "Type", "Load Rating", "Total Length", "Width", "Deck Type",
      "Superstructure", "Abutments", "Last Inspection Date", "Next Inspection Date",
      "Opening Width", "Opening Height", "Inlet Fill", "Outlet Fill", "Culvert Material",
      "Substructure", "Inspection Date", "Repair Number", "Repair Status", "Repair Priority",
      "Estimate", "Repair Type", "Repair Type Code", "Repair Class", "Repair Group Code",
      "Repair Group", "Repair Qty", "Repair Units", "Repair Description");

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** A repair type in no group for its type/class: one row, its class and group blank. */
  private static final List<StructureRepairTypeXrefEntity.Key> NO_GROUP =
      Collections.singletonList(null);

  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final StructureInventoryNames clients;
  private final StructureRepairTypeXrefRepository xrefs;
  private final StructureRepairGroupCodeRepository groups;
  private final StructureRepairClassCodeRepository classes;

  public RepairsSummaryCsv(
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      StructureInventoryNames clients,
      StructureRepairTypeXrefRepository xrefs,
      StructureRepairGroupCodeRepository groups,
      StructureRepairClassCodeRepository classes) {
    this.bridges = bridges;
    this.culverts = culverts;
    this.clients = clients;
    this.xrefs = xrefs;
    this.groups = groups;
    this.classes = classes;
  }

  /**
   * The CSV's rows for these repairs, in their order, in {@link #COLUMNS} order.
   *
   * @param lastRoutine each structure's last routine inspection date, by structure id
   */
  List<List<String>> rows(
      List<RepairsSummaryLine> lines, Map<Long, LocalDate> lastRoutine, Names names) {
    List<Long> structureIds = lines.stream().map(RepairsSummaryLine::structureId).distinct().toList();
    Map<Long, StructureBridgeDetail> bridge = firstOf(structureIds, bridges::findDetails,
        StructureBridgeDetail::crossingStructureId, Function.identity());
    Map<Long, StructureCulvertDetail> culvert = firstOf(structureIds, culverts::findDetails,
        StructureCulvertDetail::crossingStructureId, Function.identity());
    Map<String, String> clientNames = clients.clientNames(
        lines.stream().map(RepairsSummaryLine::clientNumber).toList());
    Map<List<String>, List<StructureRepairTypeXrefEntity.Key>> groupsByType = xrefs.findAll()
        .stream()
        .map(StructureRepairTypeXrefEntity::getId)
        .sorted(Comparator
            .comparing(StructureRepairTypeXrefEntity.Key::getStructureRepairGroupCode,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StructureRepairTypeXrefEntity.Key::getStructureRepairClassCode,
                Comparator.nullsLast(Comparator.naturalOrder())))
        .collect(Collectors.groupingBy(key ->
            List.of(key.getStructureTypeClassCode(), key.getStructureRepairTypeCode())));
    Map<String, String> groupNames = byCode(groups.findAll(),
        StructureRepairGroupCodeEntity::getStructureRepairGroupCode,
        StructureRepairGroupCodeEntity::getDescription);
    Map<String, String> classNames = byCode(classes.findAll(),
        StructureRepairClassCodeEntity::getStructureRepairClassCode,
        StructureRepairClassCodeEntity::getDescription);

    return lines.stream().flatMap(line -> {
      List<StructureRepairTypeXrefEntity.Key> typeGroups = line.typeClassCode() == null
          || line.repairTypeCode() == null
          ? NO_GROUP
          : groupsByType.getOrDefault(
              List.of(line.typeClassCode(), line.repairTypeCode()), NO_GROUP);
      return typeGroups.stream().map(group -> row(line,
          new Context(bridge.get(line.structureId()), culvert.get(line.structureId()),
              clientNames.get(line.clientNumber()), lastRoutine.get(line.structureId()), names),
          group == null ? null : group.getStructureRepairGroupCode(),
          group == null ? null : classNames.get(group.getStructureRepairClassCode()),
          group == null ? null : groupNames.get(group.getStructureRepairGroupCode())));
    }).toList();
  }

  private static List<String> row(
      RepairsSummaryLine line, Context context, String groupCode, String className,
      String groupName) {
    StructureBridgeDetail bridge = context.bridge();
    StructureCulvertDetail culvert = context.culvert();
    Names names = context.names();
    return List.of(
        blankIfNull(line.forestFileId()),
        blankIfNull(line.roadSectionId()),
        blankIfNull(line.roadSectionName()),
        plain(line.userKm()),
        blankIfNull(line.siteId()),
        blankIfNull(line.districtName()),
        plain(line.kilometres()),
        plain(line.latitude()),
        plain(line.longitude()),
        blankIfNull(line.structureName()),
        blankIfNull(context.clientName()),
        blankIfNull(line.clientNumber()),
        blankIfNull(line.clientLocationCode()),
        blankIfNull(line.typeClassCode()),
        plain(line.currentLoadRating()),
        bridge == null ? "" : plain(bridge.totalLength()),
        bridge == null ? "" : plain(bridge.deckWidth()),
        bridge == null ? "" : blankIfNull(bridge.deckType()),
        bridge == null ? "" : blankIfNull(bridge.superstructureType()),
        bridge == null ? "" : blankIfNull(bridge.leftAbutment()),
        date(context.lastRoutine()),
        date(line.nextPlannedInspectionDate()),
        culvert == null ? "" : plain(culvert.openingWidth()),
        culvert == null ? "" : plain(culvert.openingHeight()),
        culvert == null ? "" : plain(culvert.inletCoverDepth()),
        culvert == null ? "" : plain(culvert.outletCoverDepth()),
        culvert == null ? "" : blankIfNull(culvert.culvertMaterial()),
        culvert == null ? "" : blankIfNull(culvert.openBottomSubstructure()),
        date(line.inspectionDate()),
        plain(line.repairNumber()),
        blankIfNull(names.statuses().get(line.repairStatusCode())),
        blankIfNull(names.priorities().get(line.repairPriorityCode())),
        plain(line.estimate()),
        blankIfNull(names.types().get(line.repairTypeCode())),
        blankIfNull(line.repairTypeCode()),
        blankIfNull(className),
        blankIfNull(groupCode),
        blankIfNull(groupName),
        plain(line.repairQuantity()),
        blankIfNull(names.units().get(line.repairTypeCode())),
        blankIfNull(line.description()));
  }

  private static String date(LocalDate value) {
    return value == null ? "" : value.format(DATE);
  }

  private static <E> Map<String, String> byCode(
      List<E> rows, Function<E, String> code, Function<E, String> name) {
    Map<String, String> names = new HashMap<>();
    rows.forEach(row -> names.put(code.apply(row), name.apply(row)));
    return names;
  }

  /** What a row reads beside its repair: the structure's bridge, culvert, maintainer and dates. */
  private record Context(
      StructureBridgeDetail bridge,
      StructureCulvertDetail culvert,
      String clientName,
      LocalDate lastRoutine,
      Names names) {}
}
