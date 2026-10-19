package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.cbr.struct.v1.InspectionCreateRequest.Item;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Question;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Scope;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Subsection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Legacy's stringer rules, checked of each stringer an inspection answers. */
class StringerRulesTest {

  private static Question question(String code, String description) {
    return new Question(code, description, true, List.of());
  }

  private static final InspectionFormResponse FORM = new InspectionFormResponse("7", List.of(
      new Subsection("STR", "Stringers", Scope.SPAN, List.of(
          question("PEEL", "Peeled"), question("DMT", "Diameter"),
          question("WXD", "Width X Depth"), question("RDB", "Rot Depth (Bottom)"),
          question("RDS", "Rot Depth (Side)"), question("RDT", "Rot Depth (Top)"),
          question("WS", "Wood Species"))),
      new Subsection("WDC", "Wood Box Culvert", Scope.STRUCTURE, List.of(
          question("ASR", "Rot Depth"), question("DMT", "Diameter"),
          question("WSCS", "Species")))),
      List.of(), List.of());

  private static Item stringer(String group, String code, String answer, Long span) {
    return new Item(group, code, span, null, 1L, answer, null);
  }

  private static Map<String, String> check(List<Item> items) {
    Map<String, String> errors = new LinkedHashMap<>();
    StringerRules.check(errors, items, FORM);
    return errors;
  }

  @Test
  @DisplayName("needs a bridge stringer's Peeled, rot depths, species, and diameter or width x depth")
  void bridgeStringer() {
    assertThat(check(List.of(stringer("STR", "PEEL", "Y", 701L)))).containsOnly(
        Map.entry("items.STR.RDB.span-701.stringer-1", "(Stringers) Rot Depth (Bottom) is required."),
        Map.entry("items.STR.RDS.span-701.stringer-1", "(Stringers) Rot Depth (Side) is required."),
        Map.entry("items.STR.RDT.span-701.stringer-1", "(Stringers) Rot Depth (Top) is required."),
        Map.entry("items.STR.WS.span-701.stringer-1", "(Stringers) Wood Species is required."),
        Map.entry("items.STR.DMT.span-701.stringer-1", "Diameter OR Width X Depth is required."));
  }

  @Test
  @DisplayName("takes the diameter or the width x depth, not both")
  void diameterOrWidth() {
    List<Item> complete = List.of(stringer("STR", "PEEL", "Y", 701L),
        stringer("STR", "RDB", "1", 701L), stringer("STR", "RDS", "1", 701L),
        stringer("STR", "RDT", "1", 701L), stringer("STR", "WS", "Fir", 701L));
    List<Item> both = new ArrayList<>(complete);
    both.add(stringer("STR", "DMT", "300", 701L));
    assertThat(check(both)).isEmpty();
    both.add(stringer("STR", "WXD", "200x300", 701L));
    assertThat(check(both)).containsOnly(Map.entry("items.STR.DMT.span-701.stringer-1",
        "Diameter OR Width X Depth must be answered, not both."));
  }

  @Test
  @DisplayName("needs a wood box culvert stringer's three, and ignores stringers left blank")
  void culvertStringer() {
    assertThat(check(List.of(stringer("WDC", "DMT", "300", null)))).containsOnly(
        Map.entry("items.WDC.ASR.stringer-1", "(Wood Box Culvert) Rot Depth is required."),
        Map.entry("items.WDC.WSCS.stringer-1", "(Wood Box Culvert) Species is required."));
    assertThat(check(List.of(stringer("WDC", "DMT", " ", null)))).isEmpty();
  }

  @Test
  @DisplayName("refuses a non-number in a question answered with one, where legacy only warned")
  void numbers() {
    Map<String, String> errors = InspectionCreateValidator.itemErrors(List.of(
        stringer("STR", "PEEL", "Y", 701L), stringer("STR", "RDB", "b", 701L),
        stringer("STR", "RDS", "1.5", 701L), stringer("STR", "RDT", "-2", 701L),
        stringer("STR", "WS", "Fir", 701L), stringer("STR", "WXD", "300x400", 701L)),
        new InspectionFormResponse("7", FORM.subsections(), List.of(
            new InspectionFormResponse.Part("701", 1L)), List.of()));
    assertThat(errors).containsOnly(Map.entry("items.STR.RDB.span-701.stringer-1",
        "(Stringers) Rot Depth (Bottom) must be a number."));
  }
}
