package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * {@code THE.STRUCTURE_INSPECTION_REVIEWER} — a P.Eng who has reviewed inspections: the reviewer a
 * reviewed inspection is bound to, and the IDIR ID its load rating is shown under, as legacy's
 * {@code FIND_LOAD_RATINGS_BY_STRC_ID} shows it.
 *
 * <p>Legacy's administrator kept these rows, and an active one let its P.Eng review. FAM's
 * {@code CBR_INSPECTION_REVIEWER} role does that now; a row is found, or added, when its P.Eng
 * reviews ({@code cbr-inspection-reviewer.local.md} §10).
 */
@Entity
@Table(name = "STRUCTURE_INSPECTION_REVIEWER", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "inspectionReviewerId")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class StructureInspectionReviewerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inspectionReviewerSeq")
  @SequenceGenerator(name = "inspectionReviewerSeq",
      sequenceName = "THE.STRCTRE_INSPCTN_REVIEWER_SEQ", allocationSize = 1)
  @Column(name = "INSPECTION_REVIEWER_ID")
  private Long inspectionReviewerId;

  @Column(name = "USERID", length = 30)
  private String userid;

  /** With {@link #lastName}, the Inspections tab's Reviewed By. */
  @Column(name = "FIRST_NAME", length = 50)
  private String firstName;

  @Column(name = "LAST_NAME", length = 50)
  private String lastName;

  /**
   * {@code 'Y'} while legacy's administrator let the reviewer review; set when one reviews. No
   * longer decides who may — the role does.
   */
  @Column(name = "ACTIVE_IND", length = 1)
  private String activeInd;

  @Column(name = "ENTRY_USERID", length = 30)
  private String entryUserid;

  @Column(name = "ENTRY_TIMESTAMP")
  private LocalDateTime entryTimestamp;

  @Column(name = "UPDATE_USERID", length = 30)
  private String updateUserid;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
