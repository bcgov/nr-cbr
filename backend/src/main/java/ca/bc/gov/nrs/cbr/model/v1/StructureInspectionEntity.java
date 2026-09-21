package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JoinColumnOrFormula;
import org.hibernate.annotations.JoinFormula;

/**
 * {@code THE.STRUCTURE_INSPECTION} — one inspection of one structure.
 *
 * <p><b>Only the columns Inspection Search reads.</b> The real table is wide — channel measurements,
 * five hazard indicators, costs, comments — and the inspection form engine hangs off it through
 * {@code INSPECTION_ITEM}. None of that is mapped, because the only thing reading this today is the
 * search. The rest arrives with the inspection screens.
 *
 * <p>Not {@code @Immutable}: CBR owns the table and will write to it. The constraint that searching
 * never writes is stated on
 * {@link ca.bc.gov.nrs.cbr.service.v1.InspectionSearchService}'s read-only transaction, where it is
 * true, rather than here, where the first edit screen would have to undo it — the same reasoning as
 * {@link CrossingSiteEntity}.
 */
@Entity
@Table(name = "STRUCTURE_INSPECTION", schema = "THE")
@Getter
@ToString(exclude = {"structure", "currentStatus"})
@EqualsAndHashCode(of = "inspectionId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureInspectionEntity {

  @Id
  @Column(name = "INSPECTION_ID")
  private Long inspectionId;

  /**
   * The date of the inspection.
   *
   * <p>{@link LocalDate} rather than {@code LocalDateTime}: the column is an Oracle {@code DATE},
   * which carries a time, but every value is written at midnight and the screen shows and filters
   * whole days. Mapping it as a date means the month-range predicate compares what the user meant.
   */
  @Column(name = "INSPECTION_DATE")
  private LocalDate inspectionDate;

  @Column(name = "INSPECTOR_NAME", length = 255)
  private String inspectorName;

  /**
   * The site the structure stood at when the inspection happened.
   *
   * <p>Not derivable from the structure's current site: structures move, and this is the record of
   * where it was. It is what the results table shows, and what "Include Inspections for Structures
   * at Previous Sites?" searches instead of the site's own id.
   */
  @Column(name = "SITE_AT_TIME_OF_INSPECTION", length = 14)
  private String siteAtTimeOfInspection;

  /** → {@code STRUCTURE_INSPECTION_REVIEWER}. Null unless the inspection is `RVD`/`ACC`. */
  @Column(name = "INSPECTION_REVIEWER_ID")
  private Long inspectionReviewerId;

  @Column(name = "STRCTRE_INSPECTION_TYPE_CODE", length = 10)
  private String strctreInspectionTypeCode;

  @Column(name = "CROSSING_STRUCTURE_ID")
  private Long crossingStructureId;

  /** The structure inspected, and through it the site — both needed by every results row. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "CROSSING_STRUCTURE_ID", insertable = false, updatable = false)
  private CrossingStructureEntity structure;

  /**
   * The inspection's <b>current</b> status — the most recent row of its status history.
   *
   * <p>There is no status column on this table. Legacy resolves the current status by joining the
   * history and keeping the row whose id is {@code CBR.GET_LAST_STATUS_ID(I.INSPECTION_ID)}:
   *
   * <pre>
   * FUNCTION GET_LAST_STATUS_ID(P_INSPECTION_ID ...) RETURN VARCHAR2 IS
   * BEGIN
   *   SELECT MAX(inspection_report_status_id) INTO v ... WHERE t.inspection_id = P_INSPECTION_ID;
   *   RETURN v;
   * END;
   * </pre>
   *
   * <p>The {@link JoinFormula} below is that function, inlined as the join condition — which is
   * exactly how the legacy query uses it. Expressing it on the entity rather than rebuilding it in
   * every query means "current status" has one definition: the search filters on it, orders by it
   * and displays it through this one association, and so will the screens that come later.
   *
   * <p>Two consequences worth knowing:
   *
   * <ul>
   *   <li><b>An inspection with no history rows has no current status</b>, and the association is
   *       null. Legacy's join is inner, so such a row simply does not appear in search results.
   *       {@link ca.bc.gov.nrs.cbr.specification.v1.InspectionSearchSpecifications} reproduces that
   *       by joining inner.</li>
   *   <li><b>Fetch it, do not let it lazy-load per row.</b> The correlated subquery is cheap as a
   *       join condition and expensive twenty times over. The search fetch-joins it; it is a to-one,
   *       so it does not interfere with paging.</li>
   * </ul>
   *
   * <h3>This is the most expensive thing in the search, and the reason is a missing index</h3>
   * {@code THE.INSPECTION_REPORT_STATUS} carries exactly one index — {@code IRST_PK}, on
   * {@code INSPECTION_REPORT_STATUS_ID} — and <b>nothing on {@code INSPECTION_ID}</b>
   * ({@code nr-mof-db/scripts/THE/INDEXES}). So the subquery above has no access path: every
   * evaluation is a full scan of the status history, and it is evaluated once per candidate row of
   * {@code STRUCTURE_INSPECTION}.
   *
   * <p>Legacy has the same shape and the same missing index — {@code GET_LAST_STATUS_ID} is a
   * PL/SQL function doing the identical {@code MAX} — but it was always driven by a narrowed result
   * set, because every legacy search form refuses an empty search.
   *
   * <p><b>The fix is an index, and it belongs in nr-mof-db, not here.</b> Submitted there as
   * {@code THE.IRST_INSPECTION_ID_I (INSPECTION_ID, INSPECTION_REPORT_STATUS_ID)} — both columns,
   * so Oracle answers the {@code MAX} from the index without touching the table.
   *
   * <p>Until it is deployed, keep the subquery to one evaluation per row — see the query-shape
   * tests on {@code InspectionSearchSpecificationsTest}, which pin exactly that.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumnOrFormula(
      formula = @JoinFormula(
          value = "(SELECT MAX(h.INSPECTION_REPORT_STATUS_ID) FROM THE.INSPECTION_REPORT_STATUS h"
              + " WHERE h.INSPECTION_ID = INSPECTION_ID)",
          referencedColumnName = "INSPECTION_REPORT_STATUS_ID"))
  private InspectionReportStatusEntity currentStatus;
}
