package ca.bc.gov.nrs.cbr.model.v1;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

/**
 * {@code THE.CROSSING_SITE} — the site a structure sits on, and what Site Search searches.
 *
 * <p>The primary key is a <b>natural</b> key, not a sequence: "structure number and site number
 * combined", 14 characters (cbr-data-model.local.md §2).
 *
 * <p><b>Not {@code @Immutable}</b>, unlike the code tables and views alongside it. CBR owns this
 * table and holds full DML on it; site create and edit are simply not built yet. Marking it
 * immutable to suit today's single read-only use would have to be undone by the first write, and
 * silently ignores updates until someone notices. The search path is kept read-only at the service
 * instead, where the constraint actually belongs.
 *
 * <p>Only what Site Search needs is mapped — the criteria, the results columns, and the four
 * associations the screen joins through. The table has thirty-one columns; geometry, the map sheet
 * numbers and the audit stamps are left off until a screen reads them.
 *
 * <h2>Every association is optional, and that is load-bearing</h2>
 * Legacy joins all four with {@code LEFT OUTER JOIN}. A site with no status, no org unit, no road
 * section or no client must still appear in the results — "incomplete data" is a thing users
 * deliberately search for, and it is exactly those sites whose joins are empty. Any query built on
 * this entity has to say {@code JoinType.LEFT} explicitly: a path expression such as
 * {@code root.get("orgUnit").get("orgUnitCode")} produces an <em>inner</em> join and silently drops
 * the very rows the "Incomplete Data?" filter exists to find.
 */
