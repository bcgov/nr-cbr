package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.BATCH;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.blankIfNull;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.firstOf;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.format;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.formatDate;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;

import ca.bc.gov.nrs.cbr.model.v1.StructureCommentEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureBridgeDetail;
import ca.bc.gov.nrs.cbr.repository.v1.StructureBridgeLength;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCommentRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureValue;
import ca.bc.gov.nrs.cbr.specification.v1.InspectionsDueLine;
import ca.bc.gov.nrs.cbr.specification.v1.InspectionsDueReportSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rows the Inspections Due – Summary (legacy CBR004) and – Detailed (legacy CBR003) fill their
 * templates with, every value as legacy's templates printed it, keyed by their field names.
 *
 * <p>Which structures, in what order, is one query ({@link InspectionsDueReportSpecifications});
 * what legacy's {@code INSPCTNS_DUE_SUMMARY} and {@code INSPCTNS_DUE_DETAILED} selected beside
 * each is then read a batch at a time: the last reviewed routine inspection, the bridges and
 * culverts, and the planned-inspection comments. A structure is a row per bridge record and
 * culvert record it has, as legacy's left joins made it — its Total Length the bridge's, else the
 * culvert's, and # Piers the bridge's.
 *
 * <p>Their CSVs are the PDFs' columns, after the district the PDFs group by, with their values
 * written as the other CSVs write them: plain numbers, {@code yyyy-MM-dd} dates, the type/class
 * code, and decimal latitude and longitude.
 *
 * <p>Where it differs from legacy: a comment's date is {@code yyyy-MM-dd}, where legacy printed
 * Oracle's default timestamp.
 */
@Component
@Transactional(readOnly = true)
public class InspectionsDueRows {

  /** Legacy's template patterns. */
  private static final String KILOMETRES = "###0.00";
  private static final String USER_KM = "#,##0.00";
  private static final String ONE_PLACE = "###0.0";
  private static final String WHOLE = "###0";
  /** The Detailed's: Total Length and # Spans grouped by thousands. */
  private static final String DETAILED_LENGTH = "#,##0.0";
  private static final String SPANS = "#,##0";

  /** Planned-inspection comments: {@code PLANNED_INSPECTION_CMT_IND = 'Y'}. */
  private static final String PLANNED = "Y";

  private final EntityManager entityManager;
  private final StructureInspectionRepository inspections;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final StructureCommentRepository comments;
  private final ConfigurationService codes;

  public InspectionsDueRows(
      EntityManager entityManager,
      StructureInspectionRepository inspections,
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      StructureCommentRepository comments,
      ConfigurationService codes) {
    this.entityManager = entityManager;
    this.inspections = inspections;
    this.bridges = bridges;
    this.culverts = culverts;
    this.comments = comments;
    this.codes = codes;
  }

  /** The Summary's CSV columns: the PDF's, in its order, after the district it is grouped by. */
  static final List<String> SUMMARY_COLUMNS = List.of(
      "Forest District", "Site #", "Structure #", "Last Inspection Date", "Next Inspection Date",
      "Type/Class", "Project File ID#", "Br.", "Total Length (metres)", "# Piers", "Road Name",
      "KM", "User KM", "Crossing Name", "Special Equipment Required", "Special Access Required",
      "Planned Inspection Comment");

  /** The Detailed's CSV columns: the PDF's box, in its order, after the district. */
  static final List<String> DETAILED_COLUMNS = List.of(
      "Forest District", "Structure #", "Site #", "Year Superstructure Installed", "Type/Class",
      "KM", "User km", "Last Inspection Date", "Next Inspection Date", "Project File ID #", "Br.",
      "Road Name", "Crossing Name", "Total Length (metres)", "Special Access",
      "Special Equipment", "Longitude", "Latitude", "Superstructure", "# Spans",
      "Abutments (Left/Right)", "Planned Inspection Comments");

  private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** The Summary's rows, in order: a structure each, or one per bridge and culvert record. */
  public List<Map<String, String>> summary(InspectionsDueCriteria criteria) {
    Loaded<StructureBridgeLength> loaded = loadSummary(criteria);
    return loaded.entries().stream().map(entry -> {
      InspectionsDueLine line = entry.line();
      StructureBridgeLength bridge = entry.bridge();
      Map<String, String> row = common(line, loaded.beside());
      row.put("POINT_OF_COMMENCEMENT_DISTANCE", decimal(line.kilometres(), KILOMETRES));
      row.put("USER_KM", decimal(line.userKm(), USER_KM));
      row.put("TOTAL_LENGTH", decimal(entry.totalLength(), ONE_PLACE));
      // Legacy counted the piers of no bridge too: 0.
      row.put("PIER_COUNT", format(BigDecimal.valueOf(piers(bridge)), WHOLE));
      return row;
    }).toList();
  }

