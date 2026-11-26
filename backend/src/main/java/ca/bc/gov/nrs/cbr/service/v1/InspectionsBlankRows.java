package ca.bc.gov.nrs.cbr.service.v1;

import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.BATCH;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.blankIfNull;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.format;
import static ca.bc.gov.nrs.cbr.service.v1.StructureInventoryReportSupport.plain;

import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureMonitorItemEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureRepairEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ReportQueryHints;
import ca.bc.gov.nrs.cbr.repository.v1.StructureBridgeDetail;
import ca.bc.gov.nrs.cbr.repository.v1.StructureMonitorItemRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureRepairRepository;
import ca.bc.gov.nrs.cbr.service.v1.RepairsSummaryNames.Names;
import ca.bc.gov.nrs.cbr.specification.v1.InspectionsDueReportSpecifications;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Part;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Question;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Scope;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Subsection;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionsDueCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionKey;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.jpa.HibernateHints;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The Inspections – Blank Report's rows (legacy CBR010): a blank routine inspection form per
 * structure, as the lines its template prints, in order — each tagged with the band that prints it
 * ({@code KIND}), and carrying its structure's header fields, so every page heads itself.
 *
 * <p>The questions are the inspection form's ({@link InspectionFormService}) — the groups the
 * structure's type/class is inspected under, each group's questions matched to the structure, and
 * each question's answers in order — where legacy re-ran its own copy of that selection once per
 * group per span. Legacy's sections and their order otherwise: general information; the
 * structure's own groups (approaches, abutments, a culvert's items); then each span's deck and
 * superstructure; channel, hazards, outstanding repairs and monitoring items, the photo log, and
 * the inspector's and the professional engineer's sections.
 *
 * <p>Where it differs from legacy:
 * <ul>
 *   <li>Each pier follows the span before it — Span 1, Pier 1, Span 2 — where legacy printed it
 *       after the next span.</li>
 *   <li>A bridge with no spans recorded gets its span groups once, without "Span of 0"; with no
 *       superstructure type, it gets the Superstructure group, where legacy printed neither.</li>
 *   <li>A stringer's own questions (peeled, diameter, rot depths, species) are the stringer
 *       table's columns only, not also condition rows to circle.</li>
 *   <li>Every answer a question takes is printed — legacy dropped any after the fifth — and each
 *       section's lines are kept together on a page where they fit.</li>
 *   <li>Latitude and longitude are truncated, not rounded; outstanding repairs and monitoring items
 *       are in number order; the PEng section shows the structure's current load rating.</li>
 * </ul>
 */
@Component
@Transactional(readOnly = true)
public class InspectionsBlankRows {

  /** Outstanding repairs and monitoring items: Required and Suggested. */
  private static final Set<String> OUTSTANDING = Set.of("REQ", "SUG");
  private static final Set<String> BRIDGES = Set.of("PB", "TB");
  private static final Set<String> STRINGER_SUPERSTRUCTURES = Set.of("LGST", "TT", "TU");
  /** Deck questions asked of the approach to the structure, noted under the question. */
  private static final Set<String> APPROACH_QUESTIONS = Set.of("DML", "DMR");
  /** An answer legacy's blank form never offered: Unrated. */
  private static final String UNRATED = "UNR";
  /** Answer codes as the form prints them. Legacy's {@code CONVERT_CODE}. */
  private static final Map<String, String> CHOICE_TEXT = Map.of(
      "NA", "N/A", "ZERO", "0", "ONE", "1", "TWO", "2", "THREE", "3");
  private static final String RATING_LEGEND =
      "E = excellent; G = good; F = fair; P = poor; N/A = not appl.";
  /** Section titles as legacy's form printed them, by group; any other its name in capitals. */
  private static final Map<String, String> TITLES = Map.of(
      "APP", "LEFT/RIGHT APPROACHES (as seen facing downstream)",
      "ABUT", "LEFT/RIGHT ABUTMENTS",
      "DECK", "DECK",
      "SS", "SUPERSTRUCTURE",
      "STR", "SUPERSTRUCTURE",
      "PIER", "PIER",
      "CE", "GENERAL CULVERT ITEMS",
      "WDC", "GENERAL CULVERT ITEMS");
  private static final String WHOLE = "#,##0";
  private static final String KILOMETRES = "#,##0.00";
  private static final String MONEY = "#,##0.00";

