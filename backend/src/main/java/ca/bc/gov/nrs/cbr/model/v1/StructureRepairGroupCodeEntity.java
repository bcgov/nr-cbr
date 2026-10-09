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
 * {@code THE.STRUCTURE_REPAIR_GROUP_CODE} — The part of a structure a repair type belongs to:
 * approach, channel, miscellaneous, superstructure, substructure.
 *
 * <p>The Repair Item dialog's checkboxes, which narrow its type list.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_REPAIR_GROUP_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureRepairGroupCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureRepairGroupCodeEntity {

  @Id
  @Column(name = "STRUCTURE_REPAIR_GROUP_CODE", length = 10)
  private String structureRepairGroupCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;
}
