package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * {@code THE.STRUCTURE_MONITOR_ITEMS} — A monitoring item raised by an inspection: something to
 * keep an eye on rather than repair.
 *
 * <p>Read by the structure page's Monitoring tab, and cleared by an inspection or structure delete.
 * Its own entry and update audit columns are mapped: this application adds and edits items.
 */
@Entity
@Table(name = "STRUCTURE_MONITOR_ITEMS", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "monitorId")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class StructureMonitorItemEntity {

  /**
   * From {@code THE.STRUCTURE_MONITOR_ITEMS_SEQ}, as legacy's {@code INSERT_MONITOR} takes it.
   * {@code allocationSize = 1} because the sequence increments by 1 and legacy draws from it too:
   * Hibernate's default of 50 would hand out ids it assumes are reserved and collide with legacy's.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "structureMonitorItemsSeq")
  @SequenceGenerator(name = "structureMonitorItemsSeq",
      sequenceName = "THE.STRUCTURE_MONITOR_ITEMS_SEQ", allocationSize = 1)
  @Column(name = "MONITOR_ID")
  private Long monitorId;

  @Column(name = "INSPECTION_ID")
  private Long inspectionId;

  /**
   * The structure this belongs to. Mapped for one reason: a structure with any of these cannot be
   * deleted — see {@code StructureService.delete}.
   */
  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  /** The inspection that raised it, for its date — the Monitoring tab's second sort. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "INSPECTION_ID", insertable = false, updatable = false,
      foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
  @ToString.Exclude
  private StructureInspectionEntity inspection;

  /** Numbered within the structure. */
  @Column(name = "MONITOR_NUMBER")
  private Long monitorNumber;

  @Column(name = "DESCRIPTION", length = 2000)
  private String description;

  @Column(name = "MONITORING_STATUS_CODE", length = 10)
  private String monitoringStatusCode;

  @Column(name = "MONITOR_FREQUENCY_CODE", length = 10)
  private String monitorFrequencyCode;

  /** Free text alongside the frequency. */
  @Column(name = "MONITOR_FREQUENCY_CMT", length = 2000)
  private String monitorFrequencyCmt;

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

  /** Who added it, and when — set once, on insert. */
  @Column(name = "ENTRY_USERID", length = 30)
  private String entryUserid;

  @Column(name = "ENTRY_TIMESTAMP")
  private LocalDateTime entryTimestamp;

  /** Who last changed it — set on insert and on every edit. */
  @Column(name = "UPDATE_USERID", length = 30)
  private String updateUserid;

  @Column(name = "UPDATE_TIMESTAMP")
  private LocalDateTime updateTimestamp;
}
