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
 * {@code THE.STRUCTURE_REPLACEMENT_XREF} — one structure replaced by another, a link over time
 * between two structures at a site.
 *
 * <p>Both columns are structure ids, despite the names, and both are foreign keys to
 * {@code CROSSING_STRUCTURE} ({@code SRX_CRS_REPLACED_FK}, {@code SRX_CRS_REPLACES_FK}). Read-only
 * here: mapped so a structure on either side of a link is refused a delete rather than failing at
 * the constraint, which is what legacy did.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_REPLACEMENT_XREF", schema = "THE")
@IdClass(StructureReplacementXrefEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureReplacementXrefEntity {

  /** The older structure, the one taken out of service. */
  @Id
  @Column(name = "REPLACED_STRUCTURE_NUMBER")
  private Long replacedStructureNumber;

  /** The newer structure, the one that took its place. */
  @Id
  @Column(name = "REPLACES_STRUCTURE_NUMBER")
  private Long replacesStructureNumber;

  /** The composite key. */
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {
    private Long replacedStructureNumber;
    private Long replacesStructureNumber;
  }
}
