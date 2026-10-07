package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.PierTypeCodeEntity;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse.Pier;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse.Span;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/** The Spans &amp; Piers tab's data, against the database: it is two ordered reads and a decode. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureSpansAndPiersService.class)
class StructureSpansAndPiersServiceTest {

  @Autowired
  private StructureSpansAndPiersService service;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    for (String entity : List.of("ForestServiceBridgeSpanEntity", "ForestServiceBridgePierEntity",
        "PierTypeCodeEntity", "ForestServiceBridgeEntity", "CrossingStructureEntity")) {
      entityManager.createQuery("DELETE FROM " + entity).executeUpdate();
    }
  }

  private void givenStructure(long id) {
    entityManager.persist(CrossingStructureEntity.builder()
        .crossingStructureId(id).crossingStructureName("B" + id).structureTypeClassCode("TB")
        .activeInd("Y").closeProximityInd("N").portableStructureInd("N").build());
  }

  private void givenBridge(long id, long structureId) {
    entityManager.persist(ForestServiceBridgeEntity.builder()
        .forestServiceBridgeId(id).crossingStructureId(structureId).build());
  }

  private void givenSpan(long id, long bridgeId, int number, String length) {
    entityManager.persist(ForestServiceBridgeSpanEntity.builder()
        .forestServiceBridgeSpanId(id).forestServiceBridgeId(bridgeId)
        .spanNumber(number).spanLength(new BigDecimal(length)).build());
  }

  private void givenPier(long id, long bridgeId, long number, String type) {
    entityManager.persist(ForestServiceBridgePierEntity.builder()
        .forestServiceBridgePierId(id).forestServiceBridgeId(bridgeId)
        .pierNumber(number).pierTypeCode(type).build());
  }

  private StructureSpansAndPiersResponse find(long structureId) {
    entityManager.flush();
    entityManager.clear();
    return service.findByStructure(structureId);
  }

  @Test
  @DisplayName("lists a bridge's spans by span number, as FIND_SPANS_BY_BRIDGE_ID orders them")
  void spansBySpanNumber() {
    givenStructure(7L);
    givenBridge(70L, 7L);
    givenSpan(3L, 70L, 2, "12.500");
    givenSpan(4L, 70L, 1, "9.750");

    assertThat(find(7L).spans()).extracting(Span::number, Span::lengthMetres)
        .containsExactly(
            tuple(1, new BigDecimal("9.750")),
            tuple(2, new BigDecimal("12.500")));
  }

  @Test
  @DisplayName("lists its piers by pier number, each type decoded")
  void piersByPierNumberWithTheirType() {
    givenStructure(7L);
    givenBridge(70L, 7L);
    entityManager.persist(PierTypeCodeEntity.builder()
        .pierTypeCode("CRIB").description("Timber crib").build());
    givenPier(5L, 70L, 2L, "CRIB");
    givenPier(6L, 70L, 1L, "CRIB");

    List<Pier> piers = find(7L).piers();

    assertThat(piers).extracting(Pier::number).containsExactly(1L, 2L);
    assertThat(piers.get(0).type()).isEqualTo(new CodeValue("CRIB", "Timber crib"));
  }

  @Test
  @DisplayName("keeps a pier whose type has no code row, which legacy's inner join would drop")
  void keepsAPierWithAnUnknownType() {
    givenStructure(7L);
    givenBridge(70L, 7L);
    givenPier(5L, 70L, 1L, "GONE");

    assertThat(find(7L).piers()).singleElement()
        .extracting(Pier::type).isEqualTo(new CodeValue("GONE", null));
  }

  @Test
  @DisplayName("lists only this structure's bridge's spans and piers")
  void onlyThisBridge() {
    givenStructure(7L);
    givenStructure(8L);
    givenBridge(70L, 7L);
    givenBridge(80L, 8L);
    givenSpan(3L, 80L, 1, "10.000");
    givenPier(5L, 80L, 1L, "CRIB");

    StructureSpansAndPiersResponse response = find(7L);

    assertThat(response.spans()).isEmpty();
    assertThat(response.piers()).isEmpty();
  }

  @Test
  @DisplayName("answers empty lists for a structure with no bridge row")
  void noBridgeRow() {
    givenStructure(7L);

    StructureSpansAndPiersResponse response = find(7L);

    assertThat(response.spans()).isEmpty();
    assertThat(response.piers()).isEmpty();
  }

  @Test
  @DisplayName("refuses a structure that does not exist")
  void missingStructure() {
    assertThatThrownBy(() -> service.findByStructure(404L))
        .isInstanceOf(StructureNotFoundException.class);
  }
}
