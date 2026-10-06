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
 * {@code THE.DESIGN_VEHICLE_LOAD_CODE} — A structure's design vehicle configuration.
 *
 * <p>Read only to decode a stored code on the structure page; the code and its description are all
 * that page needs.
 */
@Entity
@Immutable
@Table(name = "DESIGN_VEHICLE_LOAD_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "designVehicleLoadCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignVehicleLoadCodeEntity {

  @Id
  @Column(name = "DESIGN_VEHICLE_LOAD_CODE", length = 10)
  private String designVehicleLoadCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
