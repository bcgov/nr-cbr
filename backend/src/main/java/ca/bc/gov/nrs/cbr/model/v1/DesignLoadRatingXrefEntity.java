package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.STRCTRE_DESGN_LOAD_RATING_XREF} — the design load rating a design vehicle fixes, as
 * legacy's {@code FIND_DVC_LOAD_RATING} reads it. A vehicle with no row here, or with {@code -1},
 * leaves the rating to be typed.
 *
 * <p>The table has no key of its own; the pair is used as one.
 */
@Entity
@Immutable
@Table(name = "STRCTRE_DESGN_LOAD_RATING_XREF", schema = "THE")
@IdClass(DesignLoadRatingXrefEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignLoadRatingXrefEntity {

  @Id
  @Column(name = "DESIGN_VEHICLE_LOAD_CODE", length = 10)
  private String designVehicleLoadCode;

  /** Tonnes GVW, {@code NUMBER(6)}; {@code -1} where the vehicle leaves it to be typed. */
  @Id
  @Column(name = "DESIGN_LOAD_RATING")
  private Long designLoadRating;

  /** The composite key. */
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {
    private String designVehicleLoadCode;
    private Long designLoadRating;
  }
}
