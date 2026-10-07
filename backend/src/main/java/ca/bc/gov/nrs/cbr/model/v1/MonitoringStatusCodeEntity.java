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
 * {@code THE.MONITORING_STATUS_CODE} — Where a monitoring item stands.
 *
 * <p>Read only to decode a stored code on the structure page's Monitoring tab.
 */
@Entity
@Immutable
@Table(name = "MONITORING_STATUS_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "monitoringStatusCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoringStatusCodeEntity {

  @Id
  @Column(name = "MONITORING_STATUS_CODE", length = 10)
  private String monitoringStatusCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
