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
 * {@code THE.PIER_TYPE_CODE} — A bridge pier's type.
 *
 * <p>Read only to decode a stored code on the structure page's Spans &amp; Piers tab; the code and
 * its description are all that tab needs.
 */
@Entity
@Immutable
@Table(name = "PIER_TYPE_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "pierTypeCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PierTypeCodeEntity {

  @Id
  @Column(name = "PIER_TYPE_CODE", length = 10)
  private String pierTypeCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;
}
