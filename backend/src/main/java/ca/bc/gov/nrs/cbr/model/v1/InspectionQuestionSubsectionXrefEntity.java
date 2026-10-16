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
 * {@code THE.STRCTRE_INSPCTN_QSTN_SBSN_XREF} — Which questions a group asks, in order.
 */
@Entity
@Immutable
@Table(name = "STRCTRE_INSPCTN_QSTN_SBSN_XREF", schema = "THE")
@IdClass(InspectionQuestionSubsectionXrefEntity.Key.class)
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionQuestionSubsectionXrefEntity {

  @Id
  @Column(name = "STRUCTURE_INSPCTN_QSTN_CODE", length = 10)
  private String questionCode;

  @Id
  @Column(name = "STRUCTURE_INSPCTN_SUBSCTN_CODE", length = 10)
  private String subsectionCode;

  /** The question's place in its group. */
  @Column(name = "ORDER_BY")
  private Integer orderBy;

  /** The composite key. */
  @EqualsAndHashCode
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Key implements Serializable {
    private String questionCode;
    private String subsectionCode;
  }
}
