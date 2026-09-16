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
 * {@code THE.CLOSE_PROXIMITY_INSPECTION} — the second table that keys to a crossing site.
 *
 * <p>Mapped for one reason: it is the child legacy forgets. {@code SiteSearchAction.delete} checks
 * for structures, active and inactive, and stops there — but {@code CPI_CS_FK} keys this table to
 * {@code CROSSING_SITE} as well, with no {@code ON DELETE CASCADE}. A site carrying a close-proximity
 * inspection and no structures therefore passes every legacy guard and fails in the database with
 * {@code ORA-02292}, which reaches the user as a stack trace.
 *
 * <p>Counting it alongside the structures turns that into the same refusal the other children get.
 */
@Entity
@Table(name = "CLOSE_PROXIMITY_INSPECTION", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "closeProximityInspectionId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloseProximityInspectionEntity {

  @Id
  @Column(name = "CLOSE_PROXIMITY_INSPECTION_ID")
  private Long closeProximityInspectionId;

  @Column(name = "CROSSING_SITE_ID", length = 14)
  private String crossingSiteId;
}
