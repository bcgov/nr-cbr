package ca.bc.gov.nrs.cbr.struct.v1;

import java.math.BigDecimal;

/**
 * A span to add, or the new values of one — legacy's span form on the Spans &amp; Piers tab.
 *
 * @param number       {@code SPAN_NUMBER}: 1 to 99999, unique on the bridge
 * @param lengthMetres {@code SPAN_LENGTH}: 0 to 99999.999, at most three decimal places
 */
public record SpanRequest(Long number, BigDecimal lengthMetres) {}
