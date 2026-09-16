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

/**
 * {@code THE.CROSSING_STRUCTURE} — a bridge or culvert standing on a crossing site.
 *
 * <p><b>Only enough of it to answer "does this site have any structures?"</b> The real table is wide
 * and is the root of the inspection, repair and monitoring trees; none of that is mapped, because
 * the one thing that reads this today is the guard on deleting a site. The rest arrives with the
 * structure screens.
 *
 * <p>{@code crossingSiteId} is deliberately a plain column rather than an association back to
 * {@link CrossingSiteEntity}. The real foreign key ({@code CRS_CS_FK}) exists in Oracle either way;
 * mapping it as an association here would make the delete guard load a site in order to count its
 * children, which is the wrong way round.
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

  /**
   * {@code 'Y'} or {@code 'N'} — "Determines if the current structure is active or not."
   *
   * <p>An archived structure is still a row, and still holds its site down: archiving is a change of
   * state, not a deletion, and the inspection history hangs off it. Legacy reports the two cases
   * separately when it refuses a site delete, which is why this column is mapped at all.
   */
  @Column(name = "ACTIVE_IND", length = 1)
  private String activeInd;
}
