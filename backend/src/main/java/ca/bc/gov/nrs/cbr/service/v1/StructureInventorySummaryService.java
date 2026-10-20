package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureValue;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.specification.v1.StructureInventoryLine;
import ca.bc.gov.nrs.cbr.specification.v1.StructureInventoryReportSpecifications;
import jakarta.persistence.EntityManager;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The Structure Inventory – Summary report (legacy CBR008): one line per active structure, filtered
 * by the criteria, as a PDF in legacy's layout or as a CSV of the same columns.
 *
 * <p>Legacy filled its template from {@code CBR_REPORTING.STRCTRE_INVNTRY_SUMMRY_RPT}. Which
 * structures, in what order, and the structure's and site's values each line prints, are now one
 * query ({@link StructureInventoryReportSpecifications}); this class reads the rest — the bridge or
 * culvert length, the last routine inspection and its status — a batch of structures at a time,
 * and formats every value once, as legacy's template printed it, for both outputs.
 *
 * <p><b>Current Load Rating</b> is the structure's {@code CURRENT_LOAD_RATING}. Legacy's summary
 * worked it out per structure with {@code FIND_CRNT_LD_RATING_BY_STRC_ID}; the column holds the same
 * answer, kept by the review ({@link LoadRatingService}), and is what legacy's Detailed report
 * already reads. Working it out again would cost several queries a structure.
 */
@Service
@Transactional(readOnly = true)
public class StructureInventorySummaryService {

  private static final Logger log = LoggerFactory.getLogger(StructureInventorySummaryService.class);

  /** The template under {@code classpath:reports/}. */
  static final String TEMPLATE = "STRUCTURE_INVENTORY_SUMMARY";

  /** The legacy report number the header prints. */
  static final String REPORT_ID = "CBR008";

  /**
   * The ministry line above every report's title — legacy's shared {@code CBR_HEADER}, word for
   * word.
   */
  static final String MINISTRY =
      "BC Ministry of Forests, Lands, and Natural Resource Operations";

  static final String LOGO = "reports/images/Fslogocl.jpg";

  /** The file's name before the date, in legacy's pattern for its reports' files. */
  static final String FILENAME = "StructureInventorySummaryReport";

  /** The columns, in order: the CSV's header, and what the PDF's column headings say. */
  static final List<String> COLUMNS = List.of(
      "Site #", "Structure #", "Project File ID # / Br.", "Road Name", "Crossing Name",
      "Portable?", "KM", "Total Length (metres)", "Structure Type/Class",
      "Current Load Rating GVW (tonnes)", "Last Inspection Date", "Year Superstructure Installed",
      "Inspection Status", "Estimated Load Restriction Date", "Capital Road");

  /** Oracle's limit on the values in one {@code IN} list. */
  private static final int BATCH = 1000;

  private static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
  private static final String YES = "Y";

  private final EntityManager entityManager;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final StructureInspectionRepository inspections;
  private final InspectionReportStatusRepository statusRows;
  private final ConfigurationService codes;
  private final ReportRenderer renderer;
  private final LoggedUserHelper loggedUser;
  private final String environment;

  public StructureInventorySummaryService(
      EntityManager entityManager,
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      StructureInspectionRepository inspections,
      InspectionReportStatusRepository statusRows,
      ConfigurationService codes,
      ReportRenderer renderer,
      LoggedUserHelper loggedUser,
      @Value("${info.app.zone:local}") String environment) {
    this.entityManager = entityManager;
    this.bridges = bridges;
    this.culverts = culverts;
    this.inspections = inspections;
    this.statusRows = statusRows;
    this.codes = codes;
    this.renderer = renderer;
    this.loggedUser = loggedUser;
    this.environment = environment;
  }

