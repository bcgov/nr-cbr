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
 * {@code THE.CROSSING_SITE_TYPE_CODE} — the kinds of crossing site.
 *
 * <p>Same shape, schema qualification and read-only treatment as
 * {@link CrossingSiteStatusCodeEntity}, which documents why each of those is the way it is.
 */
@Entity
@Immutable
@Table(name = "CROSSING_SITE_TYPE_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "crossingSiteTypeCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrossingSiteTypeCodeEntity {

  /** "Code representing a crossing site type." */
  @Id
  @Column(name = "CROSSING_SITE_TYPE_CODE", unique = true, length = 10)
  private String crossingSiteTypeCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  /**
   * Mapped but not filtered on — the legacy procedure returns expired codes too, because this list
   * feeds a search filter rather than a data-entry form. See
   * {@link ca.bc.gov.nrs.cbr.repository.v1.CrossingSiteStatusCodeRepository}.
   */
  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
