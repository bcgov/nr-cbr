package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.FOREST_SERVICE_BRIDGE_SPAN} — one span of a bridge.
 *
 * <p><b>Read-only:</b> the structure page's Spans &amp; Piers tab lists these, and its Details tab
 * counts them. A structure delete clears the table with native SQL in
 * {@code StructureService.delete}. The audit columns are not mapped; nothing reads them yet.
 */
@Entity
@Immutable
@Table(name = "FOREST_SERVICE_BRIDGE_SPAN", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "forestServiceBridgeSpanId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForestServiceBridgeSpanEntity {

  @Id
  @Column(name = "FOREST_SERVICE_BRIDGE_SPAN_ID")
  private Long forestServiceBridgeSpanId;

  @Column(name = "FOREST_SERVICE_BRIDGE_ID")
  private Long forestServiceBridgeId;

  /** Numbered from the left bank to the right, looking downstream. */
  @Column(name = "SPAN_NUMBER")
  private Integer spanNumber;

  /** Centre of bearing to centre of bearing, in metres. */
  @Column(name = "SPAN_LENGTH", precision = 8, scale = 3)
  private BigDecimal spanLength;
}
