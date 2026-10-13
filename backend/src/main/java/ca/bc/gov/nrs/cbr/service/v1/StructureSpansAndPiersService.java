package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.PierNotFoundException;
import ca.bc.gov.nrs.cbr.exception.SpanNotFoundException;
import ca.bc.gov.nrs.cbr.exception.SpanPierConflictException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.PierTypeCodeEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgePierRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeSpanRepository;
import ca.bc.gov.nrs.cbr.repository.v1.PierTypeCodeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.StructureInspectionItemRepository;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.PierRequest;
import ca.bc.gov.nrs.cbr.struct.v1.SpanRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse.Pier;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse.Span;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A bridge's spans and piers — the structure page's Spans &amp; Piers tab, legacy's
 * {@code pierSpanTab.jsp}, fed by {@code FIND_SPANS_BY_BRIDGE_ID} and
 * {@code FIND_PIERS_BY_BRIDGE_ID}.
 *
 * <p>Adds, changes and deletes them as legacy's Add, Update and delete icon do
 * ({@code StructureAction.span} and {@code pier}), with legacy's rules and a few of its own: numbers
 * above 0, a length the column holds, a span or pier reached only through its own structure, and no
 * span that would make the spans longer than the bridge. Every change is written at once, as
 * legacy's.
 */
@Service
public class StructureSpansAndPiersService {

  private static final Logger log = LoggerFactory.getLogger(StructureSpansAndPiersService.class);

  /** {@code SPAN_NUMBER NUMBER(5)}. */
  private static final long SPAN_NUMBER_MAX = 99_999L;
  /** {@code PIER_NUMBER NUMBER(10)}. */
  private static final long PIER_NUMBER_MAX = 9_999_999_999L;
  /** {@code SPAN_LENGTH NUMBER(8,3)}. */
  private static final BigDecimal LENGTH_MAX = new BigDecimal("99999.999");
  private static final int LENGTH_SCALE = 3;

  private static final String NUMBER = "number";
  private static final String LENGTH = "lengthMetres";
  private static final String TYPE = "typeCode";
  /** A refusal of the pier as a whole, not of one of its fields. */
  private static final String PIER = "pier";
  private static final String SPANS_OUTNUMBER_PIERS =
      "Number of spans must be greater than number of piers.";

  private final CrossingStructureRepository structures;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceBridgeSpanRepository spans;
  private final ForestServiceBridgePierRepository piers;
  private final PierTypeCodeRepository pierTypes;
  private final StructureInspectionItemRepository inspectionItems;
  private final LoggedUserHelper loggedUser;

  public StructureSpansAndPiersService(
      CrossingStructureRepository structures,
      ForestServiceBridgeRepository bridges,
      ForestServiceBridgeSpanRepository spans,
      ForestServiceBridgePierRepository piers,
      PierTypeCodeRepository pierTypes,
      StructureInspectionItemRepository inspectionItems,
      LoggedUserHelper loggedUser) {
    this.structures = structures;
    this.bridges = bridges;
    this.spans = spans;
    this.piers = piers;
    this.pierTypes = pierTypes;
    this.inspectionItems = inspectionItems;
    this.loggedUser = loggedUser;
  }

  /**
   * The structure's spans by span number and piers by pier number, as legacy orders them. Both are
   * empty when the structure has no bridge row — a culvert, or a bridge not yet given one.
   *
   * <p>Legacy's pier query inner-joins the pier type, so a pier whose code has no row would vanish
   * there. Here it stays, its code shown without a description.
   *
   * @throws StructureNotFoundException if there is no such structure
   */
  @Transactional(readOnly = true)
  public StructureSpansAndPiersResponse findByStructure(long structureId) {
    if (!structures.existsById(structureId)) {
      throw new StructureNotFoundException(structureId);
    }
    Optional<Long> bridgeId = bridges
        .findFirstByCrossingStructureIdOrderByForestServiceBridgeId(structureId)
        .map(ForestServiceBridgeEntity::getForestServiceBridgeId);
    if (bridgeId.isEmpty()) {
      return new StructureSpansAndPiersResponse(List.of(), List.of());
    }

    List<Span> spanList = spans
        .findByForestServiceBridgeIdOrderBySpanNumberAsc(bridgeId.get()).stream()
        .map(span -> new Span(
            String.valueOf(span.getForestServiceBridgeSpanId()),
            span.getSpanNumber(),
            span.getSpanLength()))
        .toList();

    List<ForestServiceBridgePierEntity> pierRows =
        piers.findByForestServiceBridgeIdOrderByPierNumberAsc(bridgeId.get());
    Map<String, String> descriptions = pierTypes
        .findAllById(pierRows.stream()
            .map(ForestServiceBridgePierEntity::getPierTypeCode)
            .filter(Objects::nonNull)
            .distinct()
            .toList())
        .stream()
        .collect(Collectors.toMap(
            PierTypeCodeEntity::getPierTypeCode, PierTypeCodeEntity::getDescription));
    List<Pier> pierList = pierRows.stream()
        .map(pier -> new Pier(
            String.valueOf(pier.getForestServiceBridgePierId()),
            pier.getPierNumber(),
            pier.getPierTypeCode() == null
                ? CodeValue.NONE
                : new CodeValue(pier.getPierTypeCode(), descriptions.get(pier.getPierTypeCode()))))
        .toList();

    return new StructureSpansAndPiersResponse(spanList, pierList);
  }

