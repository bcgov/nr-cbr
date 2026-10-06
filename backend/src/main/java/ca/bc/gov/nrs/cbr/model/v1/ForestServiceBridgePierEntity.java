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
 * <p><b>Minimal and read-only:</b> the key and the column that ties it to its parent, nothing else.
 * Nothing reads this table yet; a structure delete clears it, with native SQL in
 * {@code StructureService.delete}. Mapped so the test schema has the table without hand-written
 * DDL. The structure screens will map the rest when they arrive.
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
}
