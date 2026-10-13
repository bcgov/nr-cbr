package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * {@code THE.FOREST_SERVICE_BRIDGE_SPAN} — one span of a bridge.
 *
 * <p>Listed, added, changed and deleted on the structure page's Spans &amp; Piers tab, and counted
 * by its Details tab. A structure delete clears the table with native SQL in
 * {@code StructureService.delete}. The audit columns are mapped: this application writes spans.
 */
@Entity
@Table(name = "FOREST_SERVICE_BRIDGE_SPAN", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "forestServiceBridgeSpanId")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ForestServiceBridgeSpanEntity {

  /**
   * From {@code THE.FOREST_SERVICE_BRIDGE_SPAN_SEQ}, as legacy's {@code INSERT_SPAN} takes it.
   * {@code allocationSize = 1} because legacy draws from the sequence too.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bridgeSpanSeq")
  @SequenceGenerator(name = "bridgeSpanSeq", sequenceName = "THE.FOREST_SERVICE_BRIDGE_SPAN_SEQ",
      allocationSize = 1)
  @Column(name = "FOREST_SERVICE_BRIDGE_SPAN_ID")
  private Long forestServiceBridgeSpanId;

  @Column(name = "FOREST_SERVICE_BRIDGE_ID")
  private Long forestServiceBridgeId;

  /** Numbered from the left bank to the right, looking downstream. {@code NUMBER(5)}. */
  @Column(name = "SPAN_NUMBER")
  private Integer spanNumber;

  /** Centre of bearing to centre of bearing, in metres. */
  @Column(name = "SPAN_LENGTH", precision = 8, scale = 3)
  private BigDecimal spanLength;

  @Column(name = "ENTRY_USERID", length = 30)
  private String entryUserid;

  @Column(name = "ENTRY_TIMESTAMP")
  private LocalDateTime entryTimestamp;

  @Column(name = "UPDATE_USERID", length = 30)
  private String updateUserid;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
