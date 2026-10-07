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
 * {@code THE.STRUCTURE_INSPECTION_REVIEWER} — a P.Eng who may mark an inspection reviewed.
 *
 * <p>Read only for the IDIR ID a reviewed inspection's load rating is shown under, as legacy's
 * {@code FIND_LOAD_RATINGS_BY_STRC_ID} shows it. Who may review is covered in
 * {@code cbr-inspection-reviewer.local.md}.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_INSPECTION_REVIEWER", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "inspectionReviewerId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureInspectionReviewerEntity {

  @Id
  @Column(name = "INSPECTION_REVIEWER_ID")
  private Long inspectionReviewerId;

  @Column(name = "USERID", length = 30)
  private String userid;

  /** With {@link #lastName}, the Inspections tab's Reviewed By. */
  @Column(name = "FIRST_NAME", length = 50)
  private String firstName;

  @Column(name = "LAST_NAME", length = 50)
  private String lastName;
}
