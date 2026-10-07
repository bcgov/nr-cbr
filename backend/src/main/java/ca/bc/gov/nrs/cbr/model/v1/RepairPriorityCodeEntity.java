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
 * {@code THE.REPAIR_PRIORITY_CODE} — How urgent a repair is.
 *
 * <p>Read only to decode a stored code on the structure page's Repairs tab.
 */
@Entity
@Immutable
@Table(name = "REPAIR_PRIORITY_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "repairPriorityCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepairPriorityCodeEntity {

  @Id
  @Column(name = "REPAIR_PRIORITY_CODE", length = 10)
  private String repairPriorityCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