  /** The report, as asked for. */
  public ReportFile generate(StructureInventorySummaryCriteria criteria, ReportFormat format) {
    long started = System.nanoTime();
    List<Row> rows = rows(criteria, format == ReportFormat.CSV ? dataStyle() : Style.PRINTED);
    long built = System.nanoTime();
    String printed = LocalDate.now(PACIFIC).format(DATE);
    // Legacy's CSV names — "StructureInventoryDetailReport.csv" — dated, so runs on different days
    // do not overwrite each other.
    String filename = FILENAME + "_" + printed.replace('/', '-') + "."
        + format.extension();
    byte[] content = switch (format) {
      case PDF -> renderer.pdf(TEMPLATE, parameters(criteria, printed),
          rows.stream().<Map<String, ?>>map(Row::fields).toList());
      case CSV -> csv(rows);
    };
    log.info("Structure Inventory - Summary: {} rows as {} ({} bytes) — rows {} ms, {} {} ms",
        rows.size(), format, content.length, (built - started) / 1_000_000, format,
        millisSince(built));
    return new ReportFile(content, filename, format.mediaType());
  }

  /** The lines of the report, in order, every value as the PDF prints it. */
  List<Row> rows(StructureInventorySummaryCriteria criteria) {
    return rows(criteria, Style.PRINTED);
  }

  /** The lines of the report, in order, every value written in this style. */
  List<Row> rows(StructureInventorySummaryCriteria criteria, Style style) {
    List<StructureInventoryLine> lines = entityManager.createQuery(
        StructureInventoryReportSpecifications.summary(
            entityManager.getCriteriaBuilder(), criteria)).getResultList();
    List<Long> ids = lines.stream().map(StructureInventoryLine::crossingStructureId).toList();
    Map<Long, BigDecimal> bridgeLengths = firstOf(ids, bridges::findLengths);
    Map<Long, BigDecimal> culvertLengths = firstOf(ids, culverts::findLengths);
    Map<Long, LocalDate> lastInspected = firstOf(ids, inspections::findLastRoutineInspectionDates);
    Map<Long, String> statuses = latestRoutineStatuses(ids);

    return lines.stream().map(line -> {
      Long id = line.crossingStructureId();
      // Legacy's CASE: a structure with a bridge row shows the bridge's length, even when the
      // bridge has none recorded; only one with no bridge shows the culvert's.
      BigDecimal length = bridgeLengths.containsKey(id)
          ? bridgeLengths.get(id) : culvertLengths.get(id);
      return new Row(
          blankIfNull(line.crossingSiteId()),
          blankIfNull(line.crossingStructureName()),
          projectFile(line.forestFileId(), line.roadSectionId()),
          blankIfNull(line.roadSectName()),
          blankIfNull(line.crossingName()),
          YES.equalsIgnoreCase(line.portableStructureInd()) ? "Yes" : "No",
          line.pointOfCommencementDistance() == null ? ""
              : style.kilometres().apply(line.pointOfCommencementDistance()),
          length == null ? "" : style.length().apply(length),
          style.typeClass().apply(line.structureTypeClassCode()),
          line.currentLoadRating() == null ? ""
              : line.currentLoadRating().setScale(0, RoundingMode.HALF_EVEN).toPlainString(),
          // Present but empty when every routine inspection is undated — MAX of nothing.
          Optional.ofNullable(lastInspected.get(id)).map(date -> date.format(DATE)).orElse(""),
          number(line.yearBuilt()),
          blankIfNull(statuses.get(id)),
          number(line.fullLogHaulReplacementYear()),
          style.capitalRoad().apply(line.capitalRoadInd()));
    }).toList();
  }

  /** The CSV's style, with the type/class descriptions it names in full. */
  private Style dataStyle() {
    Map<String, String> typeClasses = new HashMap<>();
    codes.getStructureTypeClassCodes().forEach(option ->
        typeClasses.put(option.code(), option.description()));
    return Style.data(typeClasses);
  }

