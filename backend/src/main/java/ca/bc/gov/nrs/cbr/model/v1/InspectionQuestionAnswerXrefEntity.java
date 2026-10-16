package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

/**
 * {@code THE.STRCTRE_INSPCTN_QSTN_ANSR_XREF} — The answers a question may take. A question with none takes typed text.
 */
@Entity
@Immutable
@Table(name = "STRCTRE_INSPCTN_QSTN_ANSR_XREF", schema = "THE")
@IdClass(InspectionQuestionAnswerXrefEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionQuestionAnswerXrefEntity {

  @Id
  @Column(name = "STRUCTURE_INSPCTN_QSTN_CODE", length = 10)
  private String questionCode;

  @Id
  @Column(name = "STRUCTURE_INSPCTN_ANSWER_CODE", length = 10)
  private String answerCode;

  /** The composite key. */
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {
    private String questionCode;
    private String answerCode;
  }
}
