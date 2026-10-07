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
 * {@code THE.MONITOR_FREQUENCY_CODE} — How often a monitoring item is to be checked.
 *
 * <p>Read only to decode a stored code on the structure page's Monitoring tab.
 */
@Entity
@Immutable
@Table(name = "MONITOR_FREQUENCY_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "monitorFrequencyCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitorFrequencyCodeEntity {

  @Id
  @Column(name = "MONITOR_FREQUENCY_CODE", length = 10)
  private String monitorFrequencyCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
