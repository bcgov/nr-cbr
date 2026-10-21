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
 * {@code THE.STRUCTURE_REPAIR_CLASS_CODE} — whether a repair type bears on the structure's safety:
 * legacy's "Structural Safety", the class {@link StructureRepairTypeXrefEntity} gives each repair
 * type.
 *
 * <p>Read only, for the Structural Safety filter on the Repairs Summary report.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_REPAIR_CLASS_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureRepairClassCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureRepairClassCodeEntity {

  @Id
  @Column(name = "STRUCTURE_REPAIR_CLASS_CODE", length = 10)
  private String structureRepairClassCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;
}
