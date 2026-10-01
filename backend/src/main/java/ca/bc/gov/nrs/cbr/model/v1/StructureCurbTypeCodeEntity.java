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
 * {@code THE.STRUCTURE_CURB_TYPE_CODE} — bridge curb types, for the Curb Type select on
 * Structure Search.
 *
 * <p>Read-only, and read whole: expired codes are returned too, for the reason set out on
 * {@link SpecialAccessRequirementCodeEntity} — this list feeds a search filter, and structures
 * recorded years ago still carry codes that have since been retired.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_CURB_TYPE_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureCurbTypeCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureCurbTypeCodeEntity {

  @Id
  @Column(name = "STRUCTURE_CURB_TYPE_CODE", unique = true, length = 10)
  private String structureCurbTypeCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
