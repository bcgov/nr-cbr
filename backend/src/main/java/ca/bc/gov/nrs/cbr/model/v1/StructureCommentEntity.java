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
 * {@code THE.STRUCTURE_COMMENT} — a comment on a structure.
 *
 * <p><b>Read-only.</b> The structure page reads the comments; a structure delete clears them, with
 * native SQL in {@code StructureService.delete}.
 */
@Entity
@Immutable
@Table(name = "STRUCTURE_COMMENT", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureCommentId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureCommentEntity {

  @Id
  @Column(name = "STRUCTURE_COMMENT_ID")
  private Long structureCommentId;

  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  @Column(name = "STRUCTURE_COMMENT", length = 2000)
  private String structureComment;

  /** {@code 'Y'} for a planned-inspection comment (the Inspections tab), {@code 'N'} otherwise. */
  @Column(name = "PLANNED_INSPECTION_CMT_IND", length = 1)
  private String plannedInspectionCmtInd;

  @Column(name = "UPDATE_USERID", length = 30)
  private String updateUserid;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
