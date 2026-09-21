package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
 * {@code THE.STRUCTURE_LOAD_RATING} — one recorded load rating for a structure.
 *
 * <p>A rating arrives one of two ways, and which one it is decides how it competes with the others:
 *
 * <ul>
 *   <li><b>From an inspection</b> — {@link #inspectionId} is set, and the rating counts only once
 *       that inspection has been reviewed.</li>
 *   <li><b>Entered by hand</b> — {@link #inspectionId} is {@code null}, and the rating counts from
 *       the moment it was entered.</li>
 * </ul>
 *
 * <p>That distinction is the whole of {@code LoadRatingService.currentLoadRatingId}, which is what
 * reads this table.
 */
@Entity
@Table(name = "STRUCTURE_LOAD_RATING", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureLoadRatingId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureLoadRatingEntity {

  @Id
  @Column(name = "STRUCTURE_LOAD_RATING_ID")
  private Long structureLoadRatingId;

  /** "The capacity in tons." {@code NUMBER(6)}, and copied onto the structure when it is current. */
  @Column(name = "LOAD_RATING")
  private BigDecimal loadRating;

  /** The structure rated. */
  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  /** The inspection that produced it, or {@code null} for a manually entered rating. */
  @Column(name = "INSPECTION_ID")
  private Long inspectionId;

  /**
   * When the rating was entered.
   *
   * <p>Only meaningful for a manual rating: it is what a manual rating is ordered by, and what it
   * is compared against an inspection's date with.
   */
  @Column(name = "ENTRY_TIMESTAMP")
  private LocalDateTime entryTimestamp;
}
