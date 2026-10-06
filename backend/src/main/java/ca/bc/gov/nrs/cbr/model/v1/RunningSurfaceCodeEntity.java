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
 * {@code THE.RUNNING_SURFACE_CODE} — A bridge deck's running surface.
 *
 * <p>Read only to decode a stored code on the structure page; the code and its description are all
 * that page needs.
 */
@Entity
@Immutable
@Table(name = "RUNNING_SURFACE_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "runningSurfaceCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunningSurfaceCodeEntity {

  @Id
  @Column(name = "RUNNING_SURFACE_CODE", length = 10)
  private String runningSurfaceCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
