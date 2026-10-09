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
 * {@code THE.REPAIR_STATUS_CODE} — Where a repair stands: suggested, required, completed or
 * carried forward.
 *
 * <p>Decodes a stored code on the structure page's Repairs tab; the current codes fill the Repair
 * Item dialog's select.
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

  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;
}
