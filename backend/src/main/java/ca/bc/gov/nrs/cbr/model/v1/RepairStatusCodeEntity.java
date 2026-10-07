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
 * {@code THE.REPAIR_STATUS_CODE} — Where a repair stands: suggested, required, completed or
 * carried forward.
 *
 * <p>Read only to decode a stored code on the structure page's Repairs tab.
 */
@Entity
@Immutable
@Table(name = "REPAIR_STATUS_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "repairStatusCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepairStatusCodeEntity {

  @Id
  @Column(name = "REPAIR_STATUS_CODE", length = 10)
  private String repairStatusCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
