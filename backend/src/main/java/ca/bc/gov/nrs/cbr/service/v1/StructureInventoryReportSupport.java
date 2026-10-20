package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionReportStatusRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureValue;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.OrgUnitResponse;
import ca.bc.gov.nrs.cbr.struct.v1.ReportFormat;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventoryCriteria;
import ca.bc.gov.nrs.cbr.struct.v1.StructureInventorySummaryCriteria;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * What the Structure Inventory reports share: legacy's report header — the ministry line, the logo,
 * the user, the database, the date and the criteria line — the file's name, and the batching and
 * formatting every report's rows need.
 */
@Component
public class StructureInventoryReportSupport {

  /**
   * The ministry line above every report's title — legacy's shared {@code CBR_HEADER}, word for
   * word.
   */
  static final String MINISTRY =
      "BC Ministry of Forests, Lands, and Natural Resource Operations";

  static final String LOGO = "reports/images/Fslogocl.jpg";

  /** Oracle's limit on the values in one {@code IN} list. */
  static final int BATCH = 1000;

  static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

  private static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");

  private final ConfigurationService codes;
  private final LoggedUserHelper loggedUser;
  private final StructureInspectionRepository inspections;
  private final InspectionReportStatusRepository statusRows;
  private final String environment;

  public StructureInventoryReportSupport(
      ConfigurationService codes,
      LoggedUserHelper loggedUser,
      StructureInspectionRepository inspections,
      InspectionReportStatusRepository statusRows,
      @Value("${info.app.zone:local}") String environment) {
    this.codes = codes;
    this.loggedUser = loggedUser;
    this.inspections = inspections;
    this.statusRows = statusRows;
    this.environment = environment;
  }

  /** Today's date as the header prints it — in Pacific time; the server's clock is UTC. */
  String today() {
    return LocalDate.now(PACIFIC).format(DATE);
  }

  /**
   * "StructureInventorySummaryReport_2026-10-20.pdf": legacy's name for its reports' files —
   * {@code StructureInventoryDetailReport.csv} — dated, so runs on different days do not overwrite
   * each other.
   */
  static String filename(String report, String printed, ReportFormat format) {
    return report + "_" + printed.replace('/', '-') + "." + format.extension();
  }

  /** The header every report's template takes. */
  Map<String, Object> parameters(
      StructureInventoryCriteria criteria, String reportId, String printed) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("P_USERID", loggedUser.getLoggedUserId());
    parameters.put("P_DATABASE", environment.toUpperCase(Locale.ROOT));
    parameters.put("P_SELECTED_CRITERIA", selectedCriteria(criteria));
    parameters.put("P_DATE_PRINTED", printed);
    parameters.put("P_MINISTRY", MINISTRY);
    parameters.put("P_LOGO", LOGO);
    parameters.put("p_report_id", reportId);
    return parameters;
  }

  /**
   * The criteria line under the title — legacy's {@code getSelectedCriteria}, in its order and
   * wording, with codes shown as their descriptions; the Summary's construction filters in their
   * place among the rest. A range with one end prints {@code >=} or {@code <=}, as the filter now
   * includes that end.
   */
  String selectedCriteria(StructureInventoryCriteria criteria) {
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
    if (criteria instanceof StructureInventorySummaryCriteria summary) {
      constructionCriteria(summary, parts);
    }
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

  /** The Summary's own filters, where legacy's Summary prints them. */
  private void constructionCriteria(StructureInventorySummaryCriteria criteria, List<String> parts) {
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
  }

  /**
   * One value per structure, looked up a batch at a time. Where a structure has more than one —
   * two bridge records — the first the query returns wins.
   */
  static <T> Map<Long, T> firstOf(
      List<Long> ids, Function<Collection<Long>, List<StructureValue<T>>> lookup) {
    return firstOf(ids, lookup, StructureValue::crossingStructureId, StructureValue::value);
  }

  /** The same, for a lookup returning its own row type. */
  static <R, T> Map<Long, T> firstOf(
      List<Long> ids,
      Function<Collection<Long>, List<R>> lookup,
      Function<R, Long> structureId,
      Function<R, T> value) {
    Map<Long, T> values = new HashMap<>();
    for (int from = 0; from < ids.size(); from += BATCH) {
      for (R found : lookup.apply(ids.subList(from, Math.min(ids.size(), from + BATCH)))) {
        values.putIfAbsent(structureId.apply(found), value.apply(found));
      }
    }
    return values;
  }

  /**
   * Each structure's latest routine inspection status code: the newest status row's id per
   * structure, then those rows' codes by their key — two plain lookups a batch at a time.
   */
  Map<Long, String> latestRoutineStatuses(List<Long> ids) {
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

  /**
   * A CSV of these rows under this header: plain UTF-8, no byte-order mark, quoted only where a
   * value needs it — data to sort and filter, not a page.
   */
  static byte[] csv(List<String> header, List<List<String>> rows) {
    StringWriter out = new StringWriter();
    try (CSVPrinter printer = new CSVPrinter(out,
        CSVFormat.DEFAULT.builder().setHeader(header.toArray(String[]::new)).build())) {
      for (List<String> row : rows) {
        printer.printRecord(row);
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** "1234.6", "12.5": no grouping, no trailing zeros — what a spreadsheet reads as a number. */
  static String plain(BigDecimal value) {
    return value == null ? "" : value.stripTrailingZeros().toPlainString();
  }

  /** A whole number as a spreadsheet reads it, or blank. */
  static String plain(Long value) {
    return value == null ? "" : value.toString();
  }

  /** An indicator as the CSVs write it: Yes, No, or blank when there is none. */
  static String yesNo(String indicator) {
    if (indicator == null) {
      return "";
    }
    return "Y".equalsIgnoreCase(indicator) ? "Yes" : "No";
  }

  static long millisSince(long nanos) {
    return (System.nanoTime() - nanos) / 1_000_000;
  }

  static String blankIfNull(String value) {
    return value == null ? "" : value;
  }

  static String number(Integer value) {
    return value == null ? "" : value.toString();
  }

  /** A date as the reports print it, or blank. */
  static String date(LocalDate value) {
    return value == null ? "" : value.format(DATE);
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
}
