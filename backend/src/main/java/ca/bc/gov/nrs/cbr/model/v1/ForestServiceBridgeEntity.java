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
 * {@code THE.FOREST_SERVICE_BRIDGE} — the bridge half of a structure whose type/class is a bridge
 * ({@code PB} portable, {@code TB} timber).
 *
 * <p><b>Read by Structure Search only, and only the columns it filters on</b> — the superstructure
 * and curb types, and the eight "Incomplete Data?" checks. Reached by subquery on
 * {@link #crossingStructureId} rather than as an association of the structure: the relationship is
 * an optional one-to-one held on this side, and Hibernate cannot load the inverse side lazily, so
 * mapping it would cost a select per result row on every search. The bridge screens will map the
 * rest when they arrive.
 */
@Entity
@Immutable
@Table(name = "FOREST_SERVICE_BRIDGE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "forestServiceBridgeId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForestServiceBridgeEntity {

  @Id
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

  /** {@code 'S'} when a portable superstructure is in service — the abutments then matter. */
  @Column(name = "PRTBLE_SUPERSTRUCTURE_STS_CODE", length = 10)
  private String portableSuperstructureStatusCode;

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