@Entity
@Table(name = "CROSSING_SITE", schema = "THE")
@Getter
@ToString(exclude = {"status", "orgUnit", "roadSection", "client"})
@EqualsAndHashCode(of = "crossingSiteId")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrossingSiteEntity {

  @Id
  @Column(name = "CROSSING_SITE_ID", length = 14)
  private String crossingSiteId;

  @Column(name = "CROSSING_NAME", length = 255)
  private String crossingName;

  /** The results table's KM column. Kilometres from the point of commencement. */
  @Column(name = "POINT_OF_COMMENCEMENT_DISTANCE", precision = 8, scale = 2)
  private BigDecimal pointOfCommencementDistance;

  /** "The posted distance (relative to a sign)" — may differ from the measured distance above. */
  @Column(name = "USER_KM", precision = 8, scale = 2)
  private BigDecimal userKm;

  @Column(name = "CROSSING_SITE_STATUS_CODE", length = 10)
  private String crossingSiteStatusCode;

  @Column(name = "STRUCTURE_INSPCTN_STATUS_CODE", length = 10)
  private String structureInspectionStatusCode;

  @Column(name = "CROSSING_SITE_TYPE_CODE", length = 10)
  private String crossingSiteTypeCode;

  @Column(name = "SPECIAL_ACCESS_RQMT_CODE", length = 10)
  private String specialAccessRqmtCode;

  /** Forest District. The first of this table's three org-unit columns. */
  @Column(name = "ORG_UNIT_NO")
  private Long orgUnitNo;

  /** Management Area — a former district (see {@code CbrOrgUnitRepository}). */
  @Column(name = "MANAGEMENT_ORG_UNIT_NO")
  private Long managementOrgUnitNo;

  /**
   * BCTS Business Area — the third org-unit column, and not a level of the other two.
   *
   * <p>BC Timber Sales runs its own geography, so a site can carry a district and a business area at
   * once. Site Search does not offer it; Inspection Search does, which is why it is mapped.
   *
   * <p>None of the three is read for authorization. They were once thought to be the open question
   * blocking the FAM role design and are not — no WebADE profile is organization-secured, so there
   * is no regional scope to enforce (cbr-data-model.local.md §2).
   */
  @Column(name = "BUSINESS_AREA_ORG_UNIT_NO")
  private Long businessAreaOrgUnitNo;

  @Column(name = "ROAD_SEGMENT_ID")
  private Long roadSegmentId;

  @Column(name = "FOREST_FILE_ID", length = 10)
  private String forestFileId;

  @Column(name = "ROAD_SECTION_ID", length = 30)
  private String roadSectionId;

  @Column(name = "CLIENT_NUMBER", length = 8)
  private String clientNumber;

  @Column(name = "CLIENT_LOCN_CODE", length = 2)
  private String clientLocnCode;

  /** {@code 'Y'} or {@code 'N'}, never null — the column defaults to {@code 'N'}. */
  @Column(name = "CAPITAL_ROAD_IND", length = 1)
  private String capitalRoadInd;

  /*
   * The four joins. Each maps a column already mapped above as a scalar, so the association is
   * read-only (`insertable`/`updatable` false) and the scalar stays the thing a write would set.
   * Filters use the scalars where they can — `orgUnitNo` rather than `orgUnit.orgUnitNo` — which
   * keeps a join out of the query entirely for most criteria.
   */

  /** Supplies the results table's Status column, which shows the description and not the code. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "CROSSING_SITE_STATUS_CODE", insertable = false, updatable = false)
  private CrossingSiteStatusCodeEntity status;

  /** Supplies District Code and its tooltip. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ORG_UNIT_NO", insertable = false, updatable = false)
  private OrgUnitEntity orgUnit;

  /**
   * Supplies Forest Service Road, and is the only thing that criterion can match on.
   *
   * <p>{@code NO_CONSTRAINT} because the target is a materialized view: Oracle has no foreign key
   * here and could not have one. Saying so keeps a generated schema — which is how the tests build
   * their database — from inventing a constraint the real schema does not have, and then rejecting
   * the very rows this join exists to tolerate: a site whose road section is not in the view.
   *
   * <p><b>{@code @NotFound(IGNORE)} follows directly from that.</b> With no constraint, a site can
   * carry a {@code (FOREST_FILE_ID, ROAD_SECTION_ID)} pair the view does not contain — and it
   * routinely will, because the view is a snapshot refreshed on demand over a database link, so a
   * road section added upstream is missing here until someone refreshes it. Left to itself Hibernate
   * builds a lazy proxy for any non-null foreign key and throws {@code EntityNotFoundException} the
   * moment the results mapping reads it: a 500 on a search, caused by data that is merely stale.
   * {@code IGNORE} reads a dangling reference as "no road section", which is what the legacy left
   * join does and what the screen should show.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @NotFound(action = NotFoundAction.IGNORE)
  @JoinColumns(
      value = {
          @JoinColumn(name = "FOREST_FILE_ID", referencedColumnName = "FOREST_FILE_ID",
              insertable = false, updatable = false),
          @JoinColumn(name = "ROAD_SECTION_ID", referencedColumnName = "ROAD_SECTION_ID",
              insertable = false, updatable = false)
      },
      foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
  private CbrRoadSectionEntity roadSection;

  /**
   * Used only by the "Designated Maintainer" criterion, which matches on the client's name.
   *
   * <p>{@code NO_CONSTRAINT} because {@code V_CLIENT_PUBLIC} is a view and nothing can key to it.
   * The real constraint on this column is {@code CRS_CL_FK1}, a composite key over
   * {@code (CLIENT_NUMBER, CLIENT_LOCN_CODE)} pointing at {@code CLIENT_LOCATION} — which in turn
   * keys to {@code FOREST_CLIENT} via {@code CL_FC_FK}. So a non-null client number does resolve,
   * transitively, and the view withholds no rows: it is {@code SELECT six columns FROM
   * forest_client} with no filter.
   *
   * <p>{@code @NotFound(IGNORE)} is kept regardless, for a narrower reason than the road section's.
   * Hibernate cannot see a guarantee that runs through a table this mapping never mentions, so it
   * builds a lazy proxy and throws {@code EntityNotFoundException} the moment the results mapping
   * reads one that is missing. The guarantee also holds only while the data does: this is a view
   * over a table CBR does not own, reached through a constraint on a column pair only half of which
   * is mapped here. A search returning a 500 because one site's client went missing is a poor trade
   * against a cell rendering empty.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @NotFound(action = NotFoundAction.IGNORE)
  @JoinColumn(name = "CLIENT_NUMBER", insertable = false, updatable = false,
      foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
  private ClientPublicEntity client;
}
