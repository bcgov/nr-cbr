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
 * {@code THE.STRCTRE_INSPCTN_QSTN_CRIT_XREF} — when a question is asked: a row's every non-null code
 * must match the structure, and a question with rows is asked when any row matches. A question with
 * none is always asked. Legacy's {@code FIND_ITEMS_BY_STRUCTURE_TYPE}.
 */
@Entity
@Immutable
@Table(name = "STRCTRE_INSPCTN_QSTN_CRIT_XREF", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "criteriaId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionQuestionCriteriaEntity {

  @Id
  @Column(name = "CRITERIA_ID")
  private Long criteriaId;

  @Column(name = "STRUCTURE_INSPCTN_QSTN_CODE", length = 10)
  private String questionCode;

  /** A culvert's Open Bottom Substructure. */
  @Column(name = "OPN_BTOM_CLVRT_SUBSTRCTRE_CODE", length = 10)
  private String openBottomCode;

  @Column(name = "SUPERSTRUCTURE_TYPE_CODE", length = 10)
  private String superstructureTypeCode;

  @Column(name = "DECK_TYPE_CODE", length = 10)
  private String deckTypeCode;

  @Column(name = "RUNNING_SURFACE_CODE", length = 10)
  private String runningSurfaceCode;

  /** Either abutment. */
  @Column(name = "ABUTMENT_CODE", length = 10)
  private String abutmentCode;
}
