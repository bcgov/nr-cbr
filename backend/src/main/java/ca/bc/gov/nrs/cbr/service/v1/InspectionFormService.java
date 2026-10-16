package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceCulvertEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionAnswerCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionAnswerOrderEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionAnswerXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionCriteriaEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionQuestionSubsectionXrefEntity;
import ca.bc.gov.nrs.cbr.model.v1.InspectionSubsectionCodeEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgePierRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeSpanRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceCulvertRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionAnswerCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionAnswerOrderRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionQuestionAnswerXrefRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionQuestionCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionQuestionCriteriaRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionQuestionSubsectionXrefRepository;
import ca.bc.gov.nrs.cbr.repository.v1.InspectionSubsectionCodeRepository;
import ca.bc.gov.nrs.cbr.struct.v1.CodeOptionResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Part;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Question;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Scope;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionFormResponse.Subsection;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The questions an inspection of a structure asks — legacy's {@code FIND_ITEMS_BY_STRUCTURE_TYPE}
 * (CBR.pkg:8382-8472) and its tab rules (inspection.jsp:3229-3256):
 *
 * <ul>
 *   <li>the groups (subsections) the structure's Type/Class is inspected under, by effective date
 *       then code — Piers only when the bridge has piers; Stringers only for a log or timber
 *       stringer superstructure (LGST, TT, TU), Superstructure only for any other;</li>
 *   <li>each group's questions in order, leaving out expired ones and those whose criteria do not
 *       match the structure's superstructure, deck, running surface, abutments or (culvert) open
 *       bottom substructure;</li>
 *   <li>each question's answers, best first; none for a typed answer.</li>
 * </ul>
 *
 * <p>Deck, Superstructure and Stringers are asked of each span, Piers of each pier, the rest once;
 * some Stringers and wood box culvert questions of each stringer (legacy's constants,
 * Inspection.java:58-74).
 */
@Service
public class InspectionFormService {

  static final String STRINGERS = "STR";
  static final String WOOD_BOX_CULVERT = "WDC";
  private static final String SUPERSTRUCTURE = "SS";
  private static final String PIERS = "PIER";
  private static final Set<String> PER_SPAN = Set.of("DECK", SUPERSTRUCTURE, STRINGERS);
  /** Superstructures inspected under Stringers rather than Superstructure. */
  private static final Set<String> STRINGER_SUPERSTRUCTURES = Set.of("LGST", "TT", "TU");
  /** The questions asked of each stringer, by group. */
  static final Map<String, Set<String>> PER_STRINGER = Map.of(
      STRINGERS, Set.of("PEEL", "DMT", "WXD", "RDB", "RDS", "RDT", "WS"),
      WOOD_BOX_CULVERT, Set.of("ASR", "DMT", "WSCS"));

  private final CrossingStructureRepository structures;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceCulvertRepository culverts;
  private final ForestServiceBridgeSpanRepository spans;
  private final ForestServiceBridgePierRepository piers;
  private final InspectionSubsectionCodeRepository subsections;
  private final InspectionQuestionSubsectionXrefRepository questionLinks;
  private final InspectionQuestionCodeRepository questions;
  private final InspectionQuestionCriteriaRepository criteria;
  private final InspectionQuestionAnswerXrefRepository answerLinks;
  private final InspectionAnswerCodeRepository answers;
  private final InspectionAnswerOrderRepository answerOrder;

  @SuppressWarnings("java:S107") // One repository per table the question list is drawn from.
  public InspectionFormService(
      CrossingStructureRepository structures,
      ForestServiceBridgeRepository bridges,
      ForestServiceCulvertRepository culverts,
      ForestServiceBridgeSpanRepository spans,
      ForestServiceBridgePierRepository piers,
      InspectionSubsectionCodeRepository subsections,
      InspectionQuestionSubsectionXrefRepository questionLinks,
      InspectionQuestionCodeRepository questions,
      InspectionQuestionCriteriaRepository criteria,
      InspectionQuestionAnswerXrefRepository answerLinks,
      InspectionAnswerCodeRepository answers,
      InspectionAnswerOrderRepository answerOrder) {
    this.structures = structures;
    this.bridges = bridges;
    this.culverts = culverts;
    this.spans = spans;
    this.piers = piers;
    this.subsections = subsections;
    this.questionLinks = questionLinks;
    this.questions = questions;
    this.criteria = criteria;
    this.answerLinks = answerLinks;
    this.answers = answers;
    this.answerOrder = answerOrder;
  }

