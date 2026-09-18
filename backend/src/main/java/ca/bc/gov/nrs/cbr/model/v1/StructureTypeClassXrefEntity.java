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
 * {@code THE.STRUCTURE_TYPE_CLASS_XREF} — the display order of the structure type/class codes.
 *
 * <p>The table comment is the whole of it: <i>"This table holds the relationship between the
 * structure type class code and the display order value."</i> The same arrangement as
 * {@link CrossingSiteStatusXrefEntity}, and mapped the same way — a sibling entity joined
 * explicitly, so the code table carries no association to a presentation concern.
 *
 * <p>One difference from the site-status xref, and it is in the schema rather than here:
 * {@code DISPLAY_ORDER} is {@code NOT NULL} on this table. The row is still optional, which is what
 * the inner join in
 * {@link ca.bc.gov.nrs.cbr.repository.v1.StructureTypeClassCodeRepository} turns on.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_TYPE_CLASS_XREF", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureTypeClassCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureTypeClassXrefEntity {

  @Id
  @Column(name = "STRUCTURE_TYPE_CLASS_CODE", unique = true, length = 10)
  private String structureTypeClassCode;

  @Column(name = "DISPLAY_ORDER")
  private Integer displayOrder;
}