  /** Bands: what each line of the form is. */
  enum Kind {
    GENERAL, SECTION, COLUMNS, PART, DETAIL, QUESTION, STRINGERS, LOG_STRINGERS, CHANNEL, HAZARDS,
    REPAIRS, REPAIR, REPAIR_BLANK, MONITORS, MONITOR, MONITOR_BLANK, PHOTO_LOG, INSPECTOR, PENG
  }

  private final EntityManager entityManager;
  private final InspectionFormService forms;
  private final ForestServiceBridgeRepository forestServiceBridges;
  private final StructureRepairRepository repairs;
  private final StructureMonitorItemRepository monitors;
  private final RepairsSummaryNames repairNames;
  private final ConfigurationService codes;
  private final RoadNameService roadNames;

  public InspectionsBlankRows(
      EntityManager entityManager,
      InspectionFormService forms,
      ForestServiceBridgeRepository forestServiceBridges,
      StructureRepairRepository repairs,
      StructureMonitorItemRepository monitors,
      RepairsSummaryNames repairNames,
      ConfigurationService codes,
      RoadNameService roadNames) {
    this.entityManager = entityManager;
    this.forms = forms;
    this.forestServiceBridges = forestServiceBridges;
    this.repairs = repairs;
    this.monitors = monitors;
    this.repairNames = repairNames;
    this.codes = codes;
    this.roadNames = roadNames;
  }

  /** The CSV's columns: the structure the form is for and where it is, then the form's item. */
  static final List<String> CSV_COLUMNS = List.of(
      "Structure #", "Site #", "Type/Class", "Road Name", "Project File ID", "Branch", "User km",
      "Latitude", "Longitude", "UTM Zone", "UTM Easting", "UTM Northing",
      "Current Load Rating (tonnes)", "Span/Pier", "Section", "Section Detail", "Item", "Choices");

  /**
   * The CSV: a row per item on each structure's form, in the PDF's order — the structure's fields
   * repeated on each, then the span or pier, the section and what it is, the item and the answers
   * it takes — values as the other CSVs write them. A form with no items is still a row, so every
   * structure is listed.
   */
  public List<List<String>> csvRows(InspectionsDueCriteria criteria) {
    List<List<String>> rows = new ArrayList<>();
    csvRows(criteria, rows::add);
    return rows;
  }

  /**
   * The CSV, as {@link #csvRows} has it, written to {@code csv} as it is made: the structures a
   * batch at a time, the session cleared after each, so a province's forms — a quarter of a
   * million rows — are never all in memory. The road names are looked up once, for every
   * structure, beforehand.
   */
  public void writeCsv(InspectionsDueCriteria criteria, CsvStream csv) {
    csvRows(criteria, csv::row);
  }

  private void csvRows(InspectionsDueCriteria criteria, Consumer<List<String>> out) {
    List<Long> ids = entityManager.createQuery(InspectionsDueReportSpecifications.blankIds(
        entityManager.getCriteriaBuilder(), criteria))
        .setHint(HibernateHints.HINT_FETCH_SIZE, ReportQueryHints.FETCH_ROWS)
        .getResultList();
    Shared shared = shared(ids);
    for (int from = 0; from < ids.size(); from += BATCH) {
      List<Long> batch = ids.subList(from, Math.min(ids.size(), from + BATCH));
      Map<Long, CrossingStructureEntity> structures = structures(batch);
      Beside beside = beside(batch, structures, shared);
      for (Long id : batch) {
        CrossingStructureEntity structure = structures.get(id);
        List<String> fields = structureFields(structure, beside.roads());
        List<List<String>> items = items(new Form(structure, beside).lines());
        if (items.isEmpty()) {
          items = List.of(List.of("", "", "", "", ""));
        }
        for (List<String> item : items) {
          List<String> row = new ArrayList<>(fields);
          row.addAll(item);
          out.accept(row);
        }
      }
      // What this batch loaded is written; let it go before the next.
      entityManager.clear();
    }
  }

