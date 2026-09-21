package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * {@code THE.INSPECTION_REPORT_STATUS} — <b>one row per status transition</b>, not a code table.
 *
 * <p>The distinction is the whole of this class. {@link InspectionReportStatusCodeEntity} is the six
 * codes; this is the history of which code an inspection has held and when. The inspection row
 * itself carries <em>no</em> status column at all — its current status is the most recently inserted
 * row here, which is what {@code CBR.GET_LAST_STATUS_ID} returns and what
 * {@link StructureInspectionEntity#getCurrentStatus()} maps.
 *
 * <p>It is also the only audit trail the inspection workflow has: moving an inspection off
 * {@code RVD} erases the reviewer from the inspection row, and these rows are the only remaining
 * evidence that it was ever reviewed (cbr-workflows.local.md §1,
 * cbr-inspection-reviewer.local.md §4).
 *
 * <p>Not {@code @Immutable}: CBR owns the table and writes to it on every transition
 * ({@code INSERT_INSPECTION_STATUS}). Nothing in the search path writes, which is stated on the
 * service's read-only transaction rather than here.
 */
@Entity
@Table(name = "INSPECTION_REPORT_STATUS", schema = "THE")
@Getter
@ToString
@EqualsAndHashCode(of = "inspectionReportStatusId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionReportStatusEntity {

  /**
   * "The primary key used to uniquely identify each audited inspection report status code for all
   * inspections."
   *
   * <p>Monotonic per inspection, which is what makes {@code MAX(...)} mean "latest" — legacy orders
   * by this id, never by {@link #entryTimestamp}. Two transitions in the same second are still
   * ordered correctly; two rows inserted out of sequence would not be, but nothing inserts them.
   */
  @Id
  @Column(name = "INSPECTION_REPORT_STATUS_ID")
  private Long inspectionReportStatusId;

  @Column(name = "INSPECTION_ID")
  private Long inspectionId;

  /** "Code representing an inspection report status." One of `OFL`, `PRO`, `SUB`, `RVD`, `ACC`, `REJ`. */
  @Column(name = "INSPECTION_REPORT_STATUS_CODE", length = 10)
  private String inspectionReportStatusCode;

  /** "The user that set the inspection status." */
  @Column(name = "ENTRY_USERID", length = 30)
  private String entryUserid;

  /** "The date when a user set the inspection status." */
  @Column(name = "ENTRY_TIMESTAMP")
  private LocalDateTime entryTimestamp;

  /**
   * The decoded status, for the results table's pill.
   *
   * <p>Legacy joins the code table into the search itself
   * ({@code INNER JOIN INSPECTION_REPORT_STATUS_CODE CD}) and selects
   * {@code CD.DESCRIPTION AS INSPECTION_REPORT_STATUS_DESC}. Mapping it as an association keeps that
   * join, and the search fetches it explicitly so a page of results does not become one query per
   * row.
   *
   * <p><b>The join is inner in legacy and optional here.</b> Every status on an inspection has a
   * code-table row, so the two agree in practice; making it optional means a status whose code was
   * removed by a DBA shows as a blank label rather than removing the inspection from the results.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "INSPECTION_REPORT_STATUS_CODE", insertable = false, updatable = false)
  private InspectionReportStatusCodeEntity statusCode;
}
