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
 * {@code THE.STRUCTURE_SOURCE_CODE} — Where a structure's record came from.
 *
 * <p>Decodes a stored code on the structure page, and lists the current ones for the Details tab's
 * Structure card edit.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_SOURCE_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureSourceCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureSourceCodeEntity {

  @Id
  @Column(name = "STRUCTURE_SOURCE_CODE", length = 10)
  private String structureSourceCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;
}
