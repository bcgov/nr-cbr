package ca.bc.gov.nrs.cbr.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.cbr.exception.FieldValidationException;
import ca.bc.gov.nrs.cbr.exception.PierNotFoundException;
import ca.bc.gov.nrs.cbr.exception.SpanNotFoundException;
import ca.bc.gov.nrs.cbr.exception.SpanPierConflictException;
import ca.bc.gov.nrs.cbr.exception.StructureNotFoundException;
import ca.bc.gov.nrs.cbr.model.v1.CrossingStructureEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgePierEntity;
import ca.bc.gov.nrs.cbr.model.v1.ForestServiceBridgeSpanEntity;
import ca.bc.gov.nrs.cbr.model.v1.PierTypeCodeEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionItemEntity;
import ca.bc.gov.nrs.cbr.security.LoggedUserHelper;
import ca.bc.gov.nrs.cbr.struct.v1.PierRequest;
import ca.bc.gov.nrs.cbr.struct.v1.SpanRequest;
import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse.Pier;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse.Span;
import ca.bc.gov.nrs.cbr.struct.v1.StructureSpansAndPiersResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The Spans &amp; Piers tab's data and its writes, against the database. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StructureSpansAndPiersService.class)
class StructureSpansAndPiersServiceTest {

  @Autowired
  private StructureSpansAndPiersService service;

  @Autowired
  private EntityManager entityManager;

  @MockitoBean
  private LoggedUserHelper loggedUser;

  private static final LocalDateTime LAST_YEAR = LocalDateTime.now().minusYears(1);
  private static final LocalDateTime NEXT_YEAR = LocalDateTime.now().plusYears(1);