  /**
   * The questions for an inspection of the structure.
   *
   * @throws StructureNotFoundException if there is no such structure
   */
  @Transactional(readOnly = true)
  public InspectionFormResponse form(long structureId) {
    CrossingStructureEntity structure = structures.findById(structureId)
        .orElseThrow(() -> new StructureNotFoundException(structureId));
    Optional<ForestServiceBridgeEntity> bridge =
        bridges.findFirstByCrossingStructureIdOrderByForestServiceBridgeId(structureId);
    List<Part> spanParts = bridge.map(found -> spans
            .findByForestServiceBridgeIdOrderBySpanNumberAsc(found.getForestServiceBridgeId())
            .stream()
            .map(span -> new Part(String.valueOf(span.getForestServiceBridgeSpanId()),
                span.getSpanNumber() == null ? null : span.getSpanNumber().longValue()))
            .toList())
        .orElse(List.of());
    List<Part> pierParts = bridge.map(found -> piers
            .findByForestServiceBridgeIdOrderByPierNumberAsc(found.getForestServiceBridgeId())
            .stream()
            .map(pier -> new Part(String.valueOf(pier.getForestServiceBridgePierId()),
                pier.getPierNumber()))
            .toList())
        .orElse(List.of());
    Fit fit = fitOf(structureId, bridge);

    List<InspectionSubsectionCodeEntity> groups =
        subsections.findForTypeClass(structure.getStructureTypeClassCode()).stream()
            .filter(group -> shown(group.getSubsectionCode(), fit, !pierParts.isEmpty()))
            .toList();
    List<InspectionQuestionSubsectionXrefEntity> links = groups.isEmpty()
        ? List.of()
        : questionLinks.findInOrder(
            groups.stream().map(InspectionSubsectionCodeEntity::getSubsectionCode).toList());
    Set<String> questionCodes = links.stream()
        .map(InspectionQuestionSubsectionXrefEntity::getQuestionCode)
        .collect(Collectors.toSet());
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    Map<String, InspectionQuestionCodeEntity> asked = questions.findAllById(questionCodes).stream()
        .filter(question -> question.getExpiryDate() == null
            || now.isBefore(question.getExpiryDate()))
        .collect(Collectors.toMap(InspectionQuestionCodeEntity::getQuestionCode, Function.identity()));
    Map<String, List<InspectionQuestionCriteriaEntity>> rules = criteria
        .findByQuestionCodeIn(questionCodes).stream()
        .collect(Collectors.groupingBy(InspectionQuestionCriteriaEntity::getQuestionCode));
    Map<String, List<CodeOptionResponse>> choices = choicesFor(questionCodes);

    List<Subsection> result = new ArrayList<>();
    for (InspectionSubsectionCodeEntity group : groups) {
      String code = group.getSubsectionCode();
      List<Question> groupQuestions = links.stream()
          .filter(link -> link.getSubsectionCode().equals(code))
          .map(link -> asked.get(link.getQuestionCode()))
          .filter(Objects::nonNull)
          .filter(question -> fit.matchesAny(rules.get(question.getQuestionCode())))
          .map(question -> new Question(question.getQuestionCode(), question.getDescription(),
              PER_STRINGER.getOrDefault(code, Set.of()).contains(question.getQuestionCode()),
              choices.getOrDefault(question.getQuestionCode(), List.of())))
          .toList();
      result.add(new Subsection(code, group.getDescription(), scopeOf(code), groupQuestions));
    }
    return new InspectionFormResponse(String.valueOf(structureId), result, spanParts, pierParts);
  }

