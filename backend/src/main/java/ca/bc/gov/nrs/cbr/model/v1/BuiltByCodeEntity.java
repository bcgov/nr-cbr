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
 * {@code THE.BUILT_BY_CODE} — Who a structure was built for.
 *
 * <p>Decodes a stored code on the structure page, and lists the current ones for the Details tab's
 * Structure card edit.
 */
@Entity
@Immutable
@Table(name = "BUILT_BY_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "builtByCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuiltByCodeEntity {

  @Id
  @Column(name = "BUILT_BY_CODE", length = 10)
  private String builtByCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;
}
