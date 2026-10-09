package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
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
 * {@code THE.CLOSE_PROXIMITY_INSPECTION} — the second table that keys to a crossing site.
 *
 * <p>Mapped for one reason: it is the child legacy forgets. {@code SiteSearchAction.delete} checks
 * for structures, active and inactive, and stops there — but {@code CPI_CS_FK} keys this table to
 * {@code CROSSING_SITE} as well, with no {@code ON DELETE CASCADE}. A site carrying a close-proximity
 * inspection and no structures therefore passes every legacy guard and fails in the database with
 * {@code ORA-02292}, which reaches the user as a stack trace.
 *
 * <p>Counting it alongside the structures turns that into the same refusal the other children get.
 * It is also the Inspections tab's Completed Close Proximity Inspections, which a P.Eng adds to.
 */
@Entity
@Table(name = "CLOSE_PROXIMITY_INSPECTION", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "closeProximityInspectionId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloseProximityInspectionEntity {

  /**
   * From {@code THE.CLOSE_PROXIMITY_INSPECTION_SEQ}, as legacy's {@code INSERT_CLOSE_PROX_INSP}
   * takes it. {@code allocationSize = 1} because the sequence increments by 1 and legacy draws from
   * it too: Hibernate's default of 50 would hand out ids it assumes are reserved and collide with
   * legacy's.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "closeProximityInspectionSeq")
  @SequenceGenerator(name = "closeProximityInspectionSeq",
      sequenceName = "THE.CLOSE_PROXIMITY_INSPECTION_SEQ", allocationSize = 1)
  @Column(name = "CLOSE_PROXIMITY_INSPECTION_ID")
  private Long closeProximityInspectionId;

  @Column(name = "CROSSING_SITE_ID", length = 14)
  private String crossingSiteId;

  /** When the close proximity inspection was done — listed on the Inspections tab. */
  @Column(name = "COMPLETION_DATE")
  private LocalDate completionDate;

  /** Who recorded it — the tab's IDIR ID. */
  @Column(name = "ENTRY_USERID", length = 30)
  private String entryUserid;

  @Column(name = "ENTRY_TIMESTAMP")
  private LocalDateTime entryTimestamp;

  /**
   * The structure this belongs to. Mapped for one reason: a structure with any of these cannot be
   * deleted — see {@code StructureService.delete}.
   */
  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;
}