  /**
   * Adds a span to the structure's bridge — legacy's Add ({@code INSERT_SPAN}).
   *
   * @return the new span's id
   * @throws StructureNotFoundException if there is no such structure
   * @throws SpanPierConflictException  if the structure has no bridge record
   * @throws FieldValidationException   with a message for each field at fault
   */
  @Transactional
  public String createSpan(long structureId, SpanRequest request) {
    ForestServiceBridgeEntity bridge = bridgeOf(structureId);
    validateSpan(bridge, null, request);
    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    ForestServiceBridgeSpanEntity saved = spans.save(ForestServiceBridgeSpanEntity.builder()
        .forestServiceBridgeId(bridge.getForestServiceBridgeId())
        .spanNumber(request.number().intValue())
        .spanLength(request.lengthMetres())
        .entryUserid(user)
        .entryTimestamp(now)
        .updateUserid(user)
        .updateTimestamp(now)
        .build());
    log.info("Added span {} to structure {}", saved.getForestServiceBridgeSpanId(), structureId);
    return String.valueOf(saved.getForestServiceBridgeSpanId());
  }

  /**
   * Changes a span's number and length — legacy's Update ({@code UPDATE_SPAN}). Allowed for a span
   * an inspection has answered questions about, as legacy: the answers point at its id.
   *
   * @throws SpanNotFoundException    if the structure has no such span
   * @throws FieldValidationException with a message for each field at fault
   */
  @Transactional
  public void updateSpan(long structureId, long spanId, SpanRequest request) {
    ForestServiceBridgeEntity bridge = bridgeOf(structureId);
    ForestServiceBridgeSpanEntity span = spans.findById(spanId)
        .filter(found -> Objects.equals(
            found.getForestServiceBridgeId(), bridge.getForestServiceBridgeId()))
        .orElseThrow(() -> new SpanNotFoundException(structureId, spanId));
    validateSpan(bridge, span, request);
    spans.save(span.toBuilder()
        .spanNumber(request.number().intValue())
        .spanLength(request.lengthMetres())
        .updateUserid(loggedUser.getLoggedUserId())
        .updateTimestamp(LocalDateTime.now(ZoneId.systemDefault()))
        .build());
    log.info("Updated span {} of structure {}", spanId, structureId);
  }

  /**
   * Deletes a span — legacy's delete icon ({@code DELETE_SPAN}). Refused, as legacy, for a span an
   * inspection has answered questions about, for the bridge's last span, and when the spans left
   * would no longer outnumber the piers.
   *
   * @throws SpanNotFoundException     if the structure has no such span
   * @throws SpanPierConflictException with legacy's sentence when the delete is refused
   */
  @Transactional
  public void deleteSpan(long structureId, long spanId) {
    ForestServiceBridgeEntity bridge = bridgeOf(structureId);
    ForestServiceBridgeSpanEntity span = spans.findById(spanId)
        .filter(found -> Objects.equals(
            found.getForestServiceBridgeId(), bridge.getForestServiceBridgeId()))
        .orElseThrow(() -> new SpanNotFoundException(structureId, spanId));
    if (inspectionItems.existsByForestServiceBridgeSpanId(spanId)) {
      throw new SpanPierConflictException(
          "Span can't be deleted, it is associated with an inspection.");
    }
    long left = spans.countByForestServiceBridgeId(bridge.getForestServiceBridgeId()) - 1;
    if (left == 0) {
      throw new SpanPierConflictException("At least one span is required.");
    }
    if (left <= piers.countByForestServiceBridgeId(bridge.getForestServiceBridgeId())) {
      throw new SpanPierConflictException(SPANS_OUTNUMBER_PIERS);
    }
    spans.delete(span);
    log.info("Deleted span {} of structure {}", spanId, structureId);
  }

