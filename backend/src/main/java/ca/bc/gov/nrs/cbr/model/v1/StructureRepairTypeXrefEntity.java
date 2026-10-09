package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.STRUCTURE_REPAIR_TYPE_XREF} — Which repair types apply to which kind of structure, and
 * the group each falls in.
 *
 * <p>Every column is part of the key. The repair class is in it too, but legacy's type list
 * ignores it (its join is commented out), so a type and group can appear once per class.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_REPAIR_TYPE_XREF", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
public class StructureRepairTypeXrefEntity {

  @EmbeddedId
  private Key id;

  /** The four columns of the key. */
  @Embeddable
  @Getter
  @ToString
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {

    @Column(name = "STRUCTURE_TYPE_CLASS_CODE", length = 10)
    private String structureTypeClassCode;

    @Column(name = "STRUCTURE_REPAIR_TYPE_CODE", length = 10)
    private String structureRepairTypeCode;

    @Column(name = "STRUCTURE_REPAIR_GROUP_CODE", length = 10)
    private String structureRepairGroupCode;

    @Column(name = "STRUCTURE_REPAIR_CLASS_CODE", length = 10)
    private String structureRepairClassCode;
  }
}