  /**
   * LRMOPS's road names for these structures' sites: their distinct road sections read in one
   * small query a batch at a time, then looked up together.
   */
  private Function<CrossingSiteEntity, String> roadsOf(List<Long> structureIds) {
    List<RoadSectionKey> sections = new ArrayList<>();
    for (int from = 0; from < structureIds.size(); from += BATCH) {
      entityManager.createQuery("""
              SELECT DISTINCT site.forestFileId, site.roadSectionId
                FROM CrossingStructureEntity structure
                JOIN structure.site site
               WHERE structure.crossingStructureId IN :ids
              """, Object[].class)
          .setParameter("ids",
              structureIds.subList(from, Math.min(structureIds.size(), from + BATCH)))
          .getResultList()
          .forEach(pair -> sections.add(RoadSectionKey.of((String) pair[0], (String) pair[1])));
    }
    Map<RoadSectionKey, String> names = roadNames.namesOf(sections);
    return site -> {
      RoadSectionKey key = RoadSectionKey.of(site.getForestFileId(), site.getRoadSectionId());
      return key == null ? null : names.get(key);
    };
  }

  private static List<String> structureFields(
      CrossingStructureEntity structure, Function<CrossingSiteEntity, String> roads) {
    CrossingSiteEntity site = structure.getSite();
    return List.of(
        blankIfNull(structure.getCrossingStructureName()),
        blankIfNull(structure.getCrossingSiteId()),
        blankIfNull(structure.getStructureTypeClassCode()),
        blankIfNull(roads.apply(site)),
        blankIfNull(site.getForestFileId()),
        blankIfNull(site.getRoadSectionId()),
        plain(site.getUserKm()),
        plain(site.getLatitude()),
        plain(site.getLongitude()),
        Objects.toString(site.getUtmZone(), ""),
        plain(site.getUtmEasting()),
        plain(site.getUtmNorthing()),
        plain(structure.getCurrentLoadRating()));
  }

  /**
   * A form's items, from its lines: each question with the span or pier and the section it is
   * under, and that section's details ("LEFT: …; RIGHT: …"), the answers a space apart.
   */
  private static List<List<String>> items(List<Map<String, String>> lines) {
    List<List<String>> items = new ArrayList<>();
    String part = "";
    String section = "";
    List<String> details = new ArrayList<>();
    for (Map<String, String> line : lines) {
      switch (Kind.valueOf(line.get("KIND"))) {
        case PART -> part = blankIfNull(line.get("TEXT"));
        case SECTION -> {
          section = blankIfNull(line.get("TEXT"));
          details = new ArrayList<>();
        }
        case DETAIL -> details.add(blankIfNull(line.get("TEXT")));
        case QUESTION -> items.add(List.of(part, section, String.join("; ", details),
            blankIfNull(line.get("QUESTION"))
                + ("Y".equals(line.get("APPROACH")) ? " (approaching structure)" : ""),
            blankIfNull(line.get("CHOICES")).replace("   ", " ")));
        default -> {
          // Headings and fixed sections carry no item.
        }
      }
    }
    return items;
  }

  /**
   * The PDF's lines for Jasper to fill from, as {@link #rows} has them but loaded a batch of
   * structures at a time as Jasper reaches them, the session cleared before each — so a
   * province's forms are never all in memory. The road names are looked up once, beforehand. Fill
   * it inside a transaction: it loads as it is read.
   */
  BatchedRowSource<Long> rowSource(InspectionsDueCriteria criteria) {
    List<Long> ids = entityManager.createQuery(InspectionsDueReportSpecifications.blankIds(
        entityManager.getCriteriaBuilder(), criteria))
        .setHint(HibernateHints.HINT_FETCH_SIZE, ReportQueryHints.FETCH_ROWS)
        .getResultList();
    Shared shared = shared(ids);
    return new BatchedRowSource<>(ids, BATCH, batch -> {
      // The last batch's lines are Jasper's now, as plain values; its entities can go.
      entityManager.clear();
      Map<Long, CrossingStructureEntity> structures = structures(batch);
      Beside beside = beside(batch, structures, shared);
      List<Map<String, String>> lines = new ArrayList<>();
      for (Long id : batch) {
        lines.addAll(new Form(structures.get(id), beside).lines());
      }
      return lines;
    });
  }

