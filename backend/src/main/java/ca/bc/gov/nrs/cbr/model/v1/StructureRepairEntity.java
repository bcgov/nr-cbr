package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * {@code THE.STRUCTURE_REPAIR} — A repair raised by an inspection.
 *
 * <p>Read by the structure page's Repairs tab, and cleared by an inspection or structure delete.
 * Its own entry and update audit columns are mapped: this application edits repairs.
 */
@Entity
@Table(name = "STRUCTURE_REPAIR", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "repairId")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class StructureRepairEntity {

  @Id
  @Column(name = "REPAIR_ID")
  private Long repairId;

  @Column(name = "INSPECTION_ID")
  private Long inspectionId;

  /**
   * The structure this belongs to. Mapped for one reason: a structure with any of these cannot be
   * deleted — see {@code StructureService.delete}.
   */
  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  /** The inspection that raised it, for its date — the Repairs tab's first sort. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "INSPECTION_ID", insertable = false, updatable = false,
      foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
  @ToString.Exclude
  private StructureInspectionEntity inspection;

  /** Numbered within the structure. */
  @Column(name = "REPAIR_NUMBER")
  private Long repairNumber;

  @Column(name = "REPAIR_STATUS_CODE", length = 10)
  private String repairStatusCode;

  @Column(name = "REPAIR_PRIORITY_CODE", length = 10)
  private String repairPriorityCode;

  @Column(name = "STRUCTURE_REPAIR_TYPE_CODE", length = 10)
  private String structureRepairTypeCode;

  @Column(name = "DESCRIPTION", length = 2000)
  private String description;

  @Column(name = "REPAIR_QUANTITY")
  private Long repairQuantity;

  /** Whole dollars. */
  @Column(name = "ESTIMATE")
  private Long estimate;

  /** Whole dollars. */
  @Column(name = "ACTUAL_COST")
  private Long actualCost;

  @Column(name = "COMPLETED_DATE")
  private LocalDate completedDate;

  /** {@code Y} once the repair was carried forward to a later inspection. */
  @Column(name = "CARRIED_FORWARD_IND", length = 1)
  private String carriedForwardInd;

  @Column(name = "SUGGESTED_BY_USERID", length = 30)
  private String suggestedByUserid;

  @Column(name = "SUGGESTED_BY_TIMESTAMP")
  private LocalDateTime suggestedByTimestamp;

  @Column(name = "REQUIRED_BY_USERID", length = 30)
  private String requiredByUserid;

  @Column(name = "REQUIRED_BY_TIMESTAMP")
  private LocalDateTime requiredByTimestamp;

  @Column(name = "COMPLETED_BY_USERID", length = 30)
  private String completedByUserid;

  @Column(name = "COMPLETED_BY_TIMESTAMP")
  private LocalDateTime completedByTimestamp;

  @Column(name = "ENTRY_USERID", length = 30)
  private String entryUserid;

  @Column(name = "ENTRY_TIMESTAMP")
  private LocalDateTime entryTimestamp;

  @Column(name = "UPDATE_USERID", length = 30)
  private String updateUserid;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
