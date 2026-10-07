package ca.bc.gov.nrs.cbr.struct.v1;

import ca.bc.gov.nrs.cbr.struct.v1.StructureDetailResponse.CodeValue;
import java.math.BigDecimal;
import java.util.List;

/**
 * A bridge's spans and piers, for the structure page's Spans &amp; Piers tab —
 * {@code GET /api/v1/structures/{structureId}/spans-and-piers}; legacy's {@code pierSpanTab.jsp}.
 *
 * <p>Both lists are empty for a structure with no bridge row. The frontend's
 * {@code spansAndPiersResponse.ts} is the other half of this contract.
 *
 * @param spans by span number
 * @param piers by pier number
 */
public record StructureSpansAndPiersResponse(List<Span> spans, List<Pier> piers) {

  /** One span: numbered left bank to right bank, looking downstream. */
  public record Span(String id, Integer number, BigDecimal lengthMetres) {}

  /** One pier and its type. */
  public record Pier(String id, Long number, CodeValue type) {}
}
