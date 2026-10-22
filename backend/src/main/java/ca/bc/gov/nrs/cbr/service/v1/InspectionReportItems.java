package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionAnswerCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionSubsectionCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgePierRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeSpanRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionAnswerCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionQuestionCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionSubsectionCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionItemRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * An inspection's items as the Inspections Conducted – Detail prints them: the lines of its
 * inspection-items subreport, in order, each tagged with the band that prints it ({@link Kind}).
 *
 * <p>Legacy's {@code CBR_REPORTING.INSPCTN_ITEM_INSPCTN_RPT} read the items in subsection, span,
 * pier, stringer and question order and folded each stringer's questions into one row — the
 * stringer table of a stringer subsection (STR) or a wood-box culvert (WDC); every other item was a
 * row of its own. Its subreport then sorted the rows by subsection description and printed a span
 * or pier bar, and a stringer table's heading, where the row before differed. Both are done here,
 * so the template prints the lines it is given. The CSV takes the items unfolded, a row each
 * ({@link #answers}).
 *
 * <p>Where it differs from legacy:
 * <ul>
 *   <li>A stringer row keeps every one of its items' comments; legacy printed only the bottom rot
 *       depth's (STR) or the average rot depth's (WDC).</li>
 *   <li>The first span or pier gets its bar; legacy compared it against an empty "previous" and
 *       printed bars only for the spans after it.</li>
 *   <li>A stringer row ends when the subsection does; legacy folded an STR stringer into a WDC
 *       stringer of the same number that followed it.</li>
 * </ul>
 */
@Component
public class InspectionReportItems {

  /** The bands of the inspection-items subreport, as the template's {@code KIND} names them. */
  enum Kind {
    SUBSECTION, SPAN, PIER, WDC_HEADING, WDC, STR_HEADING, STR, QUESTION, QUESTION_APPROACH
  }

  private static final String STRINGERS = "STR";
  private static final String WOOD_CULVERT = "WDC";
  /** Deck questions printed with "(approaching structure)" beneath the description. */
  private static final List<String> APPROACH_QUESTIONS = List.of("DML", "DMR");
  private static final String COMMENT_SEPARATOR = "; ";

  private final StructureInspectionItemRepository items;
  private final InspectionSubsectionCodeRepository subsections;
  private final InspectionQuestionCodeRepository questions;
  private final InspectionAnswerCodeRepository answers;
  private final ForestServiceBridgeSpanRepository spans;
  private final ForestServiceBridgePierRepository piers;

  public InspectionReportItems(
      StructureInspectionItemRepository items,
      InspectionSubsectionCodeRepository subsections,
      InspectionQuestionCodeRepository questions,
      InspectionAnswerCodeRepository answers,
      ForestServiceBridgeSpanRepository spans,
      ForestServiceBridgePierRepository piers) {
    this.items = items;
    this.subsections = subsections;
    this.questions = questions;
    this.answers = answers;
    this.spans = spans;
    this.piers = piers;
  }

  /** Each inspection's printed lines, by inspection id; an inspection with no items has none. */
  Map<Long, List<Map<String, String>>> lines(Collection<Long> inspectionIds) {
    Map<Long, List<Map<String, String>>> lines = new HashMap<>();
    items(inspectionIds).forEach((id, list) -> lines.put(id, printed(rows(sorted(list)))));
    return lines;
  }

  /**
   * Each inspection's items as the CSV writes them, by inspection id: an item each — subsection,
   * span, pier, stringer, question, answer and comment — in the PDF's order, a stringer's questions
   * each a row of their own rather than folded into one. An inspection with no items has none.
   */
  Map<Long, List<List<String>>> answers(Collection<Long> inspectionIds) {
    Map<Long, List<List<String>>> answers = new HashMap<>();
    items(inspectionIds).forEach((id, list) -> answers.put(id, sorted(list).stream()
        .sorted(Comparator.comparing(Item::subsectionDescription,
            Comparator.nullsFirst(Comparator.naturalOrder())))
        .map(item -> List.of(
            blank(item.subsectionDescription()),
            number(item.span()),
            number(item.pier()),
            number(item.stringer()),
            blank(item.questionDescription()),
            item.stringerMeasure() ? blank(item.answerValue()) : item.answer(),
            blank(item.comment())))
        .toList()));
    return answers;
  }

  /** The items of these inspections, with their codes named, by inspection id. */
  private Map<Long, List<Item>> items(Collection<Long> inspectionIds) {
    if (inspectionIds.isEmpty()) {
      return Map.of();
    }
    List<StructureInspectionItemEntity> rows = new ArrayList<>();
    List<Long> ids = List.copyOf(inspectionIds);
    for (int from = 0; from < ids.size(); from += StructureInventoryReportSupport.BATCH) {
      rows.addAll(items.findByInspectionIdIn(
          ids.subList(from, Math.min(ids.size(), from + StructureInventoryReportSupport.BATCH))));
    }
    Map<String, InspectionSubsectionCodeEntity> subsectionCodes = byKey(subsections.findAll(),
        InspectionSubsectionCodeEntity::getSubsectionCode);
    Map<String, String> questionNames = names(questions.findAll(),
        InspectionQuestionCodeEntity::getQuestionCode, InspectionQuestionCodeEntity::getDescription);
    Map<String, String> answerNames = names(answers.findAll(),
        InspectionAnswerCodeEntity::getAnswerCode, InspectionAnswerCodeEntity::getDescription);
    Map<Long, Long> spanNumbers = numbers(spans.findAllById(ids(rows,
        StructureInspectionItemEntity::getForestServiceBridgeSpanId)),
        ForestServiceBridgeSpanEntity::getForestServiceBridgeSpanId,
        span -> span.getSpanNumber() == null ? null : span.getSpanNumber().longValue());
    Map<Long, Long> pierNumbers = numbers(piers.findAllById(ids(rows,
        StructureInspectionItemEntity::getForestServiceBridgePierId)),
        ForestServiceBridgePierEntity::getForestServiceBridgePierId,
        ForestServiceBridgePierEntity::getPierNumber);

    Map<Long, List<Item>> byInspection = new HashMap<>();
    for (StructureInspectionItemEntity row : rows) {
      InspectionSubsectionCodeEntity subsection = subsectionCodes.get(row.getSubsectionCode());
      String question = questionNames.get(row.getQuestionCode());
      // Legacy's inner joins: an item whose subsection or question is in no table is not printed.
      if (subsection == null || question == null) {
        continue;
      }
      byInspection.computeIfAbsent(row.getInspectionId(), id -> new ArrayList<>()).add(new Item(
          row.getSubsectionCode(), subsection.getDescription(), subsection.getEffectiveDate(),
          row.getQuestionCode(), question, row.getAnswerValue(),
          answerNames.get(row.getAnswerValue()), row.getItemComment(), row.getStringerNumber(),
          spanNumbers.get(row.getForestServiceBridgeSpanId()),
          pierNumbers.get(row.getForestServiceBridgePierId())));
    }
    return byInspection;
  }

  /** Legacy's cursor order: subsection (by its effective date), span, pier, stringer, question. */
  private static List<Item> sorted(List<Item> list) {
    Comparator<Long> nullsLast = Comparator.nullsLast(Comparator.naturalOrder());
    return list.stream().sorted(Comparator
            .comparing(Item::subsectionEffective, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Item::subsectionCode)
            .thenComparing(Item::span, nullsLast)
            .thenComparing(Item::pier, nullsLast)
            .thenComparing(Item::stringer, nullsLast)
            .thenComparing(Item::questionCode))
        .toList();
  }

  /**
   * The items as legacy's procedure returned them: a row per item without a stringer, and a row
   * per stringer of STR or WDC, its questions folded in, written when the stringer, the span or
   * the subsection changes.
   */
  private static List<Row> rows(List<Item> sorted) {
    List<Row> rows = new ArrayList<>();
    Stringer stringer = null;
    for (Item item : sorted) {
      if (stringer != null && stringer.endsBefore(item)) {
        rows.add(stringer.row());
        stringer = null;
      }
      if (item.stringer() == null) {
        rows.add(Row.question(item));
      } else if (STRINGERS.equals(item.subsectionCode())
          || WOOD_CULVERT.equals(item.subsectionCode())) {
        if (stringer == null) {
          stringer = new Stringer(item);
        }
        stringer.add(item);
      } else {
        // A stringer number on any other subsection: legacy printed nothing for it.
        continue;
      }
    }
    if (stringer != null) {
      rows.add(stringer.row());
    }
    return rows;
  }

  /**
   * The subreport's lines: legacy's rows sorted by subsection description, as its template did,
   * with a subsection heading, a span or pier bar and a stringer table's heading where the row
   * before differs.
   */
  private static List<Map<String, String>> printed(List<Row> rows) {
    List<Row> ordered = rows.stream()
        .sorted(Comparator.comparing(Row::subsectionDescription,
            Comparator.nullsFirst(Comparator.naturalOrder())))
        .toList();
    List<Map<String, String>> lines = new ArrayList<>();
    Row previous = null;
    for (Row row : ordered) {
      if (previous == null
          || !Objects.equals(previous.subsectionDescription(), row.subsectionDescription())) {
        lines.add(line(Kind.SUBSECTION, Map.of("TEXT",
            Objects.toString(row.subsectionDescription(), "").toUpperCase(Locale.ROOT))));
      }
      String previousSpan = previous == null ? "" : previous.spanKey();
      if (row.span() != null && !row.spanKey().equals(previousSpan)) {
        lines.add(line(Kind.SPAN, Map.of("TEXT", "Span " + row.span())));
      }
      String previousPier = previous == null ? "" : previous.pierKey();
      if (row.pier() != null && !row.pierKey().equals(previousPier)) {
        lines.add(line(Kind.PIER, Map.of("TEXT", "Pier " + row.pier())));
      }
      boolean afterStringer = previous != null && previous.stringer() != null;
      if (row.kind() == Kind.WDC) {
        if (!afterStringer) {
          lines.add(line(Kind.WDC_HEADING, Map.of()));
        }
        lines.add(row.fields());
      } else if (row.kind() == Kind.STR) {
        boolean newSpan = previous != null && previous.span() != null
            && !previous.span().equals(row.span());
        if (!afterStringer || newSpan) {
          lines.add(line(Kind.STR_HEADING, Map.of()));
        }
        lines.add(row.fields());
      } else if (StringUtils.hasText(row.questionCode())) {
        lines.add(row.fields());
      }
      previous = row;
    }
    return lines;
  }

  private static Map<String, String> line(Kind kind, Map<String, String> fields) {
    Map<String, String> line = new LinkedHashMap<>(fields);
    line.put("KIND", kind.name());
    return line;
  }

  private static List<Long> ids(
      List<StructureInspectionItemEntity> rows, Function<StructureInspectionItemEntity, Long> id) {
    return rows.stream().map(id).filter(Objects::nonNull).distinct().toList();
  }

  private static <E> Map<String, E> byKey(List<E> rows, Function<E, String> key) {
    Map<String, E> map = new HashMap<>();
    rows.forEach(row -> map.put(key.apply(row), row));
    return map;
  }

  private static <E> Map<String, String> names(
      List<E> rows, Function<E, String> key, Function<E, String> name) {
    Map<String, String> map = new HashMap<>();
    rows.forEach(row -> map.put(key.apply(row), name.apply(row)));
    return map;
  }

  private static <E> Map<Long, Long> numbers(
      List<E> rows, Function<E, Long> key, Function<E, Long> number) {
    Map<Long, Long> map = new HashMap<>();
    rows.forEach(row -> map.put(key.apply(row), number.apply(row)));
    return map;
  }

  private static String blank(String value) {
    return value == null ? "" : value;
  }

  private static String number(Long value) {
    return value == null ? "" : value.toString();
  }

  /** One inspection item, with its codes named. */
  private record Item(
      String subsectionCode,
      String subsectionDescription,
      LocalDateTime subsectionEffective,
      String questionCode,
      String questionDescription,
      String answerValue,
      String answerDescription,
      String comment,
      Long stringer,
      Long span,
      Long pier) {

    /** The answer as legacy printed it: its code's description, else the value itself. */
    String answer() {
      return StringUtils.hasText(answerDescription) ? answerDescription : blank(answerValue);
    }

    /** A stringer's measure — all but Peeled — written as its value, as the stringer table does. */
    boolean stringerMeasure() {
      return stringer != null && !"PEEL".equals(questionCode)
          && (STRINGERS.equals(subsectionCode) || WOOD_CULVERT.equals(subsectionCode));
    }
  }

  /** One of legacy's rows: an item, or a stringer with its questions folded in. */
  private record Row(
      Kind kind,
      String subsectionCode,
      String subsectionDescription,
      String questionCode,
      Long stringer,
      Long span,
      Long pier,
      Map<String, String> fields) {

    static Row question(Item item) {
      Kind kind = APPROACH_QUESTIONS.contains(item.questionCode())
          ? Kind.QUESTION_APPROACH
          : Kind.QUESTION;
      return new Row(kind, item.subsectionCode(), item.subsectionDescription(),
          item.questionCode(), null, item.span(), item.pier(), line(kind, Map.of(
              "QUESTION", blank(item.questionDescription()),
              "ANSWER", item.answer(),
              "COMMENT", blank(item.comment()))));
    }

    /** Legacy's span key: the span and the subsection it is in; a WDC stringer row has none. */
    String spanKey() {
      if (span == null || kind == Kind.WDC) {
        return "";
      }
      return span + "-" + (kind == Kind.STR ? STRINGERS : subsectionCode);
    }

    /** Legacy's pier key: the pier and the subsection; a stringer row has none. */
    String pierKey() {
      if (pier == null || kind == Kind.STR || kind == Kind.WDC) {
        return "";
      }
      return pier + "-" + subsectionCode;
    }
  }

  /** A stringer being folded into one row: its questions' answers, and every comment. */
  private static final class Stringer {

    private final String subsectionCode;
    private final String subsectionDescription;
    private final Long number;
    private final Long span;
    private final Map<String, String> fields = new HashMap<>();
    private final List<String> comments = new ArrayList<>();

    Stringer(Item first) {
      subsectionCode = first.subsectionCode();
      subsectionDescription = first.subsectionDescription();
      number = first.stringer();
      span = first.span();
    }

    /** Legacy wrote the row when the stringer, or — for STR — the span changed; here, too, the subsection. */
    boolean endsBefore(Item next) {
      return !Objects.equals(number, next.stringer())
          || !subsectionCode.equals(next.subsectionCode())
          || (STRINGERS.equals(subsectionCode) && !Objects.equals(span, next.span()));
    }

    void add(Item item) {
      String field = switch (item.questionCode()) {
        case "PEEL" -> "PEELED";
        case "RDB" -> "ROT_BTM";
        case "RDS" -> "ROT_SIDE";
        case "RDT" -> "ROT_TOP";
        case "WS", "WSCS" -> "SPECIES";
        case "ASR" -> "AVG_ROT";
        case "DMT", "WXD" -> "DIAMETER";
        default -> null;
      };
      if (field != null) {
        // Peeled is a yes/no answer, printed by its description; the rest are measures.
        fields.put(field, "PEEL".equals(item.questionCode())
            ? blank(item.answerDescription())
            : blank(item.answerValue()));
      }
      if (StringUtils.hasText(item.comment())) {
        comments.add(item.comment());
      }
    }

    Row row() {
      Kind kind = STRINGERS.equals(subsectionCode) ? Kind.STR : Kind.WDC;
      Map<String, String> line = line(kind, fields);
      line.put("STRINGER", number.toString());
      line.put("COMMENT", comments.stream().collect(Collectors.joining(COMMENT_SEPARATOR)));
      return new Row(kind, subsectionCode, subsectionDescription, null, number, span, null, line);
    }
  }
}
