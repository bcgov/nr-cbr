package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.FOREST_SERVICE_BRIDGE_PIER} — one pier of a bridge.
 *
 * <p><b>Read-only:</b> the structure page's Spans &amp; Piers tab lists these. A structure delete
 * clears the table with native SQL in {@code StructureService.delete}. The audit columns are not
 * mapped; nothing reads them yet.
 */
@Entity
@Immutable
@Table(name = "FOREST_SERVICE_BRIDGE_PIER", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "forestServiceBridgePierId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForestServiceBridgePierEntity {

  @Id
  @Column(name = "FOREST_SERVICE_BRIDGE_PIER_ID")
  private Long forestServiceBridgePierId;

  @Column(name = "FOREST_SERVICE_BRIDGE_ID")
  private Long forestServiceBridgeId;

  @Column(name = "PIER_NUMBER")
  private Long pierNumber;

  /** {@code PIER_TYPE_CODE}. */
  @Column(name = "PIER_TYPE_CODE", length = 10)
  private String pierTypeCode;
}
