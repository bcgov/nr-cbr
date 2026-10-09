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
 * {@code THE.STRUCTURE_COMMENT} — a comment on a structure: a general one (the Details tab) or a
 * planned-inspection one (the Inspections tab).
 *
 * <p>The structure page reads them and adds and edits planned-inspection ones; a structure delete
 * clears them, with native SQL in {@code StructureService.delete}.
 */
@Entity
@Table(name = "STRUCTURE_COMMENT", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "structureCommentId")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class StructureCommentEntity {

  /**
   * From {@code THE.STRUCTURE_COMMENT_SEQ}, as legacy's {@code INSERT_STRUCTURE_COMMENT} takes it.
   * {@code allocationSize = 1} because the sequence increments by 1 and legacy draws from it too:
   * Hibernate's default of 50 would hand out ids it assumes are reserved and collide with legacy's.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "structureCommentSeq")
  @SequenceGenerator(name = "structureCommentSeq", sequenceName = "THE.STRUCTURE_COMMENT_SEQ",
      allocationSize = 1)
  @Column(name = "STRUCTURE_COMMENT_ID")
  private Long structureCommentId;

  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  @Column(name = "STRUCTURE_COMMENT", length = 2000)
  private String structureComment;

  /** {@code 'Y'} for a planned-inspection comment (the Inspections tab), {@code 'N'} otherwise. */
  @Column(name = "PLANNED_INSPECTION_CMT_IND", length = 1)
  private String plannedInspectionCmtInd;

  @Column(name = "ENTRY_USERID", length = 30)
  private String entryUserid;

  @Column(name = "ENTRY_TIMESTAMP")
  private LocalDateTime entryTimestamp;

  /** Who last wrote it — the IDIR ID the page shows. */
  @Column(name = "UPDATE_USERID", length = 30)
  private String updateUserid;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
