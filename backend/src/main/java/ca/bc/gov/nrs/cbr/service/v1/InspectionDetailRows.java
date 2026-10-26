package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.firstOf;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.format;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.formatDate;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;

import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureBridgeDetail;
import ca.bc.gov.nrs.cbr.repository.v1.StructureCulvertDetail;
import ca.bc.gov.nrs.cbr.repository.v1.StructureValue;
import ca.bc.gov.nrs.cbr.service.v1.InspectionReportRecords.Records;
import ca.bc.gov.nrs.cbr.specification.v1.InspectionsConductedReportSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsConductedSort;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The rows the Inspections Conducted – Detail (legacy CBR001) fills its template with: an
 * inspection each, every value as legacy's template printed it, keyed by the template's field
 * names — with the inspection's items, repairs and monitoring items as lists for its subreports;
 * and the rows of its CSV ({@link InspectionDetailCsv}), read the same way.
 *
 * <p>Which inspections, in what order, is one query
 * ({@link InspectionsConductedReportSpecifications#detailIds}); the inspections, their structures
 * and sites are then read whole, and everything else a batch at a time: the first bridge and
 * culvert, the span lengths, the items ({@link InspectionReportItems}) and the rest
 * ({@link InspectionReportRecords}).
 *
 * <p>Where it differs from legacy: Curb Type is filled (legacy printed its label alone); a hazard
 * not answered prints blank, not "No"; and Report Status names who set the status and when — the
 * reviewer and the review date once there is one, else whoever recorded the current status —
 * where legacy named the inspection's last editor.
 */
@Component
@Transactional(readOnly = true)
public class InspectionDetailRows {

  private static final String YES = "Y";
  private static final String NO = "N";
  private static final DateTimeFormatter STATUS_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

  /** Legacy's templates patterns. */
  private static final String KILOMETRES = "###0.00";
  private static final String ONE_PLACE = "###0.0";
  private static final String WHOLE = "#,##0";

  /** Legacy's title and report number by structure type/class. */
  private static final Map<String, String[]> TITLES = Map.of(
      "PB", new String[] {"Permanent Bridge Inspection Report", "CBR001A"},
      "TB", new String[] {"Temporary Bridge Inspection Report", "CBR001B"},
      "CUL", new String[] {"Major Culvert Inspection Report", "CBR001C"},
      "WLC", new String[] {"Woodbox/Log Culvert Inspection Report", "CBR001D"});

  private final EntityManager entityManager;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final InspectionReportItems items;
  private final InspectionReportRecords records;
  private final ConfigurationService codes;

  public InspectionDetailRows(
      EntityManager entityManager,
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      InspectionReportItems items,
      InspectionReportRecords records,
      ConfigurationService codes) {
    this.entityManager = entityManager;
    this.bridges = bridges;
    this.culverts = culverts;
    this.items = items;
    this.records = records;
    this.codes = codes;
  }

  /** The report's rows, an inspection each, in its order. */
  public List<Map<String, Object>> rows(
      InspectionsConductedCriteria criteria, InspectionsConductedSort sort) {
    return rows(load(ids(criteria, sort)));
  }

  /** One inspection's row, as the report would print it — for its audit record. */
  public List<Map<String, Object>> rows(long inspectionId) {
    return rows(load(List.of(inspectionId)));
  }

  private List<Map<String, Object>> rows(Loaded loaded) {
    Map<Long, List<Map<String, String>>> itemLines = items.lines(loaded.ids());
    Records other = loaded.records();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Long id : loaded.ids()) {
      StructureInspectionEntity inspection = loaded.inspections().get(id);
      Long structureId = inspection.getCrossingStructureId();
      Map<String, Object> row = new HashMap<>();
      header(inspection, loaded.codes().types(), row);
      CrossingSiteEntity site = inspection.getStructure().getSite();
      site(site, row);
      structure(inspection.getStructure(), row);
      if (loaded.bridges().containsKey(structureId)) {
        bridge(loaded.bridges().get(structureId), loaded.spanLengths().get(structureId), row);
      }
      if (loaded.culverts().containsKey(structureId)) {
        culvert(loaded.culverts().get(structureId), row);
      }
      inspection(inspection, row);
      review(inspection, other, loaded.codes().statuses(), row);
      row.put("ITEMS", itemLines.getOrDefault(id, List.of()));
      row.put("REPAIRS", other.repairs().getOrDefault(id, List.of()));
      row.put("MONITORS", other.monitors().getOrDefault(id, List.of()));
      rows.add(row);
    }
    return rows;
  }

  /** The CSV's rows, in {@link InspectionDetailCsv#COLUMNS} order: a row per item, as it writes them. */
  public List<List<String>> csvRows(
      InspectionsConductedCriteria criteria, InspectionsConductedSort sort) {
    Loaded loaded = load(ids(criteria, sort));
    return InspectionDetailCsv.rows(loaded, items.answers(loaded.ids()));
  }

  /** The inspections for these criteria, in order. */
  private List<Long> ids(InspectionsConductedCriteria criteria, InspectionsConductedSort sort) {
    return entityManager.createQuery(InspectionsConductedReportSpecifications.detailIds(
        entityManager.getCriteriaBuilder(), criteria, sort)).getResultList();
  }

  /** These inspections, in this order, and everything read beside them. */
  private Loaded load(List<Long> ids) {
    if (ids.isEmpty()) {
      return new Loaded(List.of(), Map.of(), Map.of(), Map.of(), Map.of(),
          new Records(Map.of(), Map.of(), Map.of(), Map.of(), Map.of()),
          new Codes(Map.of(), Map.of()));
    }
    Map<Long, StructureInspectionEntity> inspections = inspections(ids);
    List<Long> structureIds = inspections.values().stream()
        .map(StructureInspectionEntity::getCrossingStructureId).distinct().toList();
    return new Loaded(ids, inspections,
        firstOf(structureIds, bridges::findDetails,
            StructureBridgeDetail::crossingStructureId, Function.identity()),
        firstOf(structureIds, culverts::findDetails,
            StructureCulvertDetail::crossingStructureId, Function.identity()),
        spanLengths(structureIds),
        records.of(ids, inspections.values().stream()
            .map(StructureInspectionEntity::getInspectionReviewerId).toList()),
        new Codes(byCode(codes.getInspectionTypeCodes()),
            byCode(codes.getInspectionReportStatusCodes())));
  }

  /**
   * The inspections, in the report's order, and what is read beside them: each structure's first
   * bridge and first culvert, its span lengths, the inspections' records and the codes' names.
   */
  record Loaded(
      List<Long> ids,
      Map<Long, StructureInspectionEntity> inspections,
      Map<Long, StructureBridgeDetail> bridges,
      Map<Long, StructureCulvertDetail> culverts,
      Map<Long, String> spanLengths,
      Records records,
      Codes codes) {}

  /** The inspection types' and report statuses' names, by code. */
  record Codes(Map<String, String> types, Map<String, String> statuses) {}

  /** The inspections, with their structures and sites, a batch at a time. */
  private Map<Long, StructureInspectionEntity> inspections(List<Long> ids) {
    Map<Long, StructureInspectionEntity> found = new HashMap<>();
    for (int from = 0; from < ids.size(); from += StructureInventoryReportSupport.BATCH) {
      entityManager.createQuery("""
              SELECT inspection
                FROM StructureInspectionEntity inspection
                JOIN FETCH inspection.structure structure
                JOIN FETCH structure.site site
                LEFT JOIN FETCH site.roadSection
                LEFT JOIN FETCH site.orgUnit
               WHERE inspection.inspectionId IN :ids
              """, StructureInspectionEntity.class)
          .setParameter("ids",
              ids.subList(from, Math.min(ids.size(), from + StructureInventoryReportSupport.BATCH)))
          .getResultList()
          .forEach(inspection -> found.put(inspection.getInspectionId(), inspection));
    }
    return found;
  }

  private Map<Long, String> spanLengths(List<Long> structureIds) {
    Map<Long, List<BigDecimal>> lengths = new HashMap<>();
    for (int from = 0; from < structureIds.size(); from += StructureInventoryReportSupport.BATCH) {
      for (StructureValue<BigDecimal> span : bridges.findSpanLengths(structureIds.subList(from,
          Math.min(structureIds.size(), from + StructureInventoryReportSupport.BATCH)))) {
        lengths.computeIfAbsent(span.crossingStructureId(), id -> new ArrayList<>())
            .add(span.value());
      }
    }
    Map<Long, String> joined = new HashMap<>();
    lengths.forEach((id, list) -> joined.put(id,
        list.stream().map(StructureInventoryReportSupport::plain).collect(Collectors.joining(", "))));
    return joined;
  }

  private static void header(
      StructureInspectionEntity inspection, Map<String, String> types, Map<String, Object> row) {
    String[] title = TITLES.get(inspection.getStructure().getStructureTypeClassCode());
    row.put("INSPECTION_ID", inspection.getInspectionId().toString());
    row.put("REPORT_TITLE", title == null ? "" : title[0]);
    row.put("REPORT_ID", title == null ? "" : title[1]);
    row.put("CROSSING_STRUCTURE_NAME", inspection.getStructure().getCrossingStructureName());
    row.put("INSPECTION_DATE", formatDate(inspection.getInspectionDate()));
    row.put("INSPECTION_TYPE", types.get(inspection.getStrctreInspectionTypeCode()));
  }

  private static void site(CrossingSiteEntity site, Map<String, Object> row) {
    row.put("CROSSING_SITE_ID", site.getCrossingSiteId());
    row.put("ROAD_SECT_NAME", site.getRoadSection() == null ? null : site.getRoadSection().getRoadSectName());
    row.put("DISTRICT", site.getOrgUnit() == null ? null : site.getOrgUnit().getOrgUnitName());
    row.put("KM", decimal(site.getPointOfCommencementDistance(), KILOMETRES));
    row.put("USER_KM", decimal(site.getUserKm(), KILOMETRES));
    row.put("CROSSING_NAME", site.getCrossingName());
    row.put("FOREST_FILE_ID", site.getForestFileId());
    row.put("ROAD_SECTION_ID", site.getRoadSectionId());
    row.put("UTM_ZONE", site.getUtmZone() == null ? null : site.getUtmZone().toString());
    row.put("UTM_EASTING", plain(site.getUtmEasting()));
    row.put("UTM_NORTHING", plain(site.getUtmNorthing()));
    row.put("SITE_DETAILS", site.getPointOfAccessDesc());
  }

  private static void structure(CrossingStructureEntity structure, Map<String, Object> row) {
    row.put("NEXT_INSPECTION_DATE", formatDate(structure.getNextPlannedInspectionDate()));
    row.put("YEAR_BUILT",
        structure.getYearBuilt() == null ? null : structure.getYearBuilt().toString());
    row.put("DESIGN_LOAD", decimal(structure.getDesignLoadRating(), WHOLE));
    row.put("DESIGN_VEHICLE", designVehicle(structure.getDesignVehicleLoadCode()));
    row.put("LOAD_RESTRICTION_YEAR", year(structure.getFullLogHaulReplacementYear()));
    row.put("REPLACEMENT_YEAR", year(structure.getLightVehicleReplacementYear()));
  }

  private static void bridge(
      StructureBridgeDetail bridge, String spanLengths, Map<String, Object> row) {
    row.put("BRIDGE", YES);
    row.put("DECK_TYPE", bridge.deckType());
    row.put("RUNNING_SURFACE", bridge.runningSurface());
    row.put("CURB_TYPE", bridge.curbType());
    row.put("SUPERSTRUCTURE", bridge.superstructureType());
    row.put("LEFT_ABUTMENT", bridge.leftAbutment());
    row.put("RIGHT_ABUTMENT", bridge.rightAbutment());
    row.put("TOTAL_LENGTH", decimal(bridge.totalLength(), ONE_PLACE));
    row.put("SPAN_COUNT", bridge.spanCount() == null ? null : bridge.spanCount().toString());
    row.put("SPAN_LENGTHS", spanLengths);
  }

  private static void culvert(StructureCulvertDetail culvert, Map<String, Object> row) {
    row.put("CULVERT", YES);
    row.put("CULVERT_TYPE", culvert.culvertType());
    row.put("CULVERT_MATERIAL", culvert.culvertMaterial());
    row.put("INLET_COVER", whole(culvert.inletCoverDepth()));
    row.put("OUTLET_COVER", whole(culvert.outletCoverDepth()));
    row.put("FOOTING_TYPE", culvert.openBottomSubstructure());
    row.put("GRADIENT", decimal(culvert.slope(), ONE_PLACE));
    row.put("CULVERT_LENGTH", decimal(culvert.culvertLength(), ONE_PLACE));
    row.put("OPENING_HEIGHT", whole(culvert.openingHeight()));
    row.put("OPENING_WIDTH", whole(culvert.openingWidth()));
  }

  private static void inspection(StructureInspectionEntity inspection, Map<String, Object> row) {
    row.put("INSPECTOR_NAME", inspection.getInspectorName());
    row.put("PWL_DEPTH", decimal(inspection.getChannelPwlDepth(), ONE_PLACE));
    row.put("PWL_WIDTH", decimal(inspection.getChannelPwlWidth(), ONE_PLACE));
    row.put("HWL_CLEARANCE", decimal(inspection.getChannelHwlClearance(), ONE_PLACE));
    row.put("HZ_HIGH_WATER", hazard(inspection.getHighWaterHazardInd()));
    row.put("HZ_SCOUR", hazard(inspection.getScourHazardInd()));
    row.put("HZ_ICE", hazard(inspection.getIceHazardInd()));
    row.put("HZ_DEBRIS", hazard(inspection.getDebrisHazardInd()));
    row.put("HZ_AGGRADATION", hazard(inspection.getAggradationHazardInd()));
    row.put("HAZARD_COMMENT", inspection.getHazardComment());
    row.put("INSPECTION_COMMENT", inspection.getInspectionComment());
    row.put("PENG_COMMENT", StringUtils.hasText(inspection.getPengComment())
        ? inspection.getPengComment()
        : null);
    row.put("PENG_REQUIRED", YES.equalsIgnoreCase(inspection.getPengInspectionReqInd()) ? "Yes" : "No");
  }

  private static void review(
      StructureInspectionEntity inspection, Records other, Map<String, String> statuses,
      Map<String, Object> row) {
    Long id = inspection.getInspectionId();
    BigDecimal rating = other.loadRatings().get(id);
    row.put("LOAD_RATING", rating == null ? "See footnote" : format(rating, "###0"));
    InspectionReportStatusEntity status = other.status(id);
    String reviewer = other.reviewers().get(inspection.getInspectionReviewerId());
    row.put("REPORT_STATUS", status(status, statuses, reviewer, inspection.getPengReviewerDate()));
  }

  /**
   * The current status, and who set it when: the reviewer and the review date once there is one,
   * else whoever recorded the status row — legacy named the inspection's last editor instead.
   */
  static String status(
      InspectionReportStatusEntity status, Map<String, String> names, String reviewer,
      LocalDateTime reviewed) {
    StatusSet set = statusSet(status, names, reviewer, reviewed);
    if (set == null) {
      return "";
    }
    String text = set.name();
    if (StringUtils.hasText(set.by())) {
      text += " By " + set.by();
    }
    if (set.on() != null) {
      text += " on " + set.on().format(STATUS_TIME);
    }
    return text;
  }

  /** The current status's name, and who set it when — as {@link #status} words it; null for none. */
  static StatusSet statusSet(
      InspectionReportStatusEntity status, Map<String, String> names, String reviewer,
      LocalDateTime reviewed) {
    if (status == null) {
      return null;
    }
    boolean byReviewer = StringUtils.hasText(reviewer);
    return new StatusSet(
        Objects.toString(names.get(status.getInspectionReportStatusCode()),
            status.getInspectionReportStatusCode()),
        byReviewer ? reviewer : status.getEntryUserid(),
        byReviewer ? reviewed : status.getEntryTimestamp());
  }

  /** A report status, who set it and when. */
  record StatusSet(String name, String by, LocalDateTime on) {}

  /** The design vehicle as legacy printed it: its code, OTHER as "Other"; blank for none. */
  static String designVehicle(String code) {
    return "OTHER".equalsIgnoreCase(code) ? "Other" : Objects.toString(code, "");
  }

  /** The structure's type as the report's title names it — "Permanent Bridge"; blank for none. */
  static String structureType(String typeClassCode) {
    String[] title = TITLES.get(typeClassCode);
    return title == null ? "" : title[0].replace(" Inspection Report", "");
  }

  private static String hazard(String indicator) {
    if (YES.equalsIgnoreCase(indicator)) {
      return "Yes";
    }
    return NO.equalsIgnoreCase(indicator) ? "No" : "";
  }

  private static String decimal(BigDecimal value, String pattern) {
    return value == null ? null : format(value, pattern);
  }

  private static String whole(Long value) {
    return value == null ? null : format(BigDecimal.valueOf(value), WHOLE);
  }

  private static String year(Integer value) {
    return value == null ? null : value.toString();
  }

  private static Map<String, String> byCode(List<CodeOptionResponse> options) {
    Map<String, String> names = new HashMap<>();
    options.forEach(option -> names.put(option.code(), option.description()));
    return names;
  }
}