  public List<Map<String, String>> rows(InspectionsDueCriteria criteria) {
    List<Long> ids = entityManager.createQuery(InspectionsDueReportSpecifications.blankIds(
        entityManager.getCriteriaBuilder(), criteria))
        .setHint(HibernateHints.HINT_FETCH_SIZE, ReportQueryHints.FETCH_ROWS)
        .getResultList();
    Map<Long, CrossingStructureEntity> structures = structures(ids);
    Beside beside = beside(ids, structures, shared(ids));
    List<Map<String, String>> lines = new ArrayList<>();
    for (Long id : ids) {
      lines.addAll(new Form(structures.get(id), beside).lines());
    }
    return lines;
  }

  /**
   * What every form reads beside its structure, a batch at a time: its questions, its first bridge
   * and that bridge's descriptions, its outstanding repairs and monitoring items, and the codes'
   * names — where a structure at a time made a province's forms outrun the browser's wait.
   *
   * @param shared what the report read once, before its batches ({@link #shared})
   */
  private Beside beside(
      List<Long> ids, Map<Long, CrossingStructureEntity> structures, Shared shared) {
    Map<Long, ForestServiceBridgeEntity> firstBridges = new HashMap<>();
    Map<Long, StructureBridgeDetail> details = new HashMap<>();
    Map<Long, List<StructureRepairEntity>> outstandingRepairs = new HashMap<>();
    Map<Long, List<StructureMonitorItemEntity>> outstandingMonitors = new HashMap<>();
    for (int from = 0; from < ids.size(); from += BATCH) {
      List<Long> batch = ids.subList(from, Math.min(ids.size(), from + BATCH));
      forestServiceBridges.findByCrossingStructureIdInOrderByForestServiceBridgeId(batch)
          .forEach(found -> firstBridges.putIfAbsent(found.getCrossingStructureId(), found));
      forestServiceBridges.findDetails(batch)
          .forEach(found -> details.putIfAbsent(found.crossingStructureId(), found));
      repairs.findByCrossingStructureIdInAndRepairStatusCodeInOrderByCrossingStructureIdAscRepairNumberAsc(
              batch, OUTSTANDING)
          .forEach(repair -> outstandingRepairs
              .computeIfAbsent(repair.getCrossingStructureId(), id -> new ArrayList<>()).add(repair));
      monitors
          .findByCrossingStructureIdInAndMonitoringStatusCodeInOrderByCrossingStructureIdAscMonitorNumberAsc(
              batch, OUTSTANDING)
          .forEach(monitor -> outstandingMonitors
              .computeIfAbsent(monitor.getCrossingStructureId(), id -> new ArrayList<>())
              .add(monitor));
    }
    Map<String, String> monitorStatuses = new HashMap<>();
    codes.getMonitoringStatusCodes()
        .forEach(code -> monitorStatuses.put(code.code(), code.description()));
    return new Beside(
        // The first bridges just read, and the catalogues read so far: not read again.
        forms.forms(ids.stream().map(structures::get).toList(), firstBridges,
            shared.catalogues()),
        firstBridges, details, outstandingRepairs, outstandingMonitors,
        shared.repairNames(), monitorStatuses, shared.roads());
  }

  /**
   * What a report reads once, before its batches, rather than in each: LRMOPS's road names for
   * every structure, the repair code tables, and a store for the form catalogues as each
   * type/class is first met. Read per batch, the catalogues and code tables were some 200 queries
   * for a province.
   */
  private Shared shared(List<Long> structureIds) {
    return new Shared(roadsOf(structureIds), repairNames.names(), forms.catalogues());
  }