  /** Whom a group's questions are asked of. */
  static Scope scopeOf(String subsectionCode) {
    if (PER_SPAN.contains(subsectionCode)) {
      return Scope.SPAN;
    }
    return PIERS.equals(subsectionCode) ? Scope.PIER : Scope.STRUCTURE;
  }

  /** Legacy's tab rules: Piers with piers; Stringers or Superstructure by the superstructure. */
  private static boolean shown(String subsectionCode, Fit fit, boolean hasPiers) {
    boolean stringerBridge = fit.superstructure() != null
        && STRINGER_SUPERSTRUCTURES.contains(fit.superstructure());
    return switch (subsectionCode) {
      case PIERS -> hasPiers;
      case STRINGERS -> stringerBridge;
      case SUPERSTRUCTURE -> !stringerBridge;
      default -> true;
    };
  }

  /** Each question's answers, in legacy's answer order. */
  private Map<String, List<CodeOptionResponse>> choicesFor(Set<String> questionCodes) {
    if (questionCodes.isEmpty()) {
      return Map.of();
    }
    List<InspectionQuestionAnswerXrefEntity> links = answerLinks.findByQuestionCodeIn(questionCodes);
    Map<String, InspectionAnswerCodeEntity> codes = answers.findAllById(links.stream()
            .map(InspectionQuestionAnswerXrefEntity::getAnswerCode).collect(Collectors.toSet()))
        .stream()
        .collect(Collectors.toMap(InspectionAnswerCodeEntity::getAnswerCode, Function.identity()));
    Map<String, Integer> order = answerOrder.findAll().stream()
        .collect(Collectors.toMap(InspectionAnswerOrderEntity::getAnswerCode,
            InspectionAnswerOrderEntity::getOrderBy));
    return links.stream()
        .filter(link -> codes.containsKey(link.getAnswerCode()))
        .sorted(Comparator.comparing(
            (InspectionQuestionAnswerXrefEntity link) -> order.getOrDefault(link.getAnswerCode(),
                Integer.MAX_VALUE)))
        .collect(Collectors.groupingBy(InspectionQuestionAnswerXrefEntity::getQuestionCode,
            Collectors.mapping(link -> new CodeOptionResponse(link.getAnswerCode(),
                codes.get(link.getAnswerCode()).getDescription()), Collectors.toList())));
  }

  private Fit fitOf(long structureId, Optional<ForestServiceBridgeEntity> bridge) {
    String openBottom = culverts.findFirstByCrossingStructureIdOrderByForestServiceCulvertId(
            structureId)
        .map(ForestServiceCulvertEntity::getOpnBtomClvrtSubstrctreCode)
        .orElse(null);
    return bridge
        .map(found -> new Fit(openBottom, found.getSuperstructureTypeCode(),
            found.getDeckTypeCode(), found.getRunningSurfaceCode(), found.getLeftAbutmentCode(),
            found.getRightAbutmentCode()))
        .orElse(new Fit(openBottom, null, null, null, null, null));
  }

  /** The structure's codes a question's criteria are matched against. */
  record Fit(String openBottom, String superstructure, String deckType, String runningSurface,
      String leftAbutment, String rightAbutment) {

    /** Asked when the question has no criteria, or any of its rows matches. */
    boolean matchesAny(List<InspectionQuestionCriteriaEntity> rows) {
      return rows == null || rows.isEmpty() || rows.stream().anyMatch(this::matches);
    }

    /** Every code the row names is the structure's — as legacy's SQL, a null never matches. */
    private boolean matches(InspectionQuestionCriteriaEntity row) {
      return is(row.getOpenBottomCode(), openBottom)
          && is(row.getSuperstructureTypeCode(), superstructure)
          && is(row.getDeckTypeCode(), deckType)
          && is(row.getRunningSurfaceCode(), runningSurface)
          && (row.getAbutmentCode() == null
              || row.getAbutmentCode().equals(leftAbutment)
              || row.getAbutmentCode().equals(rightAbutment));
    }

    private static boolean is(String required, String actual) {
      return required == null || required.equals(actual);
    }
  }
}
