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
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.ORG_UNIT} — the ministry org hierarchy, read but not owned by CBR.
 *
 * <p><b>The table, not the {@code CBR_ORG_UNIT} view</b>, and that is a deliberate departure from
 * the legacy site search, which joins the view. The view is a union of six branches and its
 * {@code ORG_UNIT_NO} is therefore not unique across them — an area that is also a business area
 * appears twice. A {@code @ManyToOne} onto a non-unique key is a runtime failure ("More than one row
 * with the given identifier"), not a compile-time one, and it would strike only on whichever site
 * happens to point at such a unit.
 *
 * <p>What the two return differs only in reach: for a site's own org unit — a current district or a
 * retired one — both carry the same code and name. The view additionally omits any org unit that
 * fits none of its branches, where legacy shows a blank District Code; through the table such a site
 * shows its real code instead. That is the whole of the divergence, and it is an improvement.
 *
 * <p>{@link CbrOrgUnitEntity} still reads the view, because the dropdowns need what the view exists
 * to express: which branch a row came from.
 */
@Entity
@Immutable
@Table(name = "ORG_UNIT", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "orgUnitNo")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgUnitEntity {

  @Id
  @Column(name = "ORG_UNIT_NO")
  private Long orgUnitNo;

  /** Shown in the results table's District Code column, e.g. {@code "DCK"}. */
  @Column(name = "ORG_UNIT_CODE")
  private String orgUnitCode;

  /** The District Code column's tooltip — the column is too narrow for the name. */
  @Column(name = "ORG_UNIT_NAME")
  private String orgUnitName;
}
