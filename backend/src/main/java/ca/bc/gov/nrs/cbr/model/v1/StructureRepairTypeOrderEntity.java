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
 * {@code THE.STRUCTURE_REPAIR_TYPE_ORDER} — A repair type's place in lists, and the unit its
 * quantity is counted in.
 *
 * <p>Read for the unit only, on the structure page's Repairs tab — legacy's {@code REPAIR_UNITS}.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_REPAIR_TYPE_ORDER", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureRepairTypeCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureRepairTypeOrderEntity {

  @Id
  @Column(name = "STRUCTURE_REPAIR_TYPE_CODE", length = 10)
  private String structureRepairTypeCode;

  /** What a repair of this type is counted in, e.g. metres. */
  @Column(name = "STRUCTURE_REPAIR_UNIT", length = 20)
  private String structureRepairUnit;
}
