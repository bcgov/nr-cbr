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
 * {@code THE.STRUCTURE_REPAIR_TYPE_CODE} — What kind of repair it is.
 *
 * <p>Read only to decode a stored code on the structure page's Repairs tab.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_REPAIR_TYPE_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureRepairTypeCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureRepairTypeCodeEntity {

  @Id
  @Column(name = "STRUCTURE_REPAIR_TYPE_CODE", length = 10)
  private String structureRepairTypeCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
