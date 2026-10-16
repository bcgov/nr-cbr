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
 * {@code THE.STRUCTURE_INSPCTN_ANSWER_CODE} — One answer a question may take — Excellent, Good, Fair, Poor ({@code P}), Unrated
 * ({@code UNR}), …
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_INSPCTN_ANSWER_CODE", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "answerCode")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionAnswerCodeEntity {

  @Id
  @Column(name = "STRUCTURE_INSPCTN_ANSWER_CODE", length = 10)
  private String answerCode;

  @Column(name = "DESCRIPTION", length = 120)
  private String description;

  @Column(name = "EFFECTIVE_DATE")
  private LocalDateTime effectiveDate;

  @Column(name = "EXPIRY_DATE")
  private LocalDateTime expiryDate;
}