  private record Shared(
      Function<CrossingSiteEntity, String> roads,
      Names repairNames,
      InspectionFormService.Catalogues catalogues) {}

  /** What the forms read beside their structures, by structure id. */
  private record Beside(
      Map<Long, InspectionFormResponse> forms,
      Map<Long, ForestServiceBridgeEntity> firstBridges,
      Map<Long, StructureBridgeDetail> bridgeDetails,
      Map<Long, List<StructureRepairEntity>> repairs,
      Map<Long, List<StructureMonitorItemEntity>> monitors,
      Names names,
      Map<String, String> monitorStatuses,
      Function<CrossingSiteEntity, String> roads) {}

  private Map<Long, CrossingStructureEntity> structures(List<Long> ids) {
    Map<Long, CrossingStructureEntity> found = new HashMap<>();
    for (int from = 0; from < ids.size(); from += BATCH) {
      entityManager.createQuery("""
              SELECT structure
                FROM CrossingStructureEntity structure
                JOIN FETCH structure.site site
               WHERE structure.crossingStructureId IN :ids
              """, CrossingStructureEntity.class)
          .setHint(HibernateHints.HINT_FETCH_SIZE, ReportQueryHints.FETCH_ROWS)
          .setParameter("ids", ids.subList(from, Math.min(ids.size(), from + BATCH)))
          .getResultList()
          .forEach(structure -> found.put(structure.getCrossingStructureId(), structure));
    }
    return found;
  }

  /** One structure's form, built a line at a time. */
  private final class Form {

    private final CrossingStructureEntity structure;
    private final Beside beside;
    private final Map<String, String> header = new HashMap<>();
    private final List<Map<String, String>> lines = new ArrayList<>();
    private final Optional<ForestServiceBridgeEntity> bridge;
    private final Optional<StructureBridgeDetail> bridgeDetail;
    private int section;

    Form(CrossingStructureEntity structure, Beside beside) {
      this.structure = structure;
      this.beside = beside;
      Long id = structure.getCrossingStructureId();
      bridge = Optional.ofNullable(beside.firstBridges().get(id));
      bridgeDetail = Optional.ofNullable(beside.bridgeDetails().get(id));
      String typeClass = structure.getStructureTypeClassCode();
      boolean isBridge = typeClass != null && BRIDGES.contains(typeClass);
      header.put("STRUCTURE_KEY", id.toString());
      header.put("CROSSING_SITE_ID", structure.getCrossingSiteId());
      header.put("CROSSING_STRUCTURE_NAME", structure.getCrossingStructureName());
      header.put("TITLE1", isBridge
          ? "Routine Condition Bridge Inspection Form"
          : "Routine Condition Culvert Inspection Form");
      if (isBridge) {
        String superstructure = superstructureCode();
        header.put("TITLE2", superstructure != null && STRINGER_SUPERSTRUCTURES.contains(superstructure)
            ? "(for log stringer or timber stringer bridges)"
            : "(except for log stringer or timber stringer bridges)");
      }
    }

    List<Map<String, String>> lines() {
      general();
      InspectionFormResponse form = beside.forms().get(structure.getCrossingStructureId());
      List<Subsection> perSpan = new ArrayList<>();
      Subsection pierGroup = null;
      for (Subsection group : form.subsections()) {
        if (group.scope() == Scope.SPAN) {
          perSpan.add(group);
        } else if (group.scope() == Scope.PIER) {
          pierGroup = group;
        } else {
          structureGroup(group);
        }
      }
      parts(perSpan, pierGroup, form.spans(), form.piers());
      fixed(Kind.CHANNEL);
      fixed(Kind.HAZARDS);
      repairs();
      monitors();
      fixed(Kind.PHOTO_LOG);
      fixed(Kind.INSPECTOR);
      Map<String, String> peng = line(Kind.PENG, newSection());
      peng.put("LOAD_RATING", structure.getCurrentLoadRating() == null
          ? null
          : format(structure.getCurrentLoadRating(), WHOLE));
      return lines;
    }