  /** The Summary's CSV rows, the PDF's, in {@link #SUMMARY_COLUMNS} order. */
  public List<List<String>> summaryCsv(InspectionsDueCriteria criteria) {
    Loaded<StructureBridgeLength> loaded = loadSummary(criteria);
    Beside beside = loaded.beside();
    return loaded.entries().stream().map(entry -> {
      InspectionsDueLine line = entry.line();
      Long id = line.structureId();
      return List.of(
          blankIfNull(line.districtName()),
          blankIfNull(line.siteId()),
          blankIfNull(line.structureName()),
          date(beside.lastInspected().get(id)),
          date(line.nextPlannedInspectionDate()),
          blankIfNull(line.structureTypeClassCode()),
          blankIfNull(line.forestFileId()),
          blankIfNull(line.roadSectionId()),
          plain(entry.totalLength()),
          String.valueOf(piers(entry.bridge())),
          blankIfNull(line.roadSectionName()),
          plain(line.kilometres()),
          plain(line.userKm()),
          blankIfNull(line.crossingName()),
          blankIfNull(beside.equipment().get(line.specialEquipmentCode())),
          blankIfNull(beside.access().get(line.specialAccessCode())),
          blankIfNull(beside.comments().get(id)));
    }).toList();
  }

  /**
   * The Detailed's rows, in order: a structure each, or one per bridge and culvert record, as the
   * Summary's. What only a bridge has — the year its superstructure went in, the superstructure,
   * the abutments and the spans — is blank without one, where legacy printed 0 spans.
   */
  public List<Map<String, String>> detailed(InspectionsDueCriteria criteria) {
    Loaded<StructureBridgeDetail> loaded = loadDetailed(criteria);
    List<Map<String, String>> rows = new ArrayList<>();
    for (Entry<StructureBridgeDetail> entry : loaded.entries()) {
      InspectionsDueLine line = entry.line();
      StructureBridgeDetail bridge = entry.bridge();
      Map<String, String> row = common(line, loaded.beside());
      row.put("POINT_OF_COMMENCEMENT_DISTANCE", decimal(line.kilometres(), USER_KM));
      row.put("USER_KM", decimal(line.userKm(), USER_KM));
      row.put("STRUCTURE_TYPE", loaded.beside().types().get(line.structureTypeClassCode()));
      row.put("LATITUDE", RepairsSummaryService.degreesMinutesSeconds(line.latitude()));
      row.put("LONGITUDE", RepairsSummaryService.degreesMinutesSeconds(line.longitude()));
      row.put("TOTAL_LENGTH", decimal(entry.totalLength(), DETAILED_LENGTH));
      if (bridge != null) {
        row.put("BRIDGE", "Y");
        row.put("YEAR_BUILT", line.yearBuilt() == null ? null : line.yearBuilt().toString());
        row.put("SUPERSTRUCTURE", bridge.superstructureType());
        row.put("ABUTMENTS", abutments(bridge.leftAbutment(), bridge.rightAbutment()));
        row.put("SPAN_COUNT", format(BigDecimal.valueOf(bridge.spanCount()), SPANS));
      }
      // Each printed box its own group, kept whole on a page.
      row.put("ROW_KEY", String.valueOf(rows.size()));
      rows.add(row);
    }
    return rows;
  }

