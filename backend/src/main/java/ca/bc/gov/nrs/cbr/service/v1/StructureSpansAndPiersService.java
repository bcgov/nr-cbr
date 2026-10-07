package ca.bc.gov.nrs.cbr.service.v1;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import ca.bc.gov.nrs.cbr.model.v1.PierTypeCodeEntity;
import ca.bc.gov.nrs.cbr.repository.v1.CrossingStructureRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgePierRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeRepository;
import ca.bc.gov.nrs.cbr.repository.v1.ForestServiceBridgeSpanRepository;
import ca.bc.gov.nrs.cbr.repository.v1.PierTypeCodeRepository;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse.Pier;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse.Span;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A bridge's spans and piers — the structure page's Spans &amp; Piers tab, legacy's
 * {@code pierSpanTab.jsp}, fed by {@code FIND_SPANS_BY_BRIDGE_ID} and
 * {@code FIND_PIERS_BY_BRIDGE_ID}.
 *
 * <p>Read-only for now; legacy's Add, Update and Delete (Level 2) come with the page's editing.
 */
@Service
public class StructureSpansAndPiersService {

  private final CrossingStructureRepository structures;
  private final ForestServiceBridgeRepository bridges;
  private final ForestServiceBridgeSpanRepository spans;
  private final ForestServiceBridgePierRepository piers;
  private final PierTypeCodeRepository pierTypes;

  public StructureSpansAndPiersService(
      CrossingStructureRepository structures,
      ForestServiceBridgeRepository bridges,
      ForestServiceBridgeSpanRepository spans,
      ForestServiceBridgePierRepository piers,
      PierTypeCodeRepository pierTypes) {
    this.structures = structures;
    this.bridges = bridges;
    this.spans = spans;
    this.piers = piers;
    this.pierTypes = pierTypes;
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
}