  /**
   * Adds a pier to the structure's bridge — legacy's Add ({@code INSERT_PIER}). Refused, as legacy,
   * unless the spans will still outnumber the piers with it.
   *
   * @return the new pier's id
   * @throws StructureNotFoundException if there is no such structure
   * @throws SpanPierConflictException  if the structure has no bridge record
   * @throws FieldValidationException   with a message for each field at fault, or keyed
   *     {@code pier} when the bridge has too few spans for another pier
   */
  @Transactional
  public String createPier(long structureId, PierRequest request) {
    ForestServiceBridgeEntity bridge = bridgeOf(structureId);
    Long bridgeId = bridge.getForestServiceBridgeId();
    Map<String, String> errors = new LinkedHashMap<>();
    if (spans.countByForestServiceBridgeId(bridgeId)
        <= piers.countByForestServiceBridgeId(bridgeId) + 1) {
      errors.put(PIER, SPANS_OUTNUMBER_PIERS);
    }
    validatePier(errors, bridgeId, null, request);
    String user = loggedUser.getLoggedUserId();
    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
    ForestServiceBridgePierEntity saved = piers.save(ForestServiceBridgePierEntity.builder()
        .forestServiceBridgeId(bridgeId)
        .pierNumber(request.number())
        .pierTypeCode(request.typeCode().trim())
        .entryUserid(user)
        .entryTimestamp(now)
        .updateUserid(user)
        .updateTimestamp(now)
        .build());
    log.info("Added pier {} to structure {}", saved.getForestServiceBridgePierId(), structureId);
    return String.valueOf(saved.getForestServiceBridgePierId());
  }

  /**
   * Changes a pier's number and type — legacy's Update ({@code UPDATE_PIER}). A type since expired
   * may be kept by the pier that has it.
   *
   * @throws PierNotFoundException    if the structure has no such pier
   * @throws FieldValidationException with a message for each field at fault
   */
  @Transactional
  public void updatePier(long structureId, long pierId, PierRequest request) {
    ForestServiceBridgeEntity bridge = bridgeOf(structureId);
    ForestServiceBridgePierEntity pier = piers.findById(pierId)
        .filter(found -> Objects.equals(
            found.getForestServiceBridgeId(), bridge.getForestServiceBridgeId()))
        .orElseThrow(() -> new PierNotFoundException(structureId, pierId));
    validatePier(new LinkedHashMap<>(), bridge.getForestServiceBridgeId(), pier, request);
    piers.save(pier.toBuilder()
        .pierNumber(request.number())
        .pierTypeCode(request.typeCode().trim())
        .updateUserid(loggedUser.getLoggedUserId())
        .updateTimestamp(LocalDateTime.now(ZoneId.systemDefault()))
        .build());
    log.info("Updated pier {} of structure {}", pierId, structureId);
  }

  /**
   * Deletes a pier — legacy's delete icon ({@code DELETE_PIER}). Refused, as legacy, for a pier an
   * inspection has answered questions about.
   *
   * @throws PierNotFoundException     if the structure has no such pier
   * @throws SpanPierConflictException with legacy's sentence when the delete is refused
   */
  @Transactional
  public void deletePier(long structureId, long pierId) {
    ForestServiceBridgeEntity bridge = bridgeOf(structureId);
    ForestServiceBridgePierEntity pier = piers.findById(pierId)
        .filter(found -> Objects.equals(
            found.getForestServiceBridgeId(), bridge.getForestServiceBridgeId()))
        .orElseThrow(() -> new PierNotFoundException(structureId, pierId));
    if (inspectionItems.existsByForestServiceBridgePierId(pierId)) {
      throw new SpanPierConflictException(
          "Pier can't be deleted, it is associated with an inspection.");
    }
    piers.delete(pier);
    log.info("Deleted pier {} of structure {}", pierId, structureId);
  }

