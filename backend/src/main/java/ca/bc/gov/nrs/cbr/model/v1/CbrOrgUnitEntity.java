package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.CBR_ORG_UNIT} — <b>a view, not a table</b>, over the ministry {@code ORG_UNIT}
 * hierarchy.
 *
 * <p>CBR reads {@code ORG_UNIT} and owns none of it (cbr-data-model.local.md §6). The view is a
 * union of six branches, each stamping an {@code ORG_UNIT_TYPE}: business areas ({@code T}), current
 * districts ({@code D}), regions ({@code R}), <b>obsolete districts ({@code O})</b>, the three
 * hard-coded areas ({@code A}), and recreation districts ({@code RD}). The two lookups here read
 * the {@code D} and {@code O} branches, and both are keyed by a real {@code ORG_UNIT_NO}, which is
 * why mapping it as the identifier is safe — the {@code RD} branch synthesises ids
 * ({@code 1000000000 + ROWNUM}) and would not be.
 *
 * <p>Every branch except the obsolete one filters to currently-effective rows, so the view is
 * already time-filtered before any query here adds a predicate of its own. That is not incidental;
 * see {@link ca.bc.gov.nrs.cbr.repository.v1.CbrOrgUnitRepository#findManagementAreas(Long)}, where
 * it is the difference between a correct query and one that looks broken.
 *
 * <p>Only the columns the two queries need are mapped. The view also exposes the rollup
 * area/region/district codes; they are left off until something reads them, rather than mapped on
 * the chance it might.
 */
@Entity
@Immutable
@Table(name = "CBR_ORG_UNIT", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "orgUnitNo")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CbrOrgUnitEntity {

  @Id
  @Column(name = "ORG_UNIT_NO")
  private Long orgUnitNo;

  /** Three characters for a district, e.g. {@code "DCK"}. Two of the queries key off its length. */
  @Column(name = "ORG_UNIT_CODE")
  private String orgUnitCode;

  @Column(name = "ORG_UNIT_NAME")
  private String orgUnitName;

  /** {@code D} district, {@code T} business area, {@code R} region, {@code RD} recreation. */
  @Column(name = "ORG_LEVEL_CODE")
  private String orgLevelCode;

  /**
   * Which branch of the view produced this row — the closest thing it has to a discriminator.
   *
   * <p>Not a column of {@code ORG_UNIT}: the view stamps it as a literal per branch, so it says
   * <em>why</em> a row is in the result rather than what the row is. That distinction is the whole
   * point of both queries in
   * {@link ca.bc.gov.nrs.cbr.repository.v1.CbrOrgUnitRepository} — {@code D} is a district in use,
   * {@code O} is one that has been retired into another, and the two are the same kind of thing
   * with the same {@code ORG_LEVEL_CODE}.
   */
  @Column(name = "ORG_UNIT_TYPE")
  private String orgUnitType;

  /**
   * The district this row rolls up into — equal to {@link #orgUnitNo} for a district that is its
   * own rollup, and for an obsolete district the <em>current</em> district that absorbed it.
   */
  @Column(name = "ROLLUP_DIST_NO")
  private Long rollupDistNo;

  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;
}
