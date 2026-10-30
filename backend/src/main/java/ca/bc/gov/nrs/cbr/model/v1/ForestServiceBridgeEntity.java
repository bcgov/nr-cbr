package ca.bc.gov.nrs.cbr.model.v1;

import ca.bc.gov.nrs.cbr.model.id.AssignableSequence;
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

/**
 * {@code THE.FOREST_SERVICE_BRIDGE} — the bridge half of a structure whose type/class is a bridge
 * ({@code PB} Durable Bridge, {@code TB} Non-Durable Bridge).
 *
 * <p>Read by Structure Search's filters and the structure page, and written by the Details tab's
 * Bridge card. Reached by subquery on {@link #crossingStructureId} rather than as an association of
 * the structure: the relationship is an optional one-to-one held on this side, and Hibernate cannot
 * load the inverse side lazily, so mapping it would cost a select per result row on every search.
 * Spans and piers hang from {@link #forestServiceBridgeId}.
 */
@Entity
@Table(name = "FOREST_SERVICE_BRIDGE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "forestServiceBridgeId")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ForestServiceBridgeEntity {

  @Id
  @AssignableSequence
  @Column(name = "FOREST_SERVICE_BRIDGE_ID")
  private Long forestServiceBridgeId;

  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  @Column(name = "SUPERSTRUCTURE_TYPE_CODE", length = 10)
  private String superstructureTypeCode;

  @Column(name = "STRUCTURE_CURB_TYPE_CODE", length = 10)
  private String structureCurbTypeCode;

  @Column(name = "TOTAL_BRIDGE_LENGTH")
  private BigDecimal totalBridgeLength;

  @Column(name = "DECK_WIDTH")
  private BigDecimal deckWidth;

  @Column(name = "RUNNING_SURFACE_CODE", length = 10)
  private String runningSurfaceCode;

  @Column(name = "DECK_TYPE_CODE", length = 10)
  private String deckTypeCode;

  /** {@code 'S'} when a portable superstructure is in storage — it then has no abutments. */
  @Column(name = "PRTBLE_SUPERSTRUCTURE_STS_CODE", length = 10)
  private String portableSuperstructureStatusCode;

  /**
   * Where a portable superstructure is — the Structure Inventory – Detailed report's "Location
   * Details". Read only: nothing in this application edits it yet.
   */
  @Column(name = "PORTABLE_SPRSTRCTRE_LOCN_DTL", length = 2000, insertable = false,
      updatable = false)
  private String portableSuperstructureLocationDetail;

  @Column(name = "RIGHT_ABUTMENT_CODE", length = 10)
  private String rightAbutmentCode;

  @Column(name = "LEFT_ABUTMENT_CODE", length = 10)
  private String leftAbutmentCode;

  // The structure page's Bridge section.

  @Column(name = "NEEDLE_BEAM_IND", length = 1)
  private String needleBeamInd;

  @Column(name = "SUPERSTRUCTURE_COMMENT", length = 2000)
  private String superstructureComment;

  @Column(name = "DECK_TYPE_COMMENT", length = 2000)
  private String deckTypeComment;

  @Column(name = "ABUTMENT_COMMENT", length = 2000)
  private String abutmentComment;

  @Column(name = "STRUCTURE_CURB_TYPE_CMT", length = 2000)
  private String structureCurbTypeCmt;
}
