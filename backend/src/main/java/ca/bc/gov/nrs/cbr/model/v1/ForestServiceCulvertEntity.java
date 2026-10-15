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
 * {@code THE.FOREST_SERVICE_CULVERT} — the culvert half of a structure whose type/class is a
 * culvert ({@code CUL} Culvert, or {@code WLC} Wood Log Culvert).
 *
 * <p>Read by Structure Search's filters and the structure page, and written by the Details tab's
 * Culvert card. Reached by subquery, for the reason given on {@link ForestServiceBridgeEntity}.
 */
@Entity
@Table(name = "FOREST_SERVICE_CULVERT", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "forestServiceCulvertId")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ForestServiceCulvertEntity {

  @Id
  @AssignableSequence
  @Column(name = "FOREST_SERVICE_CULVERT_ID")
  private Long forestServiceCulvertId;

  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  /** "Culvert Type" on Structure Search. */
  @Column(name = "ENGINEERED_CULVERT_TYPE_CODE", length = 10)
  private String engineeredCulvertTypeCode;

  @Column(name = "CULVERT_NUMBER")
  private Integer culvertNumber;

  @Column(name = "CULVERT_LENGTH")
  private BigDecimal culvertLength;

  @Column(name = "OPENING_HEIGHT")
  private Long openingHeight;

  @Column(name = "OPENING_WIDTH")
  private Long openingWidth;

  @Column(name = "ENGINEERED_CLVRT_MATERIAL_CODE", length = 10)
  private String engineeredCulvertMaterialCode;

  // The structure page's Culvert section.

  @Column(name = "SLOPE")
  private BigDecimal slope;

  @Column(name = "INLET_COVER_DEPTH")
  private Long inletCoverDepth;

  @Column(name = "OUTLET_COVER_DEPTH")
  private Long outletCoverDepth;

  @Column(name = "CULVERT_MATERIAL_COMMENT", length = 2000)
  private String culvertMaterialComment;

  @Column(name = "HEADWALL_LOCATION_CODE", length = 10)
  private String headwallLocationCode;

  @Column(name = "OPN_BTOM_CLVRT_SUBSTRCTRE_CODE", length = 10)
  private String opnBtomClvrtSubstrctreCode;
}
