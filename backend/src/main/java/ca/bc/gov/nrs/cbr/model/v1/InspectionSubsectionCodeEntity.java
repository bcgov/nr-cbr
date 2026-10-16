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
 * {@code THE.STRUCTURE_INSPCTN_SUBSCTN_CODE} — A group of inspection questions — legacy's subsection, a tab of its inspection form: Approaches,
 * Deck, Superstructure, Stringers, Piers, … Ordered by effective date, then code, as legacy.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_INSPCTN_SUBSCTN_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "subsectionCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionSubsectionCodeEntity {

  @Id
  @Column(name = "STRUCTURE_INSPCTN_SUBSCTN_CODE", length = 10)
  private String subsectionCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;
}
