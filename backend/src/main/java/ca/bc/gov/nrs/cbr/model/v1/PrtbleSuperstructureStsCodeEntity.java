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
 * {@code THE.PRTBLE_SUPERSTRUCTURE_STS_CODE} — where a portable superstructure is: in service, in
 * storage. For the Portable Superstructure Status select on the Structure Inventory reports.
 *
 * <p>Read-only, and read whole: expired codes are returned too, for the reason set out on
 * {@link SpecialAccessRequirementCodeEntity} — this list feeds a report filter, and structures
 * recorded years ago still carry codes that have since been retired.
 */
@Entity
@Immutable
@Table(name = "PRTBLE_SUPERSTRUCTURE_STS_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "prtbleSuperstructureStsCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrtbleSuperstructureStsCodeEntity {

  @Id
  @Column(name = "PRTBLE_SUPERSTRUCTURE_STS_CODE", unique = true, length = 10)
  private String prtbleSuperstructureStsCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