  /**
   * How a line's values are written. The PDF prints them as legacy's template did — a thousands
   * separator, two decimals of kilometres, the one-letter type/class, Y or N. The CSV is data to
   * sort, sum and filter: plain numbers, and codes named in full.
   */
  record Style(
      Function<BigDecimal, String> kilometres,
      Function<BigDecimal, String> length,
      Function<String, String> typeClass,
      Function<String, String> capitalRoad) {

    static final Style PRINTED = new Style(
        km -> km.setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
        length -> lengthFormat().format(length),
        StructureInventorySummaryService::typeClass,
        StructureInventorySummaryService::blankIfNull);

    /** Plain numbers, the type/class's description, and Yes or No. */
    static Style data(Map<String, String> typeClasses) {
      return new Style(
          Style::plain,
          Style::plain,
          code -> code == null ? "" : typeClasses.getOrDefault(code, code),
          indicator -> {
            if (indicator == null) {
              return "";
            }
            return YES.equalsIgnoreCase(indicator) ? "Yes" : "No";
          });
    }

    /** "1234.6", "12.5": no grouping, no trailing zeros — what a spreadsheet reads as a number. */
    private static String plain(BigDecimal value) {
      return value.stripTrailingZeros().toPlainString();
    }
  }

  /**
   * The criteria line under the title — legacy's {@code getSelectedCriteria}, in its order and
   * wording, with codes shown as their descriptions. A range with one end prints {@code >=} or
   * {@code <=}, as the filter now includes that end.
   */
  String selectedCriteria(StructureInventorySummaryCriteria criteria) {
    List<String> parts = new ArrayList<>();
    orgUnit(codes.getForestDistricts(), criteria.orgUnit())
        .ifPresent(name -> parts.add("Forest District: " + name));
    orgUnit(codes.getBusinessAreas(), criteria.businessAreaOrgUnit())
        .ifPresent(name -> parts.add("Business Area: " + name));
    if (StringUtils.hasText(criteria.managementOrgUnit()) && StringUtils.hasText(criteria.orgUnit())) {
      orgUnit(codes.getManagementAreas(criteria.orgUnit()), criteria.managementOrgUnit())
          .ifPresent(name -> parts.add("Management Area: " + name));
    }
    text(criteria.forestFileId()).ifPresent(value -> parts.add("Project File ID: " + value));
    text(criteria.roadSectionId()).ifPresent(value -> parts.add("Br.: " + value));
    if (Boolean.TRUE.equals(criteria.downrated())) {
      parts.add("Downrated Structure: Yes");
    }
    range(criteria.kiloStart(), criteria.kiloEnd())
        .ifPresent(value -> parts.add("Kilometres: " + value));
    range(criteria.yearBuiltStart(), criteria.yearBuiltEnd())
        .ifPresent(value -> parts.add("Year Installed: " + value));
    code(codes.getSpecialAccessCodes(), criteria.specialAccessCode())
        .ifPresent(value -> parts.add("Special Access Requirements: " + value));
    code(codes.getPortableSuperstructureStatusCodes(), criteria.portableSuperstructureStatusCode())
        .ifPresent(value -> parts.add("Portable Superstructure Status: " + value));
    range(criteria.loadRestrictionYearStart(), criteria.loadRestrictionYearEnd())
        .ifPresent(value -> parts.add("Estimated Load Restriction Date: " + value));
    range(criteria.replacementYearStart(), criteria.replacementYearEnd())
        .ifPresent(value -> parts.add("Estimated Replacement Date: " + value));
    text(criteria.clientNumber())
        .ifPresent(value -> parts.add("Primary User Client Number: " + value));
    text(criteria.clientLocationCode())
        .ifPresent(value -> parts.add("Client Location Code: " + value));
    code(codes.getStructureTypeClassCodes(), criteria.structureTypeClassCode())
        .ifPresent(value -> parts.add("Type/Class: " + value));
    code(codes.getSuperstructureTypeCodes(), criteria.superstructureTypeCode())
        .ifPresent(value -> parts.add("Superstructure Type: " + value));
    code(codes.getDeckTypeCodes(), criteria.deckTypeCode())
        .ifPresent(value -> parts.add("Deck Type: " + value));
    code(codes.getCulvertTypeCodes(), criteria.culvertTypeCode())
        .ifPresent(value -> parts.add("Culvert Type: " + value));
    code(codes.getCulvertMaterialCodes(), criteria.culvertMaterialCode())
        .ifPresent(value -> parts.add("Culvert Material: " + value));
    range(criteria.openingWidthMin(), criteria.openingWidthMax())
        .ifPresent(value -> parts.add("Opening Width: " + value));
    code(codes.getSiteStatusCodes(), criteria.siteStatusCode())
        .ifPresent(value -> parts.add("Site Status: " + value));
    if (Boolean.TRUE.equals(criteria.capitalRoad())) {
      parts.add("Capital Road: Yes");
    }
    code(codes.getSiteTypeCodes(), criteria.siteTypeCode())
        .ifPresent(value -> parts.add("Site Type: " + value));
    parts.add("Sort By: " + criteria.sortOrDefault().label());
    return String.join(" ", parts);
  }

