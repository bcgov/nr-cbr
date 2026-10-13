package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * {@code THE.FOREST_SERVICE_BRIDGE_PIER} — one pier of a bridge.
 *
 * <p>Listed, added, changed and deleted on the structure page's Spans &amp; Piers tab. A structure
 * delete clears the table with native SQL in {@code StructureService.delete}. The audit columns are
 * mapped: this application writes piers.
 */
@Entity
@Table(name = "FOREST_SERVICE_BRIDGE_PIER", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "forestServiceBridgePierId")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ForestServiceBridgePierEntity {

  /**
   * From {@code THE.FOREST_SERVICE_BRIDGE_PIER_SEQ}, as legacy's {@code INSERT_PIER} takes it.
   * {@code allocationSize = 1} because legacy draws from the sequence too.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bridgePierSeq")
  @SequenceGenerator(name = "bridgePierSeq", sequenceName = "THE.FOREST_SERVICE_BRIDGE_PIER_SEQ",
      allocationSize = 1)
  @Column(name = "FOREST_SERVICE_BRIDGE_PIER_ID")
  private Long forestServiceBridgePierId;

  @Column(name = "FOREST_SERVICE_BRIDGE_ID")
  private Long forestServiceBridgeId;

  /** {@code NUMBER(10)}. */
  @Column(name = "PIER_NUMBER")
  private Long pierNumber;

  /** {@code PIER_TYPE_CODE}. */
  @Column(name = "PIER_TYPE_CODE", length = 10)
  private String pierTypeCode;

  @Column(name = "ENTRY_USERID", length = 30)
  private String entryUserid;

  @Column(name = "ENTRY_TIMESTAMP")
  private LocalDateTime entryTimestamp;

  @Column(name = "UPDATE_USERID", length = 30)
  private String updateUserid;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
