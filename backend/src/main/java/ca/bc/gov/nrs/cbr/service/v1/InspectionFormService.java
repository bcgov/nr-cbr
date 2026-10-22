package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
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
import java.util.HashMap;
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
  private static final String PIER_GROUP = "PIER";
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
    List<Part> spanParts = bridge.map(found -> spanParts(spans
            .findByForestServiceBridgeIdOrderBySpanNumberAsc(found.getForestServiceBridgeId())))
        .orElse(List.of());
    List<Part> pierParts = bridge.map(found -> pierParts(piers
            .findByForestServiceBridgeIdOrderByPierNumberAsc(found.getForestServiceBridgeId())))
        .orElse(List.of());
    String openBottom = culverts.findFirstByCrossingStructureIdOrderByForestServiceCulvertId(
            structureId)
        .map(ForestServiceCulvertEntity::getOpnBtomClvrtSubstrctreCode)
        .orElse(null);
    return build(structureId, fitOf(bridge, openBottom), spanParts, pierParts,
        catalogue(structure.getStructureTypeClassCode()));
  }

  /**
   * The questions for an inspection of each of these structures — a report's blank forms. What a
   * structure's type/class is asked is read once per type/class, and each structure's bridge,
   * spans, piers and culvert a batch at a time, rather than a dozen queries per structure.
   */
  @Transactional(readOnly = true)
  public Map<Long, InspectionFormResponse> forms(List<CrossingStructureEntity> listed) {
    List<Long> ids = listed.stream().map(CrossingStructureEntity::getCrossingStructureId).toList();
    Map<Long, ForestServiceBridgeEntity> firstBridges = new HashMap<>();
    Map<Long, String> openBottoms = new HashMap<>();
    for (List<Long> batch : batches(ids)) {
      bridges.findByCrossingStructureIdInOrderByForestServiceBridgeId(batch)
          .forEach(found -> firstBridges.putIfAbsent(found.getCrossingStructureId(), found));
      culverts.findByCrossingStructureIdInOrderByForestServiceCulvertId(batch)
          .forEach(found -> openBottoms.putIfAbsent(found.getCrossingStructureId(),
              found.getOpnBtomClvrtSubstrctreCode()));
    }
    List<Long> bridgeIds = firstBridges.values().stream()
        .map(ForestServiceBridgeEntity::getForestServiceBridgeId).toList();
    Map<Long, List<ForestServiceBridgeSpanEntity>> spansByBridge = new HashMap<>();
    Map<Long, List<ForestServiceBridgePierEntity>> piersByBridge = new HashMap<>();
    for (List<Long> batch : batches(bridgeIds)) {
      spans.findByForestServiceBridgeIdInOrderBySpanNumberAsc(batch).forEach(span -> spansByBridge
          .computeIfAbsent(span.getForestServiceBridgeId(), id -> new ArrayList<>()).add(span));
      piers.findByForestServiceBridgeIdInOrderByPierNumberAsc(batch).forEach(pier -> piersByBridge
          .computeIfAbsent(pier.getForestServiceBridgeId(), id -> new ArrayList<>()).add(pier));
    }
    Map<String, Catalogue> catalogues = new HashMap<>();
    Map<Long, InspectionFormResponse> result = new HashMap<>();
    for (CrossingStructureEntity structure : listed) {
      Long id = structure.getCrossingStructureId();
      Optional<ForestServiceBridgeEntity> bridge = Optional.ofNullable(firstBridges.get(id));
      Long bridgeId = bridge.map(ForestServiceBridgeEntity::getForestServiceBridgeId).orElse(null);
      String typeClass = structure.getStructureTypeClassCode();
      Catalogue catalogue = typeClass == null
          ? catalogue(null)
          : catalogues.computeIfAbsent(typeClass, this::catalogue);
      result.put(id, build(id, fitOf(bridge, openBottoms.get(id)),
          spanParts(spansByBridge.getOrDefault(bridgeId, List.of())),
          pierParts(piersByBridge.getOrDefault(bridgeId, List.of())), catalogue));
    }
    return result;
  }

  /** A structure's form from its type/class's catalogue: the groups and questions that fit it. */
  private static InspectionFormResponse build(long structureId, Fit fit, List<Part> spanParts,
      List<Part> pierParts, Catalogue catalogue) {
    List<Subsection> result = new ArrayList<>();
    for (InspectionSubsectionCodeEntity group : catalogue.groups()) {
      String code = group.getSubsectionCode();
      if (!shown(code, fit, !pierParts.isEmpty())) {
        continue;
      }
      List<Question> groupQuestions = catalogue.links().stream()
          .filter(link -> link.getSubsectionCode().equals(code))
          .map(link -> catalogue.asked().get(link.getQuestionCode()))
          .filter(Objects::nonNull)
          .filter(question -> fit.matchesAny(catalogue.rules().get(question.getQuestionCode())))
          .map(question -> new Question(question.getQuestionCode(), question.getDescription(),
              PER_STRINGER.getOrDefault(code, Set.of()).contains(question.getQuestionCode()),
              catalogue.choices().getOrDefault(question.getQuestionCode(), List.of())))
          .toList();
      result.add(new Subsection(code, group.getDescription(), scopeOf(code), groupQuestions));
    }
    return new InspectionFormResponse(String.valueOf(structureId), result, spanParts, pierParts);
  }

  /**
   * What a type/class is asked: its groups in order, their questions in order (those not expired),
   * each question's criteria and answers. Which of them a structure gets is {@link #build}'s.
   */
  private Catalogue catalogue(String typeClass) {
    List<InspectionSubsectionCodeEntity> groups = subsections.findForTypeClass(typeClass);
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
    return new Catalogue(groups, links, asked, rules, choicesFor(questionCodes));
  }

  /** A type/class's groups, their questions, and each question's criteria and answers. */
  private record Catalogue(
      List<InspectionSubsectionCodeEntity> groups,
      List<InspectionQuestionSubsectionXrefEntity> links,
      Map<String, InspectionQuestionCodeEntity> asked,
      Map<String, List<InspectionQuestionCriteriaEntity>> rules,
      Map<String, List<CodeOptionResponse>> choices) {}

  private static List<Part> spanParts(List<ForestServiceBridgeSpanEntity> found) {
    return found.stream()
        .map(span -> new Part(String.valueOf(span.getForestServiceBridgeSpanId()),
            span.getSpanNumber() == null ? null : span.getSpanNumber().longValue()))
        .toList();
  }

  private static List<Part> pierParts(List<ForestServiceBridgePierEntity> found) {
    return found.stream()
        .map(pier -> new Part(String.valueOf(pier.getForestServiceBridgePierId()),
            pier.getPierNumber()))
        .toList();
  }

  private static List<List<Long>> batches(List<Long> ids) {
    List<List<Long>> batches = new ArrayList<>();
    for (int from = 0; from < ids.size(); from += StructureInventoryReportSupport.BATCH) {
      batches.add(ids.subList(from, Math.min(ids.size(), from + StructureInventoryReportSupport.BATCH)));
    }
    return batches;
  }

  /** Whom a group's questions are asked of. */
  static Scope scopeOf(String subsectionCode) {
    if (PER_SPAN.contains(subsectionCode)) {
      return Scope.SPAN;
    }
    return PIER_GROUP.equals(subsectionCode) ? Scope.PIER : Scope.STRUCTURE;
  }

  /** Legacy's tab rules: Piers with piers; Stringers or Superstructure by the superstructure. */
  private static boolean shown(String subsectionCode, Fit fit, boolean hasPiers) {
    boolean stringerBridge = fit.superstructure() != null
        && STRINGER_SUPERSTRUCTURES.contains(fit.superstructure());
    return switch (subsectionCode) {
      case PIER_GROUP -> hasPiers;
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

  private static Fit fitOf(Optional<ForestServiceBridgeEntity> bridge, String openBottom) {
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
