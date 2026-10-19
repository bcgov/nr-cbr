package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest.Item;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Question;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Subsection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Legacy's stringer rules ({@code InspectionAction.stringer} and {@code woodbox}), checked of each
 * stringer an inspection answers: a bridge's needs Peeled, the three rot depths and the species, and
 * its Diameter or its Width X Depth, not both; a wood box culvert's, its rot depth, diameter and
 * species. Messages are keyed as the answer they are about —
 * {@link InspectionCreateValidator#itemKey}.
 */
final class StringerRules {

  private static final Map<String, List<String>> REQUIRED = Map.of(
      "STR", List.of("PEEL", "RDB", "RDS", "RDT", "WS"),
      "WDC", List.of("ASR", "DMT", "WSCS"));
  private static final String STRINGERS = "STR";
  private static final String DIAMETER = "DMT";
  private static final String WIDTH_BY_DEPTH = "WXD";

  private StringerRules() {}

  /** One stringer: its group, span or pier, and number. */
  private record Stringer(String subsectionCode, Long spanId, Long pierId, Long number) {

    Item item(String questionCode) {
      return new Item(subsectionCode, questionCode, spanId, pierId, number, null, null);
    }
  }

  /** Adds a message for each stringer rule the answers break. */
  static void check(Map<String, String> errors, List<Item> items, InspectionFormResponse form) {
    Map<Stringer, Set<String>> answered = items.stream()
        .filter(item -> item.stringerNumber() != null && REQUIRED.containsKey(item.subsectionCode()))
        .filter(item -> InspectionCreateValidator.trimmed(item.answer()) != null)
        .collect(Collectors.groupingBy(
            item -> new Stringer(item.subsectionCode(), item.spanId(), item.pierId(),
                item.stringerNumber()),
            LinkedHashMap::new,
            Collectors.mapping(Item::questionCode, Collectors.toSet())));
    answered.forEach((stringer, codes) -> form.subsections().stream()
        .filter(subsection -> subsection.code().equals(stringer.subsectionCode()))
        .findFirst()
        .ifPresent(subsection -> checkOne(errors, subsection, stringer, codes)));
  }

  private static void checkOne(Map<String, String> errors, Subsection subsection,
      Stringer stringer, Set<String> codes) {
    Map<String, Question> asked = subsection.questions().stream()
        .collect(Collectors.toMap(Question::code, question -> question));
    for (String code : REQUIRED.get(subsection.code())) {
      Question question = asked.get(code);
      if (question != null && !codes.contains(code)) {
        errors.putIfAbsent(InspectionCreateValidator.itemKey(stringer.item(code)),
            label(subsection, question) + " is required.");
      }
    }
    if (STRINGERS.equals(subsection.code()) && asked.containsKey(DIAMETER)
        && asked.containsKey(WIDTH_BY_DEPTH)) {
      boolean diameter = codes.contains(DIAMETER);
      boolean widthByDepth = codes.contains(WIDTH_BY_DEPTH);
      String key = InspectionCreateValidator.itemKey(stringer.item(DIAMETER));
      if (!diameter && !widthByDepth) {
        errors.putIfAbsent(key, "Diameter OR Width X Depth is required.");
      } else if (diameter && widthByDepth) {
        errors.putIfAbsent(key, "Diameter OR Width X Depth must be answered, not both.");
      }
    }
  }

  private static String label(Subsection subsection, Question question) {
    return "(" + Objects.toString(subsection.description(), subsection.code()) + ") "
        + question.description();
  }
}
