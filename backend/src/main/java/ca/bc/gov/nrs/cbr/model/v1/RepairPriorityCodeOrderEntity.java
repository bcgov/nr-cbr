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
 * {@code THE.REPAIR_PRIORITY_CODE_ORDER} — A repair priority's rank, most pressing first.
 *
 * <p>Read for the order of the Repair Item dialog's Priority list, which leaves out a priority with
 * no row here, and to sort repairs by priority on the structure page's Repairs tab and the Repairs
 * Summary report — as legacy does. One row per priority, as legacy's inner joins assume: nothing
 * but the key enforces it.
 */
@Entity
@Immutable
@Table(name = "REPAIR_PRIORITY_CODE_ORDER", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "repairPriorityCodeOrderId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepairPriorityCodeOrderEntity {

  @Id
  @Column(name = "REPAIR_PRIORITY_CODE_ORDER_ID")
  private Integer repairPriorityCodeOrderId;

  @Column(name = "REPAIR_PRIORITY_CODE", length = 10)
  private String repairPriorityCode;

  /** The priority's rank: lower is more pressing. */
  @Column(name = "REPAIR_PRIORITY_CODE_ORDER")
  private Integer repairPriorityCodeOrder;
}