  /** The structure's bridge record, which spans and piers hang from. */
  private ForestServiceBridgeEntity bridgeOf(long structureId) {
    if (!structures.existsById(structureId)) {
      throw new StructureNotFoundException(structureId);
    }
    return bridges.findFirstByCrossingStructureIdOrderByForestServiceBridgeId(structureId)
        .orElseThrow(() -> new SpanPierConflictException("Structure " + structureId
            + " has no bridge record, so it has no spans or piers."));
  }

  /**
   * Legacy's span rules, plus: a number above 0 that fits the column, a length the column holds,
   * and spans that do not add up to more than the Bridge Length — which legacy checked only when
   * the structure was next saved.
   *
   * @param current the span being changed, or null for a new one
   */
  private void validateSpan(ForestServiceBridgeEntity bridge, ForestServiceBridgeSpanEntity current,
      SpanRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    Long bridgeId = bridge.getForestServiceBridgeId();
    Long number = request.number();
    if (number == null) {
      errors.put(NUMBER, "Span Number is required.");
    } else if (number <= 0) {
      errors.put(NUMBER, "Span Number must be greater than 0.");
    } else if (number > SPAN_NUMBER_MAX) {
      errors.put(NUMBER, "Span Number can be at most " + SPAN_NUMBER_MAX + ".");
    } else if ((current == null || !Objects.equals(current.getSpanNumber(), number.intValue()))
        && spans.existsByForestServiceBridgeIdAndSpanNumber(bridgeId, number.intValue())) {
      errors.put(NUMBER, "Span Number already exists.");
    }

    BigDecimal length = request.lengthMetres();
    if (length == null) {
      errors.put(LENGTH, "Length is required.");
    } else if (length.signum() < 0) {
      errors.put(LENGTH, "Length cannot be negative.");
    } else if (length.stripTrailingZeros().scale() > LENGTH_SCALE) {
      errors.put(LENGTH, "Length can have at most " + LENGTH_SCALE + " decimal places.");
    } else if (length.compareTo(LENGTH_MAX) > 0) {
      errors.put(LENGTH, "Length can be at most " + LENGTH_MAX.toPlainString() + " metres.");
    } else if (bridge.getTotalBridgeLength() != null) {
      BigDecimal total = spans.findByForestServiceBridgeIdOrderBySpanNumberAsc(bridgeId).stream()
          .filter(span -> current == null || !span.equals(current))
          .map(ForestServiceBridgeSpanEntity::getSpanLength)
          .filter(Objects::nonNull)
          .reduce(length, BigDecimal::add);
      if (total.compareTo(bridge.getTotalBridgeLength()) > 0) {
        errors.put(LENGTH, "The spans would total " + metres(total)
            + " metres, more than the Bridge Length of "
            + metres(bridge.getTotalBridgeLength()) + " metres.");
      }
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Span cannot be saved", errors);
    }
  }

  /**
   * Legacy's pier rules, plus a number above 0 that fits the column. A new pier takes a current
   * type; a changed one may also keep its own, since expired.
   *
   * @param current the pier being changed, or null for a new one
   */
  private void validatePier(Map<String, String> errors, Long bridgeId,
      ForestServiceBridgePierEntity current, PierRequest request) {
    Long number = request.number();
    if (number == null) {
      errors.put(NUMBER, "Pier Number is required.");
    } else if (number <= 0) {
      errors.put(NUMBER, "Pier Number must be greater than 0.");
    } else if (number > PIER_NUMBER_MAX) {
      errors.put(NUMBER, "Pier Number can be at most " + PIER_NUMBER_MAX + ".");
    } else if ((current == null || !number.equals(current.getPierNumber()))
        && piers.existsByForestServiceBridgeIdAndPierNumber(bridgeId, number)) {
      errors.put(NUMBER, "Pier Number already exists.");
    }

    String type = request.typeCode() == null || request.typeCode().isBlank()
        ? null
        : request.typeCode().trim();
    boolean keepsOwn = current != null && type != null && type.equals(current.getPierTypeCode());
    if (type == null) {
      errors.put(TYPE, "Type is required.");
    } else if (!keepsOwn && !pierTypes.isCurrent(type)) {
      errors.put(TYPE, "Type is not one of the listed types.");
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException("Pier cannot be saved", errors);
    }
  }

  /** A length as the tab shows it: no trailing zeros, never in exponent form. */
  private static String metres(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }
}