  private Map<String, Object> parameters(StructureInventorySummaryCriteria criteria, String printed) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("P_USERID", loggedUser.getLoggedUserId());
    parameters.put("P_DATABASE", environment.toUpperCase(Locale.ROOT));
    parameters.put("P_SELECTED_CRITERIA", selectedCriteria(criteria));
    parameters.put("P_DATE_PRINTED", printed);
    parameters.put("P_MINISTRY", MINISTRY);
    parameters.put("P_LOGO", LOGO);
    parameters.put("p_report_id", REPORT_ID);
    return parameters;
  }

  private static byte[] csv(List<Row> rows) {
    StringWriter out = new StringWriter();
    try (CSVPrinter printer = new CSVPrinter(out,
        CSVFormat.DEFAULT.builder().setHeader(COLUMNS.toArray(String[]::new)).build())) {
      for (Row row : rows) {
        printer.printRecord(row.values());
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * One value per structure, looked up a batch at a time. Where a structure has more than one —
   * two bridge records — the first the query returns wins.
   */
  private static <T> Map<Long, T> firstOf(
      List<Long> ids, Function<Collection<Long>, List<StructureValue<T>>> lookup) {
    Map<Long, T> values = new HashMap<>();
    for (int from = 0; from < ids.size(); from += BATCH) {
      for (StructureValue<T> found : lookup.apply(ids.subList(from, Math.min(ids.size(), from + BATCH)))) {
        values.putIfAbsent(found.crossingStructureId(), found.value());
      }
    }
    return values;
  }

  /**
   * Each structure's latest routine inspection status code: the newest status row's id per
   * structure, then those rows' codes by their key — two plain lookups a batch at a time.
   */
  private Map<Long, String> latestRoutineStatuses(List<Long> ids) {
    Map<Long, Long> newest = firstOf(ids, inspections::findLatestRoutineStatusIds);
    List<Long> statusIds = newest.values().stream().filter(Objects::nonNull).toList();
    Map<Long, String> codesById = new HashMap<>();
    for (int from = 0; from < statusIds.size(); from += BATCH) {
      for (InspectionReportStatusEntity row
          : statusRows.findAllById(statusIds.subList(from, Math.min(statusIds.size(), from + BATCH)))) {
        codesById.put(row.getInspectionReportStatusId(), row.getInspectionReportStatusCode());
      }
    }
    Map<Long, String> latest = new HashMap<>();
    newest.forEach((structureId, statusId) -> latest.put(structureId, codesById.get(statusId)));
    return latest;
  }

  private static long millisSince(long nanos) {
    return (System.nanoTime() - nanos) / 1_000_000;
  }

  /** "R00123-01": the project, and its branch after a dash when there is one. */
  private static String projectFile(String forestFileId, String roadSectionId) {
    String file = blankIfNull(forestFileId);
    return StringUtils.hasLength(roadSectionId) ? file + "-" + roadSectionId : file;
  }

  /** Legacy's one-letter type/class: P, T, MC (a culvert), W (a wood-log culvert). */
  private static String typeClass(String code) {
    if (code == null) {
      return "";
    }
    return switch (code.toUpperCase(Locale.ROOT)) {
      case "PB" -> "P";
      case "TB" -> "T";
      case "CUL" -> "MC";
      case "WLC" -> "W";
      default -> "";
    };
  }

  /** Legacy's {@code #,##0.0}. Made per call: {@link DecimalFormat} is not thread-safe. */
  private static DecimalFormat lengthFormat() {
    DecimalFormat format = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.CANADA));
    format.setRoundingMode(RoundingMode.HALF_EVEN);
    return format;
  }

  private static String number(Integer value) {
    return value == null ? "" : value.toString();
  }

  private static String blankIfNull(String value) {
    return value == null ? "" : value;
  }

  private static Optional<String> text(String value) {
    return StringUtils.hasText(value) ? Optional.of(value.trim()) : Optional.empty();
  }

  private static Optional<String> range(String from, String to) {
    boolean hasFrom = StringUtils.hasText(from);
    boolean hasTo = StringUtils.hasText(to);
    if (hasFrom && hasTo) {
      return Optional.of(from.trim() + " - " + to.trim());
    }
    if (hasFrom) {
      return Optional.of(">= " + from.trim());
    }
    return hasTo ? Optional.of("<= " + to.trim()) : Optional.empty();
  }

  /** A code's description, or the code itself when the list does not hold it. */
  private static Optional<String> code(List<CodeOptionResponse> options, String code) {
    return text(code).map(wanted -> options.stream()
        .filter(option -> wanted.equals(option.code()))
        .map(CodeOptionResponse::description)
        .filter(StringUtils::hasText)
        .findFirst()
        .orElse(wanted));
  }

  private static Optional<String> orgUnit(List<OrgUnitResponse> options, String orgUnitNo) {
    return text(orgUnitNo).map(wanted -> options.stream()
        .filter(option -> wanted.equals(option.orgUnitNo()))
        .map(OrgUnitResponse::orgUnitName)
        .filter(StringUtils::hasText)
        .findFirst()
        .orElse(wanted));
  }

  /** One line of the report, every value as it prints. */
  record Row(
      String siteId,
      String structureName,
      String projectFile,
      String roadName,
      String crossingName,
      String portable,
      String kilometres,
      String totalLength,
      String typeClass,
      String loadRating,
      String lastInspectionDate,
      String yearBuilt,
      String inspectionStatus,
      String loadRestrictionYear,
      String capitalRoad) {

    /** In {@link #COLUMNS} order, for the CSV. */
    List<String> values() {
      return List.of(siteId, structureName, projectFile, roadName, crossingName, portable,
          kilometres, totalLength, typeClass, loadRating, lastInspectionDate, yearBuilt,
          inspectionStatus, loadRestrictionYear, capitalRoad);
    }

    /** Keyed by the template's field names, for the PDF. */
    Map<String, String> fields() {
      Map<String, String> fields = new LinkedHashMap<>();
      fields.put("CROSSING_SITE_ID", siteId);
      fields.put("CROSSING_STRUCTURE_NAME", structureName);
      fields.put("PROJECT_FILE", projectFile);
      fields.put("ROAD_SECT_NAME", roadName);
      fields.put("CROSSING_NAME", crossingName);
      fields.put("PORTABLE", portable);
      fields.put("KM", kilometres);
      fields.put("TOTAL_LENGTH", totalLength);
      fields.put("TYPE_CLASS", typeClass);
      fields.put("LOAD_RATING", loadRating);
      fields.put("INSPECTION_DATE", lastInspectionDate);
      fields.put("YEAR_BUILT", yearBuilt);
      fields.put("INSPECTION_STATUS", inspectionStatus);
      fields.put("LOAD_RESTRICTION_YEAR", loadRestrictionYear);
      fields.put("CAPITAL_ROAD", capitalRoad);
      return fields;
    }
  }
}