  @BeforeEach
  void setUp() {
    when(loggedUser.getLoggedUserId()).thenReturn("IDIR\\EDITOR");
    for (String entity : List.of("StructureInspectionItemEntity", "ForestServiceBridgeSpanEntity",
        "ForestServiceBridgePierEntity", "PierTypeCodeEntity", "ForestServiceBridgeEntity",
        "CrossingStructureEntity")) {
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

  private long givenSpan(long bridgeId, int number, String length) {
    ForestServiceBridgeSpanEntity span = ForestServiceBridgeSpanEntity.builder()
        .forestServiceBridgeId(bridgeId).spanNumber(number).spanLength(new BigDecimal(length))
        .build();
    entityManager.persist(span);
    return span.getForestServiceBridgeSpanId();
  }

  private long givenPier(long bridgeId, long number, String type) {
    ForestServiceBridgePierEntity pier = ForestServiceBridgePierEntity.builder()
        .forestServiceBridgeId(bridgeId).pierNumber(number).pierTypeCode(type).build();
    entityManager.persist(pier);
    return pier.getForestServiceBridgePierId();
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
    givenSpan(70L, 2, "12.500");
    givenSpan(70L, 1, "9.750");

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
    givenPier(70L, 2L, "CRIB");
    givenPier(70L, 1L, "CRIB");

    List<Pier> piers = find(7L).piers();

    assertThat(piers).extracting(Pier::number).containsExactly(1L, 2L);
    assertThat(piers.get(0).type()).isEqualTo(new CodeValue("CRIB", "Timber crib"));
  }

  @Test
  @DisplayName("keeps a pier whose type has no code row, which legacy's inner join would drop")
  void keepsAPierWithAnUnknownType() {
    givenStructure(7L);
    givenBridge(70L, 7L);
    givenPier(70L, 1L, "GONE");

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
    givenSpan(80L, 1, "10.000");
    givenPier(80L, 1L, "CRIB");

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

  /** A structure 7 with bridge 70 of the given Bridge Length, and the types a pier may take. */
  private void givenBridgeOf(String bridgeLength) {
    givenStructure(7L);
    entityManager.persist(ForestServiceBridgeEntity.builder()
        .forestServiceBridgeId(70L).crossingStructureId(7L)
        .totalBridgeLength(bridgeLength == null ? null : new BigDecimal(bridgeLength)).build());
    entityManager.persist(PierTypeCodeEntity.builder().pierTypeCode("CRIB")
        .description("Timber crib").effectiveDate(LAST_YEAR).expiryDate(NEXT_YEAR).build());
    entityManager.persist(PierTypeCodeEntity.builder().pierTypeCode("OLD")
        .description("Retired").effectiveDate(LAST_YEAR).expiryDate(LAST_YEAR).build());
  }

  private void givenInspected(Long spanId, Long pierId) {
    entityManager.persist(StructureInspectionItemEntity.builder()
        .structureInspectionItemId(900L).inspectionId(1L)
        .forestServiceBridgeSpanId(spanId).forestServiceBridgePierId(pierId).build());
  }

  private static SpanRequest span(Long number, String length) {
    return new SpanRequest(number, length == null ? null : new BigDecimal(length));
  }

  private Map<String, String> spanRefused(Long spanId, SpanRequest request) {
    try {
      if (spanId == null) {
        service.createSpan(7L, request);
      } else {
        service.updateSpan(7L, spanId, request);
      }
    } catch (FieldValidationException refused) {
      return refused.getFieldErrors();
    }
    throw new AssertionError("the span was saved");
  }

  private Map<String, String> pierRefused(Long pierId, PierRequest request) {
    try {
      if (pierId == null) {
        service.createPier(7L, request);
      } else {
        service.updatePier(7L, pierId, request);
      }
    } catch (FieldValidationException refused) {
      return refused.getFieldErrors();
    }
    throw new AssertionError("the pier was saved");
  }

  private ForestServiceBridgeSpanEntity storedSpan(String id) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(ForestServiceBridgeSpanEntity.class, Long.parseLong(id));
  }

  private ForestServiceBridgePierEntity storedPier(String id) {
    entityManager.flush();
    entityManager.clear();
    return entityManager.find(ForestServiceBridgePierEntity.class, Long.parseLong(id));
  }

  @Test
  @DisplayName("adds a span to the structure's bridge, recording who added it")
  void createsSpan() {
    givenBridgeOf(null);

    ForestServiceBridgeSpanEntity span = storedSpan(service.createSpan(7L, span(1L, "12.5")));

    assertThat(span.getForestServiceBridgeId()).isEqualTo(70L);
    assertThat(span.getSpanNumber()).isEqualTo(1);
    assertThat(span.getSpanLength()).isEqualByComparingTo("12.5");
    assertThat(span.getEntryUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(span.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(span.getEntryTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("needs a span number above 0 that fits the column and is not taken")
  void refusesSpanNumbers() {
    givenBridgeOf(null);
    givenSpan(70L, 1, "10");

    assertThat(spanRefused(null, span(null, "1")))
        .containsExactly(Map.entry("number", "Span Number is required."));
    assertThat(spanRefused(null, span(0L, "1")))
        .containsExactly(Map.entry("number", "Span Number must be greater than 0."));
    assertThat(spanRefused(null, span(-2L, "1")))
        .containsExactly(Map.entry("number", "Span Number must be greater than 0."));
    assertThat(spanRefused(null, span(100_000L, "1")))
        .containsExactly(Map.entry("number", "Span Number can be at most 99999."));
    assertThat(spanRefused(null, span(1L, "1")))
        .containsExactly(Map.entry("number", "Span Number already exists."));
  }

  @Test
  @DisplayName("needs a length from 0 to 99999.999 with at most three decimals; 0 as legacy")
  void refusesSpanLengths() {
    givenBridgeOf(null);

    assertThat(spanRefused(null, span(1L, null)))
        .containsExactly(Map.entry("lengthMetres", "Length is required."));
    assertThat(spanRefused(null, span(1L, "-1")))
        .containsExactly(Map.entry("lengthMetres", "Length cannot be negative."));
    assertThat(spanRefused(null, span(1L, "1.2345")))
        .containsExactly(Map.entry("lengthMetres", "Length can have at most 3 decimal places."));
    assertThat(spanRefused(null, span(1L, "100000")))
        .containsExactly(Map.entry("lengthMetres", "Length can be at most 99999.999 metres."));
    assertThat(storedSpan(service.createSpan(7L, span(1L, "0"))).getSpanLength())
        .isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("refuses a span that would make the spans longer than the Bridge Length")
  void refusesSpansLongerThanTheBridge() {
    givenBridgeOf("20");
    long first = givenSpan(70L, 1, "9");
    givenSpan(70L, 2, "9");

    assertThat(spanRefused(null, span(3L, "2.5"))).containsExactly(Map.entry("lengthMetres",
        "The spans would total 20.5 metres, more than the Bridge Length of 20 metres."));
    // An edit counts the span's new length in place of its old one.
    assertThat(spanRefused(first, span(1L, "15"))).containsExactly(Map.entry("lengthMetres",
        "The spans would total 24 metres, more than the Bridge Length of 20 metres."));
    service.updateSpan(7L, first, span(1L, "11"));
    assertThat(storedSpan(String.valueOf(first)).getSpanLength()).isEqualByComparingTo("11");
  }

  @Test
  @DisplayName("does not check the spans against a bridge with no Bridge Length")
  void noBridgeLength() {
    givenBridgeOf(null);
    givenSpan(70L, 1, "900");

    assertThat(service.createSpan(7L, span(2L, "900"))).isNotBlank();
  }

  @Test
  @DisplayName("changes a span's number and length, keeping its own number free to reuse")
  void updatesSpan() {
    givenBridgeOf(null);
    long id = givenSpan(70L, 1, "10");

    service.updateSpan(7L, id, span(1L, "12"));
    service.updateSpan(7L, id, span(4L, "12"));

    ForestServiceBridgeSpanEntity span = storedSpan(String.valueOf(id));
    assertThat(span.getSpanNumber()).isEqualTo(4);
    assertThat(span.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
  }

  @Test
  @DisplayName("reaches a span or pier only through its own structure")
  void onlyThroughItsOwnStructure() {
    givenBridgeOf(null);
    givenStructure(8L);
    givenBridge(80L, 8L);
    long otherSpan = givenSpan(80L, 1, "10");
    long otherPier = givenPier(80L, 1L, "CRIB");
    SpanRequest spanChange = span(1L, "5");
    PierRequest pierChange = new PierRequest(1L, "CRIB");

    assertThatThrownBy(() -> service.updateSpan(7L, otherSpan, spanChange))
        .isInstanceOf(SpanNotFoundException.class);
    assertThatThrownBy(() -> service.deleteSpan(7L, otherSpan))
        .isInstanceOf(SpanNotFoundException.class);
    assertThatThrownBy(() -> service.updatePier(7L, otherPier, pierChange))
        .isInstanceOf(PierNotFoundException.class);
    assertThatThrownBy(() -> service.deletePier(7L, otherPier))
        .isInstanceOf(PierNotFoundException.class);
  }

  @Test
  @DisplayName("refuses a span or pier for a structure with no bridge record")
  void noBridgeRecord() {
    givenStructure(7L);
    SpanRequest request = span(1L, "5");

    assertThatThrownBy(() -> service.createSpan(7L, request))
        .isInstanceOf(SpanPierConflictException.class)
        .hasMessageContaining("has no bridge record");
  }

  @Test
  @DisplayName("deletes a span, but not an inspected one, the last, or one the piers need")
  void deletesSpan() {
    givenBridgeOf(null);
    long inspected = givenSpan(70L, 1, "10");
    long second = givenSpan(70L, 2, "10");
    long third = givenSpan(70L, 3, "10");
    givenPier(70L, 1L, "CRIB");
    givenInspected(inspected, null);

    assertThatThrownBy(() -> service.deleteSpan(7L, inspected))
        .isInstanceOf(SpanPierConflictException.class)
        .hasMessageContaining("Span can't be deleted, it is associated with an inspection.");
    service.deleteSpan(7L, third);
    assertThatThrownBy(() -> service.deleteSpan(7L, second))
        .isInstanceOf(SpanPierConflictException.class)
        .hasMessageContaining("Number of spans must be greater than number of piers.");
    assertThat(storedSpan(String.valueOf(third))).isNull();
  }

  @Test
  @DisplayName("will not delete the bridge's last span")
  void keepsTheLastSpan() {
    givenBridgeOf(null);
    long only = givenSpan(70L, 1, "10");

    assertThatThrownBy(() -> service.deleteSpan(7L, only))
        .isInstanceOf(SpanPierConflictException.class)
        .hasMessageContaining("At least one span is required.");
  }

  @Test
  @DisplayName("adds a pier while the spans will still outnumber the piers")
  void createsPier() {
    givenBridgeOf(null);
    givenSpan(70L, 1, "10");
    givenSpan(70L, 2, "10");

    ForestServiceBridgePierEntity pier =
        storedPier(service.createPier(7L, new PierRequest(1L, " CRIB ")));

    assertThat(pier.getForestServiceBridgeId()).isEqualTo(70L);
    assertThat(pier.getPierNumber()).isEqualTo(1L);
    assertThat(pier.getPierTypeCode()).isEqualTo("CRIB");
    assertThat(pier.getEntryUserid()).isEqualTo("IDIR\\EDITOR");
    assertThat(pierRefused(null, new PierRequest(2L, "CRIB")))
        .containsExactly(Map.entry("pier", "Number of spans must be greater than number of piers."));
  }

  @Test
  @DisplayName("needs a pier number above 0 that is not taken, and a current type")
  void refusesPiers() {
    givenBridgeOf(null);
    for (int number = 1; number <= 4; number++) {
      givenSpan(70L, number, "5");
    }
    givenPier(70L, 1L, "CRIB");

    assertThat(pierRefused(null, new PierRequest(null, null))).containsExactly(
        Map.entry("number", "Pier Number is required."),
        Map.entry("typeCode", "Type is required."));
    assertThat(pierRefused(null, new PierRequest(0L, "CRIB")))
        .containsExactly(Map.entry("number", "Pier Number must be greater than 0."));
    assertThat(pierRefused(null, new PierRequest(10_000_000_000L, "CRIB")))
        .containsExactly(Map.entry("number", "Pier Number can be at most 9999999999."));
    assertThat(pierRefused(null, new PierRequest(1L, "CRIB")))
        .containsExactly(Map.entry("number", "Pier Number already exists."));
    assertThat(pierRefused(null, new PierRequest(2L, "OLD")))
        .containsExactly(Map.entry("typeCode", "Type is not one of the listed types."));
    assertThat(pierRefused(null, new PierRequest(2L, "NOPE")))
        .containsExactly(Map.entry("typeCode", "Type is not one of the listed types."));
  }

  @Test
  @DisplayName("lets a pier keep its own type since expired, but not take another expired one")
  void keepsAnExpiredTypeOfItsOwn() {
    givenBridgeOf(null);
    givenSpan(70L, 1, "5");
    givenSpan(70L, 2, "5");
    long id = givenPier(70L, 1L, "OLD");

    service.updatePier(7L, id, new PierRequest(3L, "OLD"));

    ForestServiceBridgePierEntity pier = storedPier(String.valueOf(id));
    assertThat(pier.getPierNumber()).isEqualTo(3L);
    assertThat(pier.getPierTypeCode()).isEqualTo("OLD");
    assertThat(pier.getUpdateUserid()).isEqualTo("IDIR\\EDITOR");
  }

  @Test
  @DisplayName("deletes a pier, but not an inspected one")
  void deletesPier() {
    givenBridgeOf(null);
    givenSpan(70L, 1, "5");
    givenSpan(70L, 2, "5");
    givenSpan(70L, 3, "5");
    long inspected = givenPier(70L, 1L, "CRIB");
    long free = givenPier(70L, 2L, "CRIB");
    givenInspected(null, inspected);

    assertThatThrownBy(() -> service.deletePier(7L, inspected))
        .isInstanceOf(SpanPierConflictException.class)
        .hasMessageContaining("Pier can't be deleted, it is associated with an inspection.");
    service.deletePier(7L, free);
    assertThat(storedPier(String.valueOf(free))).isNull();
  }
}