    private void general() {
      CrossingSiteEntity site = structure.getSite();
      Map<String, String> line = line(Kind.GENERAL, newSection());
      line.put("ROAD_SECT_NAME", beside.roads().apply(site));
      line.put("USER_KM", site.getUserKm() == null ? null : format(site.getUserKm(), KILOMETRES));
      line.put("FOREST_FILE_ID", site.getForestFileId());
      line.put("ROAD_SECTION_ID", site.getRoadSectionId());
      line.put("LONGITUDE", RepairsSummaryService.degreesMinutesSeconds(site.getLongitude()));
      line.put("LATITUDE", RepairsSummaryService.degreesMinutesSeconds(site.getLatitude()));
      line.put("UTM_ZONE", Objects.toString(site.getUtmZone(), null));
      line.put("UTM_EASTING", Objects.toString(site.getUtmEasting(), null));
      line.put("UTM_NORTHING", Objects.toString(site.getUtmNorthing(), null));
    }

    /** A group asked once of the structure: its heading, what it is, its questions. */
    private void structureGroup(Subsection group) {
      String key = newSection();
      Map<String, String> heading = line(Kind.SECTION, key);
      heading.put("TEXT", title(group));
      if ("APP".equals(group.code())) {
        heading.put("NOTE", RATING_LEGEND);
      }
      if ("ABUT".equals(group.code())) {
        bridgeDetail.ifPresent(detail -> {
          detail("LEFT: " + described(bridge.map(ForestServiceBridgeEntity::getLeftAbutmentCode),
              detail.leftAbutment()), key);
          detail("RIGHT: " + described(bridge.map(ForestServiceBridgeEntity::getRightAbutmentCode),
              detail.rightAbutment()), key);
        });
      }
      questions(group, key);
      if (InspectionFormService.WOOD_BOX_CULVERT.equals(group.code())) {
        fixed(Kind.LOG_STRINGERS);
      }
    }

    /**
     * The span groups for each span, the pier after the span before it; with no spans recorded,
     * the span groups once, then any piers.
     */
    private void parts(List<Subsection> perSpan, Subsection pierGroup, List<Part> spans,
        List<Part> piers) {
      if (spans.isEmpty()) {
        perSpan.forEach(group -> spanGroup(group, newSection()));
      }
      for (int index = 0; index < spans.size(); index++) {
        String key = part("Span " + number(spans.get(index)) + " of " + spans.size(),
            "(spans are numbered consecutively from the left abutment)");
        // The span's heading goes with its first group, never alone at the foot of a page.
        for (Subsection group : perSpan) {
          spanGroup(group, key);
          key = newSection();
        }
        if (pierGroup != null && index < piers.size() && index < spans.size() - 1) {
          pier(pierGroup, piers.get(index), piers.size());
        }
      }
      if (pierGroup != null) {
        int printed = spans.isEmpty() ? 0 : Math.min(piers.size(), spans.size() - 1);
        for (int index = printed; index < piers.size(); index++) {
          pier(pierGroup, piers.get(index), piers.size());
        }
      }
    }

    private void pier(Subsection group, Part pier, int count) {
      String key = part("Pier " + number(pier) + " of " + count,
          "(piers are numbered consecutively from the left abutment)");
      line(Kind.SECTION, key).put("TEXT", title(group));
      questions(group, key);
    }

    private void spanGroup(Subsection group, String key) {
      Map<String, String> heading = line(Kind.SECTION, key);
      heading.put("TEXT", title(group));
      if (InspectionFormService.STRINGERS.equals(group.code())) {
        heading.put("NOTE", "(stringers are numbered from upstream side of bridge)");
      }
      if ("DECK".equals(group.code())) {
        bridgeDetail.ifPresent(detail -> detail(
            described(bridge.map(ForestServiceBridgeEntity::getDeckTypeCode), detail.deckType()),
            key));
      } else {
        bridgeDetail.ifPresent(detail -> detail(described(
            bridge.map(ForestServiceBridgeEntity::getSuperstructureTypeCode),
            detail.superstructureType()), key));
      }
      questions(group, key);
      if (InspectionFormService.STRINGERS.equals(group.code())) {
        fixed(Kind.STRINGERS);
      }
    }

