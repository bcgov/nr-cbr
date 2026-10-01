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
 * {@code THE.FOREST_SERVICE_CULVERT} — the culvert half of a structure whose type/class is a
 * culvert ({@code CUL}, or {@code WLC} wildlife culvert).
 *
 * <p>Read by Structure Search only, and only the columns it filters on — the culvert type, and the
 * six "Incomplete Data?" checks. Reached by subquery, for the reason given on
 * {@link ForestServiceBridgeEntity}.
 */
@Entity
@Immutable
@Table(name = "FOREST_SERVICE_CULVERT", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "forestServiceCulvertId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForestServiceCulvertEntity {

  @Id
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
}
