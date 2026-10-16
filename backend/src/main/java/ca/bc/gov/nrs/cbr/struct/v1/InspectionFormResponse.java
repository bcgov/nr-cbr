package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.List;

/**
 * The questions an inspection of a structure asks — legacy's inspection form tabs, as Add
 * Inspection's Condition tab lays them out — and the spans and piers to answer them for.
 *
 * @param structureId  the structure inspected
 * @param subsections  the question groups, in legacy's order
 * @param spans        a bridge's spans, for the groups asked of each span
 * @param piers        a bridge's piers, for the Piers group
 */
public record InspectionFormResponse(
    String structureId,
    List<Subsection> subsections,
    List<Part> spans,
    List<Part> piers) {

  /** Whom a group's questions are asked of. */
  public enum Scope { STRUCTURE, SPAN, PIER }

  /**
   * A group of questions — legacy's subsection.
   *
   * @param code      APP, DECK, SS, STR, PIER, WDC, …
   * @param scope     asked once, or of each span (Deck, Superstructure, Stringers) or pier
   * @param questions in order
   */
  public record Subsection(String code, String description, Scope scope,
      List<Question> questions) {}

  /**
   * One question.
   *
   * @param perStringer asked of each stringer — Stringers' and the wood box culvert's stringer
   *                    questions — rather than once for the group
   * @param answers     the answers it takes, best first; empty when the answer is typed
   */
  public record Question(String code, String description, boolean perStringer,
      List<CodeOptionResponse> answers) {}

  /** A span or pier: its id, and its number as the user knows it. */
  public record Part(String id, Long number) {}
}