    /**
     * A group's condition questions under their column headings — a stringer's own questions are
     * its table's columns instead.
     */
    private void questions(Subsection group, String key) {
      List<Question> asked = group.questions().stream()
          .filter(question -> !question.perStringer())
          .toList();
      if (!asked.isEmpty()) {
        line(Kind.COLUMNS, key);
      }
      for (Question question : asked) {
        Map<String, String> line = line(Kind.QUESTION, key);
        line.put("QUESTION", question.description());
        line.put("CHOICES", choices(question.answers()));
        if (APPROACH_QUESTIONS.contains(question.code())) {
          line.put("APPROACH", "Y");
        }
      }
    }

    private void repairs() {
      String key = newSection();
      line(Kind.REPAIRS, key);
      for (StructureRepairEntity repair
          : beside.repairs().getOrDefault(structure.getCrossingStructureId(), List.of())) {
        Map<String, String> line = line(Kind.REPAIR, key);
        line.put("NUMBER", Objects.toString(repair.getRepairNumber(), null));
        line.put("STATUS", beside.names().statuses().get(repair.getRepairStatusCode()));
        line.put("PRIORITY", beside.names().priorities().get(repair.getRepairPriorityCode()));
        line.put("ESTIMATE", repair.getEstimate() == null
            ? null
            : format(BigDecimal.valueOf(repair.getEstimate()), MONEY));
        line.put("DESCRIPTION", repair.getDescription());
      }
      line(Kind.REPAIR_BLANK, key);
    }

    private void monitors() {
      String key = newSection();
      line(Kind.MONITORS, key);
      for (StructureMonitorItemEntity monitor
          : beside.monitors().getOrDefault(structure.getCrossingStructureId(), List.of())) {
        Map<String, String> line = line(Kind.MONITOR, key);
        line.put("NUMBER", Objects.toString(monitor.getMonitorNumber(), null));
        line.put("STATUS", beside.monitorStatuses().get(monitor.getMonitoringStatusCode()));
        line.put("DESCRIPTION", monitor.getDescription());
      }
      line(Kind.MONITOR_BLANK, key);
    }

    /** A span or pier heading, starting a section the part's first group goes on in. */
    private String part(String text, String note) {
      String key = newSection();
      Map<String, String> line = line(Kind.PART, key);
      line.put("TEXT", text);
      line.put("NOTE", note);
      return key;
    }

    private void detail(String text, String key) {
      line(Kind.DETAIL, key).put("TEXT", text);
    }

    private void fixed(Kind kind) {
      line(kind, newSection());
    }

    private String newSection() {
      return structure.getCrossingStructureId() + "-" + section++;
    }

    private Map<String, String> line(Kind kind, String sectionKey) {
      Map<String, String> line = new HashMap<>(header);
      line.put("KIND", kind.name());
      line.put("SECTION_KEY", sectionKey);
      lines.add(line);
      return line;
    }

    private String superstructureCode() {
      return bridge.map(ForestServiceBridgeEntity::getSuperstructureTypeCode).orElse(null);
    }
  }

  private static String title(Subsection group) {
    return TITLES.getOrDefault(group.code(),
        Objects.toString(group.description(), group.code()).toUpperCase(Locale.ROOT));
  }

  /** "TMB - Timber", or whichever half there is. */
  private static String described(Optional<String> code, String description) {
    return Stream.of(code.orElse(null), description)
        .filter(StringUtils::hasText)
        .collect(Collectors.joining(" - "));
  }

  private static String number(Part part) {
    return part.number() == null ? "" : part.number().toString();
  }

  /** A question's answers to circle, in order, spaced apart: "E   G   F   P   N/A". */
  static String choices(List<CodeOptionResponse> answers) {
    return answers.stream()
        .map(CodeOptionResponse::code)
        .filter(code -> !UNRATED.equals(code))
        .map(code -> CHOICE_TEXT.getOrDefault(code, code))
        .collect(Collectors.joining("   "));
  }
}