  /** The Detailed's CSV rows, the PDF's boxes, in {@link #DETAILED_COLUMNS} order. */
  public List<List<String>> detailedCsv(InspectionsDueCriteria criteria) {
    Loaded<StructureBridgeDetail> loaded = loadDetailed(criteria);
    Beside beside = loaded.beside();
    return loaded.entries().stream().map(entry -> {
      InspectionsDueLine line = entry.line();
      StructureBridgeDetail bridge = entry.bridge();
      Long id = line.structureId();
      return List.of(
          blankIfNull(line.districtName()),
          blankIfNull(line.structureName()),
          blankIfNull(line.siteId()),
          bridge == null || line.yearBuilt() == null ? "" : line.yearBuilt().toString(),
          blankIfNull(line.structureTypeClassCode()),
          plain(line.kilometres()),
          plain(line.userKm()),
          date(beside.lastInspected().get(id)),
          date(line.nextPlannedInspectionDate()),
          blankIfNull(line.forestFileId()),
          blankIfNull(line.roadSectionId()),
          blankIfNull(line.roadSectionName()),
          blankIfNull(line.crossingName()),
          plain(entry.totalLength()),
          blankIfNull(beside.access().get(line.specialAccessCode())),
          blankIfNull(beside.equipment().get(line.specialEquipmentCode())),
          plain(line.longitude()),
          plain(line.latitude()),
          bridge == null ? "" : blankIfNull(bridge.superstructureType()),
          bridge == null ? "" : plain(bridge.spanCount()),
          bridge == null ? "" : blankIfNull(abutments(bridge.leftAbutment(), bridge.rightAbutment())),
          blankIfNull(beside.comments().get(id)));
    }).toList();
  }

  private Loaded<StructureBridgeLength> loadSummary(InspectionsDueCriteria criteria) {
    List<InspectionsDueLine> lines = entityManager
        .createQuery(InspectionsDueReportSpecifications.lines(
            entityManager.getCriteriaBuilder(), criteria))
        .getResultList();
    Beside beside = beside(lines);
    return new Loaded<>(expand(lines, grouped(beside.ids(), bridges::findLengthsAndPiers,
        StructureBridgeLength::crossingStructureId), beside, StructureBridgeLength::totalLength),
        beside);
  }

  private Loaded<StructureBridgeDetail> loadDetailed(InspectionsDueCriteria criteria) {
    List<InspectionsDueLine> lines = entityManager
        .createQuery(InspectionsDueReportSpecifications.detailedLines(
            entityManager.getCriteriaBuilder(), criteria))
        .getResultList();
    Beside beside = beside(lines);
    return new Loaded<>(expand(lines, grouped(beside.ids(), bridges::findDetails,
        StructureBridgeDetail::crossingStructureId), beside, StructureBridgeDetail::totalLength),
        beside);
  }

  /**
   * A row per structure for each bridge record and each culvert record it has, as legacy's left
   * joins made it; its Total Length the bridge's, or with no bridge, the culvert's.
   */
  private static <B> List<Entry<B>> expand(
      List<InspectionsDueLine> lines, Map<Long, List<B>> bridgeRows, Beside beside,
      Function<B, BigDecimal> bridgeLength) {
    List<Entry<B>> entries = new ArrayList<>();
    for (InspectionsDueLine line : lines) {
      Long id = line.structureId();
      for (B bridge : orNone(bridgeRows.get(id))) {
        for (StructureValue<BigDecimal> culvert : orNone(beside.culvertLengths().get(id))) {
          BigDecimal length = bridge == null ? culvertLength(culvert) : bridgeLength.apply(bridge);
          entries.add(new Entry<>(line, bridge, length));
        }
      }
    }
    return entries;
  }

  /** What both reports read beside the structures listed. */
  private Beside beside(List<InspectionsDueLine> lines) {
    List<Long> ids = lines.stream().map(InspectionsDueLine::structureId).distinct().toList();
    return new Beside(ids,
        firstOf(ids, inspections::findLastReviewedRoutineInspectionDates),
        grouped(ids, culverts::findLengths, StructureValue::crossingStructureId),
        plannedComments(ids),
        new Names(byCode(codes.getSpecialEquipmentCodes()), byCode(codes.getSpecialAccessCodes()),
            byCode(codes.getStructureTypeClassCodes())));
  }

