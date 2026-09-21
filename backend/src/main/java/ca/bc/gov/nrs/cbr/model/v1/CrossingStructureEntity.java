package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * {@code THE.CROSSING_STRUCTURE} — a bridge or culvert standing on a crossing site.
 *
 * <p><b>Only the columns its two readers need.</b> The real table is wide and is the root of the
 * inspection, repair and monitoring trees; most of it is unmapped, and arrives with the structure
 * screens. What is here serves the guard on deleting a site, and Inspection Search — which reaches
 * the site through this table, filters on the structure's name and type, and reads
 * {@code CLOSE_PROXIMITY_IND} for one of its four toggles.
 *
 * <p>{@code crossingSiteId} stays a plain column <em>as well as</em> an association. The column is
 * what the delete guard counts by — loading a site in order to count its children is the wrong way
 * round — while {@link #site} is what a search traverses to reach the site's road, district and
 * project file. Both map {@code CRS_CS_FK}; the association is read-only
 * ({@code insertable = false}) so only one of them can ever write it.
 */
@Entity
@Table(name = "CROSSING_STRUCTURE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "crossingStructureId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrossingStructureEntity {

  @Id
  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  @Column(name = "CROSSING_SITE_ID", length = 14)
  private String crossingSiteId;

  /** "The display name of the structure entity." Labelled "Structure #" on both search screens. */
  @Column(name = "CROSSING_STRUCTURE_NAME", length = 14)
  private String crossingStructureName;

  /**
   * The discriminator: bridge → {@code FOREST_SERVICE_BRIDGE}, culvert →
   * {@code FOREST_SERVICE_CULVERT}. It also decides which inspection subsections and repair types
   * apply (cbr-data-model.local.md §2).
   */
  @Column(name = "STRUCTURE_TYPE_CLASS_CODE", length = 10)
  private String structureTypeClassCode;

  /**
   * {@code 'Y'} or {@code 'N'} — whether a close-proximity inspection is required.
   *
   * <p>On the structure, not on the inspection, which is why Inspection Search has to traverse to
   * get at it.
   */
  @Column(name = "CLOSE_PROXIMITY_IND", length = 1)
  private String closeProximityInd;

  /**
   * {@code 'Y'} or {@code 'N'} — "Determines if the current structure is active or not."
   *
   * <p>An archived structure is still a row, and still holds its site down: archiving is a change of
   * state, not a deletion, and the inspection history hangs off it. Legacy reports the two cases
   * separately when it refuses a site delete, which is why this column is mapped at all.
   */
  @Column(name = "ACTIVE_IND", length = 1)
  private String activeInd;

  /**
   * The structure's current capacity in tons, denormalized from {@code STRUCTURE_LOAD_RATING}.
   *
   * <p>Not a value anything sets directly: it is a copy of whichever rating
   * {@code LoadRatingService.currentLoadRatingId} resolves for this structure, and it is rewritten
   * whenever the set of ratings changes — including when an inspection that supplied one is deleted.
   * {@code null} when the structure has no rating at all.
   */
  @Setter
  @Column(name = "CURRENT_LOAD_RATING")
  private BigDecimal currentLoadRating;

  /**
   * {@code 'Y'} or {@code 'N'} — whether the structure's capacity is unknown.
   *
   * <p>Set to {@code 'Y'} when the rating that was current is removed, which is the first of the two
   * corrections an inspection delete makes to its parent structure.
   */
  @Setter
  @Column(name = "LOAD_RATING_UNKNOWN_INDICATOR", length = 1)
  private String loadRatingUnknownIndicator;

  /** The site the structure stands on <em>now</em> — see the note on {@code crossingSiteId} above. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "CROSSING_SITE_ID", insertable = false, updatable = false)
  private CrossingSiteEntity site;
}