  /** The fields both reports print alike. */
  private static Map<String, String> common(InspectionsDueLine line, Beside beside) {
    Long id = line.structureId();
    Map<String, String> row = new HashMap<>();
    row.put("CROSSING_STRUCTURE_NAME", line.structureName());
    row.put("CROSSING_SITE_ID", line.siteId());
    row.put("ROAD_SECT_NAME", line.roadSectionName());
    row.put("CROSSING_NAME", line.crossingName());
    row.put("LAST_INSPECTION_DATE", formatDate(beside.lastInspected().get(id)));
    row.put("NEXT_PLANNED_INSPECTION_DATE", formatDate(line.nextPlannedInspectionDate()));
    row.put("FOREST_FILE_ID", line.forestFileId());
    row.put("ROAD_SECTION_ID", line.roadSectionId());
    row.put("STRUCTURE_TYPE_CLASS_CODE", line.structureTypeClassCode());
    row.put("FOREST_DISTRICT", line.districtName());
    row.put("FOREST_DISTRICT_KEY",
        line.districtOrgUnitNo() == null ? "" : line.districtOrgUnitNo().toString());
    row.put("SPECIAL_EQUIPMENT_RQMT_CODE", beside.equipment().get(line.specialEquipmentCode()));
    row.put("SPECIAL_ACCESS_RQMT_CODE", beside.access().get(line.specialAccessCode()));
    row.put("PLN_INSP_CMT", beside.comments().get(id));
    return row;
  }

  /**
   * Legacy's Abutments (Left/Right): the left, then the right after a "/" unless it is the same —
   * "Concrete" for both alike, "Concrete/Timber crib" for two.
   */
  static String abutments(String left, String right) {
    if (left == null || left.isBlank()) {
      return right;
    }
    if (right == null || right.isBlank() || right.equals(left)) {
      return left;
    }
    return left + "/" + right;
  }

  private static long piers(StructureBridgeLength bridge) {
    return bridge == null ? 0 : bridge.pierCount();
  }

  private static BigDecimal culvertLength(StructureValue<BigDecimal> culvert) {
    return culvert == null ? null : culvert.value();
  }

  private static String date(LocalDate value) {
    return value == null ? "" : value.format(CSV_DATE);
  }

  /** One printed row: the structure, the bridge record it is for if any, and its Total Length. */
  private record Entry<B>(InspectionsDueLine line, B bridge, BigDecimal totalLength) {}

  /** A report's rows before they are formatted, and what was read beside them. */
  private record Loaded<B>(List<Entry<B>> entries, Beside beside) {}

  /** The requirement and type/class codes' names, by code. */
  private record Names(
      Map<String, String> equipment, Map<String, String> access, Map<String, String> types) {}

  /**
   * What both reports read beside the structures: each one's last reviewed routine inspection, its
   * culverts' lengths and its planned-inspection comments, and the codes' names.
   */
  private record Beside(
      List<Long> ids,
      Map<Long, LocalDate> lastInspected,
      Map<Long, List<StructureValue<BigDecimal>>> culvertLengths,
      Map<Long, String> comments,
      Names names) {

    Map<String, String> equipment() {
      return names.equipment();
    }

    Map<String, String> access() {
      return names.access();
    }

    Map<String, String> types() {
      return names.types();
    }
  }

  /** Each structure's planned-inspection comments, newest first, one to a line. */
  private Map<Long, String> plannedComments(List<Long> ids) {
    Map<Long, List<StructureCommentEntity>> byStructure = grouped(ids,
        batch -> comments.findByKindIn(batch, PLANNED), StructureCommentEntity::getCrossingStructureId);
    Map<Long, String> joined = new HashMap<>();
    byStructure.forEach((id, list) -> joined.put(id, list.stream()
        .map(StructureDetailRows::comment)
        .collect(Collectors.joining("\n"))));
    return joined;
  }

  /** A lookup's rows by structure, in the order it returns them, a batch at a time. */
  private static <R> Map<Long, List<R>> grouped(
      List<Long> ids, Function<Collection<Long>, List<R>> lookup, ToLongFunction<R> structureId) {
    Map<Long, List<R>> grouped = new LinkedHashMap<>();
    for (int from = 0; from < ids.size(); from += BATCH) {
      for (R found : lookup.apply(ids.subList(from, Math.min(ids.size(), from + BATCH)))) {
        grouped.computeIfAbsent(structureId.applyAsLong(found), id -> new ArrayList<>()).add(found);
      }
    }
    return grouped;
  }

  /** The records, or a single none — a left join's one row for a structure without any. */
  private static <R> List<R> orNone(List<R> records) {
    return records == null || records.isEmpty() ? Collections.singletonList(null) : records;
  }

  private static String decimal(BigDecimal value, String pattern) {
    return value == null ? null : format(value, pattern);
  }

  private static Map<String, String> byCode(List<CodeOptionResponse> options) {
    Map<String, String> names = new HashMap<>();
    options.forEach(option -> names.put(option.code(), option.description()));
    return names;
  }
}
