package ca.bc.gov.nrs.cbr.specification.v1;

import ca.bc.gov.nrs.cbr.model.v1.InspectionReportStatusEntity;
import ca.bc.gov.nrs.cbr.model.v1.StructureInspectionEntity;
import ca.bc.gov.nrs.cbr.struct.v1.InspectionSearchCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Turns {@link InspectionSearchCriteria} into a JPA {@link Specification} over
 * {@link StructureInspectionEntity}.
 *
 * <p>This replaces {@code CBR.FIND_INSPECTIONS_BY_CRITERIA}, which does not take the criteria as
 * parameters: {@code OracleInspectionDAO.search()} assembles a {@code WHERE} clause as a string,
 * passes a {@code CBR_VARCHAR2_ARRAY} of bind values and their count, and the two must agree by
 * position. The Criteria API cannot express that mistake — each predicate carries its own value.
 *
 * <h2>The shape being reproduced</h2>
 * The legacy query is {@code GET_INSPECTION_BASE_SELECT()} plus the caller's clause:
 *
 * <pre>
 * FROM STRUCTURE_INSPECTION I
 * INNER JOIN INSPECTION_REPORT_STATUS IRS
 *         ON IRS.INSPECTION_ID = I.INSPECTION_ID
 *        AND IRS.INSPECTION_REPORT_STATUS_ID = CBR.GET_LAST_STATUS_ID(I.INSPECTION_ID)
 * INNER JOIN CROSSING_STRUCTURE CR ON I.CROSSING_STRUCTURE_ID = CR.CROSSING_STRUCTURE_ID
 * INNER JOIN CROSSING_SITE      S  ON CR.CROSSING_SITE_ID = S.CROSSING_SITE_ID
 * LEFT  JOIN CBR_ROAD_SEGMENT_VW R   (selected from, never read)
 * LEFT  JOIN CBR_ROAD_SECTION_VW SEC ON S.FOREST_FILE_ID = SEC.FOREST_FILE_ID
 *                                   AND S.ROAD_SECTION_ID = SEC.ROAD_SECTION_ID
 * INNER JOIN INSPECTION_REPORT_STATUS_CODE CD ON IRS...CODE = CD...CODE
 * INNER JOIN CBR_ORG_UNIT O  ON O.ORG_UNIT_NO = S.ORG_UNIT_NO
 * LEFT  JOIN STRUCTURE_INSPECTION_REVIEWER  IR   (displayed, never filtered here)
 * LEFT  JOIN STRCTRE_INSPECTION_TYPE_CODE   SITC (displayed, never filtered here)
 * </pre>
 *
 * <p>The three inner joins that matter are reproduced as inner joins: an inspection with no status
 * history, no structure or no site does not appear, exactly as it does not in legacy. The road
 * section stays a left join, so an inspection whose site has no road section is still found by every
 * other criterion.
 *
 * <p>{@code CBR_ROAD_SEGMENT_VW} is joined by legacy and never read — no column of {@code R} appears
 * in the select list or any predicate. It is dropped here. It is a left join, so it cannot have been
 * filtering anything, and it reaches across a database link (cbr-data-model.local.md §6).
 *
 * <h2>Where this diverges from legacy</h2>
 * Four places, each marked on the member that makes the choice:
 *
 * <ol>
 *   <li><b>The inspection date range is inclusive of both whole months</b>, where legacy's is
 *       neither — see {@link #inspectionDateRange}. This is the one divergence that changes which
 *       rows come back for an ordinary search.</li>
 *   <li><b>The district and business-area filters read {@code CROSSING_SITE} directly</b> rather
 *       than the {@code CBR_ORG_UNIT} alias — see {@link #matching}.</li>
 *   <li><b>The org unit is joined from the {@code ORG_UNIT} table, not the {@code CBR_ORG_UNIT}
 *       view, and joined left</b> — see {@link #fetchAndOrder}.</li>
 *   <li><b>"Structures at previous sites" binds its value</b> instead of concatenating it into the
 *       SQL — see {@link #siteIdPredicate}.</li>
 * </ol>
 */
public final class InspectionSearchSpecifications {

  private InspectionSearchSpecifications() {}

  /** Legacy's {@code Search.LIKE} is an unanchored contains. */
  private static final String WILDCARD = "%";

  /** Legacy parses both date bounds with {@code SimpleDateFormat("yyyy/MM")}. */
  private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/M");

  /*
   * Entity attribute names — strings because the Criteria API takes strings. Named once for the
   * reasons set out on SiteSearchSpecifications.
   */
  private static final String INSPECTION_DATE = "inspectionDate";
  private static final String INSPECTOR_NAME = "inspectorName";
  private static final String SITE_AT_TIME_OF_INSPECTION = "siteAtTimeOfInspection";
  private static final String INSPECTION_REVIEWER_ID = "inspectionReviewerId";
  private static final String INSPECTION_TYPE_CODE = "strctreInspectionTypeCode";
  private static final String CROSSING_STRUCTURE_ID = "crossingStructureId";
  private static final String INSPECTION_ID = "inspectionId";

  /* Associations. */
  private static final String STRUCTURE = "structure";
  private static final String CURRENT_STATUS = "currentStatus";
  private static final String STATUS_CODE = "statusCode";
  private static final String SITE = "site";
  private static final String ORG_UNIT = "orgUnit";
  private static final String ROAD_SECTION = "roadSection";
  private static final String CLIENT = "client";

  /* Columns on CROSSING_STRUCTURE. */
  private static final String STRUCTURE_NAME = "crossingStructureName";
  private static final String STRUCTURE_TYPE_CLASS_CODE = "structureTypeClassCode";
  private static final String CLOSE_PROXIMITY_IND = "closeProximityInd";

  /* Columns on CROSSING_SITE. */
  private static final String SITE_ID = "crossingSiteId";
  private static final String FOREST_FILE_ID = "forestFileId";
  private static final String ROAD_SECTION_ID = "roadSectionId";
  private static final String CROSSING_NAME = "crossingName";
  private static final String KILOMETRES = "pointOfCommencementDistance";
  private static final String ORG_UNIT_NO = "orgUnitNo";
  private static final String MANAGEMENT_ORG_UNIT_NO = "managementOrgUnitNo";
  private static final String BUSINESS_AREA_ORG_UNIT_NO = "businessAreaOrgUnitNo";

  /* Columns elsewhere. */
  private static final String ORG_UNIT_CODE = "orgUnitCode";
  private static final String ROAD_SECTION_NAME = "roadSectName";
  private static final String STATUS_CODE_VALUE = "inspectionReportStatusCode";
  private static final String STATUS_HISTORY_INSPECTION_ID = "inspectionId";

  /** The value an indicator column carries when it is set. */
  private static final String YES = "Y";

  /** The terminal status. {@link #findChangedReviewed} is defined by its absence and its history. */
  private static final String REVIEWED = "RVD";

  /**
   * Builds the predicate for a set of criteria.
   *
   * <p>Returns a specification that matches everything when nothing is set. The API allows that; the
   * screen does not, because {@code InspectionSearchService} refuses an empty criteria object before
   * it gets here — the same refusal legacy makes with {@code errors.search.select}.
   *
   * <p><b>Divergence 2 — the org-unit filters read {@code CROSSING_SITE}.</b> Legacy maps Forest
   * District to {@code "O." + ORG_UNIT_NO}, the alias of the joined {@code CBR_ORG_UNIT}, while
   * Management Area and BCTS Business Area are mapped to bare column names that resolve to
   * {@code CROSSING_SITE}. Since {@code O} is joined on {@code O.ORG_UNIT_NO = S.ORG_UNIT_NO} the
   * two are the same value, so all three read the site here. That makes the district filter
   * independent of whether the org unit resolves at all, which matters once the join is a left join
   * (divergence 3).
   */
  public static Specification<StructureInspectionEntity> matching(InspectionSearchCriteria criteria) {
    return (root, query, builder) -> {
      List<Predicate> predicates = new ArrayList<>();

      // INNER, as legacy: an inspection with no structure, no site or no status history is not a
      // result.
      //
      // Built once, here, and reused by every predicate and by the ordering. When the query returns
      // entities these are *fetch* joins, so the row carries what it needs to be rendered; when it
      // is the count they are plain joins, because a fetch in a count is invalid.
      //
      // <p><b>This is one set of joins, not two.</b> An earlier version created plain joins here
      // and fetches again further down, on the belief that a fetch reuses a join of the same
      // attribute. It does not — {@code root.fetch(X)} is a new join every time — so the emitted
      // SQL joined CROSSING_STRUCTURE, CROSSING_SITE and INSPECTION_REPORT_STATUS twice each, and
      // evaluated the current-status subquery twice per candidate row. See {@link #currentStatus}
      // for why that subquery is the expensive part.
      boolean projecting = returnsEntities(query);
      From<?, ?> structure = joinOrFetch(root, STRUCTURE, JoinType.INNER, projecting);
      From<?, ?> site = joinOrFetch(structure, SITE, JoinType.INNER, projecting);
      From<?, ?> status = joinOrFetch(root, CURRENT_STATUS, JoinType.INNER, projecting);

      // On STRUCTURE_INSPECTION itself.
      contains(builder, root.get(INSPECTOR_NAME), criteria.inspectorName())
          .ifPresent(predicates::add);
      equals(builder, root.get(INSPECTION_TYPE_CODE), criteria.inspectionTypeCode())
          .ifPresent(predicates::add);
      equalsNumber(builder, root.get(INSPECTION_REVIEWER_ID), criteria.inspectionReviewerId())
          .ifPresent(predicates::add);
      inspectionDateRange(builder, root.get(INSPECTION_DATE), criteria)
          .ifPresent(predicates::add);

      // The current status — IRS in legacy, the association here.
      equals(builder, status.get(STATUS_CODE_VALUE), criteria.inspectionReportStatusCode())
          .ifPresent(predicates::add);

      // On CROSSING_STRUCTURE.
      contains(builder, structure.get(STRUCTURE_NAME), criteria.structureName())
          .ifPresent(predicates::add);
      equals(builder, structure.get(STRUCTURE_TYPE_CLASS_CODE), criteria.structureTypeClassCode())
          .ifPresent(predicates::add);

      // Close Proximity is a Y/N indicator and the filter is one-way: switched on it means "close
      // proximity required only", switched off it means "do not filter" — not "the others". Legacy
      // adds the criterion only when the box is ticked, and the toggle's wording follows from that.
      if (Boolean.TRUE.equals(criteria.closeProximity())) {
        predicates.add(builder.equal(structure.get(CLOSE_PROXIMITY_IND), YES));
      }

      // On CROSSING_SITE.
      contains(builder, site.get(FOREST_FILE_ID), criteria.forestFileId())
          .ifPresent(predicates::add);
      contains(builder, site.get(ROAD_SECTION_ID), criteria.roadSectionId())
          .ifPresent(predicates::add);
      equalsNumber(builder, site.get(ORG_UNIT_NO), criteria.orgUnitNo()).ifPresent(predicates::add);
      equalsNumber(builder, site.get(MANAGEMENT_ORG_UNIT_NO), criteria.managementOrgUnitNo())
          .ifPresent(predicates::add);
      equalsNumber(builder, site.get(BUSINESS_AREA_ORG_UNIT_NO), criteria.businessAreaOrgUnitNo())
          .ifPresent(predicates::add);

      siteIdPredicate(builder, root, site, criteria).ifPresent(predicates::add);

      // The road section and the org unit are read by every results row and filtered on by one
      // criterion, so they are joined once here — fetched when projecting — and shared. LEFT on
      // both: a site with no road section, or pointing at an org unit the table has no row for, is
      // still a candidate for every other criterion.
      From<?, ?> roadSection = joinOrFetch(site, ROAD_SECTION, JoinType.LEFT, projecting);
      From<?, ?> orgUnit = joinOrFetch(site, ORG_UNIT, JoinType.LEFT, projecting);
      if (projecting) {
        joinOrFetch(status, STATUS_CODE, JoinType.LEFT, true);
        // No column of this screen shows the site's designated maintainer, and no criterion filters
        // on it — but {@code CrossingSiteEntity.client} carries {@code @NotFound(IGNORE)}, which
        // Hibernate cannot honour alongside {@code FetchType.LAZY}: it has to look for the row
        // before it can choose between an entity and a null. So the association loads whatever the
        // fetch type says, and the only question is whether it loads with the page or one select at
        // a time. This makes it the page. Site Search inherits the same problem from the same
        // mapping and answers it the same way.
        joinOrFetch(site, CLIENT, JoinType.LEFT, true);
      }

      contains(builder, roadSection.get(ROAD_SECTION_NAME), criteria.forestServiceRoad())
          .ifPresent(predicates::add);

      if (Boolean.TRUE.equals(criteria.mostRecentInspections())) {
        predicates.add(mostRecentInspection(builder, query, root));
      }
      if (Boolean.TRUE.equals(criteria.findChangedReviewed())) {
        predicates.add(findChangedReviewed(builder, query, root, status));
      }

      order(root, structure, site, orgUnit, query, builder, criteria.sortBy());

      return predicates.isEmpty()
          ? builder.conjunction()
          : builder.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Site #, and what "Include Inspections for Structures at Previous Sites?" does to it.
   *
   * <p>The toggle is not an independent filter: it changes which column Site # is matched against.
   * Off, legacy matches {@code S.CROSSING_SITE_ID}. On, it matches either the site recorded on the
   * inspection or the structure's current site:
   *
   * <pre>
   * (UPPER(I.SITE_AT_TIME_OF_INSPECTION) LIKE UPPER('%value%')
   *   OR UPPER(S.CROSSING_SITE_ID)       LIKE UPPER('%value%'))
   * </pre>
   *
   * <p>So it widens the search to structures that have since moved away from the site being
   * searched. With no Site # entered it does nothing at all, which the legacy form does not say —
   * hence it not counting as a criterion in {@link InspectionSearchCriteria#isEmpty()}.
   *
   * <p><b>Divergence 4 — the value is bound, not concatenated.</b> Legacy builds this branch by
   * string concatenation directly into the dynamic SQL, while every other criterion on the screen
   * goes through the bind array. A site id containing a quote is a broken query at best; the column
   * is 14 characters and the field is user input. The predicate is otherwise identical.
   *
   * <p>The {@code UPPER(...)} on both sides is dropped, because Site Search does not have it either:
   * {@code CROSSING_SITE_ID} is a system-generated key with no lower-case form, so case folding it
   * only prevents Oracle from using its index.
   */
  private static Optional<Predicate> siteIdPredicate(
      CriteriaBuilder builder,
      Root<StructureInspectionEntity> root,
      From<?, ?> site,
      InspectionSearchCriteria criteria) {
    if (!StringUtils.hasText(criteria.siteId())) {
      return Optional.empty();
    }
    Optional<Predicate> onSite = contains(builder, site.get(SITE_ID), criteria.siteId());
    if (!Boolean.TRUE.equals(criteria.findMovedStructures())) {
      return onSite;
    }
    Optional<Predicate> atTimeOfInspection =
        contains(builder, root.get(SITE_AT_TIME_OF_INSPECTION), criteria.siteId());
    return Optional.of(builder.or(atTimeOfInspection.orElseThrow(), onSite.orElseThrow()));
  }

  /**
   * "Most Recent Inspections Only?" — keep only each structure's latest inspection.
   *
   * <pre>
   * I.INSPECTION_DATE = (SELECT MAX(INSPECTION_DATE) FROM STRUCTURE_INSPECTION
   *                       WHERE CROSSING_STRUCTURE_ID = I.CROSSING_STRUCTURE_ID)
   * </pre>
   *
   * <p>Ported as written, including the consequence: <b>two inspections of one structure on the same
   * date both survive this filter</b>, because it compares dates rather than picking one row. That is
   * rarer than it sounds — {@code InspectionSameDateException} refuses a second inspection of a
   * structure on a date that already has one — but the guard is application-level, so historical
   * data can still hold pairs.
   *
   * <p>The subquery is uncorrelated in legacy's text only; {@code I} inside it refers to the outer
   * query, which is what makes it per-structure. {@link Subquery#correlate} is the Criteria API's
   * way of saying the same thing.
   */
  private static Predicate mostRecentInspection(
      CriteriaBuilder builder, CriteriaQuery<?> query, Root<StructureInspectionEntity> root) {
    Subquery<LocalDate> latest = query.subquery(LocalDate.class);
    Root<StructureInspectionEntity> other = latest.from(StructureInspectionEntity.class);
    latest.select(builder.greatest(other.<LocalDate>get(INSPECTION_DATE)))
        .where(builder.equal(
            other.get(CROSSING_STRUCTURE_ID), root.get(CROSSING_STRUCTURE_ID)));
    return builder.equal(root.get(INSPECTION_DATE), latest);
  }

  /**
   * "Previously Reviewed Inspections Only?" — inspections that reached {@code RVD} and have since
   * moved off it.
   *
   * <pre>
   * IRS.INSPECTION_REPORT_STATUS_CODE != 'RVD'
   *   AND (SELECT COUNT(*) FROM INSPECTION_REPORT_STATUS IRS2
   *         WHERE IRS2.INSPECTION_ID = I.INSPECTION_ID
   *           AND IRS2.INSPECTION_REPORT_STATUS_CODE = 'RVD') > 0
   * </pre>
   *
   * <p>The label understates it. This is not "inspections that have been reviewed" — it is
   * inspections that were reviewed and then <em>changed</em>, which is why the legacy field is called
   * {@code findChangedReviewed}. An inspection currently sitting at {@code RVD} is excluded by the
   * first half.
   *
   * <p>That makes it the only way to find the rows described in cbr-inspection-reviewer.local.md §4:
   * moving an inspection off {@code RVD} nulls its reviewer and P.Eng date, so these history rows are
   * the only surviving evidence that it was ever reviewed.
   *
   * <p>{@code COUNT(*) > 0} is written as an {@code EXISTS}. Identical meaning, and it can stop at
   * the first row.
   */
  private static Predicate findChangedReviewed(
      CriteriaBuilder builder,
      CriteriaQuery<?> query,
      Root<StructureInspectionEntity> root,
      From<?, ?> currentStatus) {
    Subquery<Integer> everReviewed = query.subquery(Integer.class);
    Root<InspectionReportStatusEntity> history = everReviewed.from(InspectionReportStatusEntity.class);
    everReviewed.select(builder.literal(1))
        .where(
            builder.equal(history.get(STATUS_HISTORY_INSPECTION_ID), root.get(INSPECTION_ID)),
            builder.equal(history.get(STATUS_CODE_VALUE), REVIEWED));

    return builder.and(
        builder.notEqual(currentStatus.get(STATUS_CODE_VALUE), REVIEWED),
        builder.exists(everReviewed));
  }

  /**
   * The inspection date range.
   *
   * <p><b>Divergence 1, and the only one that changes which rows an ordinary search returns.</b>
   *
   * <p>Legacy parses both bounds as {@code yyyy/MM} and hands them to Oracle as
   * {@code TO_DATE(:n, 'yyyy/MM')}, which resolves a month to <em>its first day</em>. It then emits
   * one of three comparisons:
   *
   * <table>
   *   <caption>Legacy behaviour</caption>
   *   <tr><th>Form filled</th><th>SQL</th><th>What it actually matches</th></tr>
   *   <tr><td>both</td><td>{@code BETWEEN TO_DATE(from) AND TO_DATE(to)}</td>
   *       <td>1st of the start month to the <b>1st</b> of the end month</td></tr>
   *   <tr><td>start only</td><td>{@code > TO_DATE(from)}</td>
   *       <td>strictly after the 1st — the 1st itself is excluded</td></tr>
   *   <tr><td>end only</td><td>{@code < TO_DATE(to)}</td>
   *       <td>strictly before the 1st of the end month</td></tr>
   * </table>
   *
   * <p>So "2026/01 to 2026/12" silently omits 2 – 31 December, and "from 2026/01" omits any
   * inspection dated 1 January. Both halves of that are indefensible on a screen whose label says
   * {@code (yyyy/mm)}: the user named months, and a month is a range of days, not its first one.
   *
   * <p>Here both bounds are inclusive of the whole month — the start month from its 1st, the end
   * month through its last day. That is what the label has always promised.
   *
   * <p>This is the same call Site Search made with its kilometre bounds, for the same reason: legacy
   * mixed {@code BETWEEN} with {@code >} and {@code <}, nothing depended on the asymmetry, and no
   * user could predict it. It is recorded here rather than buried because, unlike that one, it
   * changes result counts on searches people run every day.
   */
  private static Optional<Predicate> inspectionDateRange(
      CriteriaBuilder builder, Expression<LocalDate> path, InspectionSearchCriteria criteria) {
    YearMonth from = month(criteria.inspectionDateStart());
    YearMonth to = month(criteria.inspectionDateEnd());
    if (from != null && to != null) {
      return Optional.of(builder.between(path, from.atDay(1), to.atEndOfMonth()));
    }
    if (from != null) {
      return Optional.of(builder.greaterThanOrEqualTo(path, from.atDay(1)));
    }
    if (to != null) {
      return Optional.of(builder.lessThanOrEqualTo(path, to.atEndOfMonth()));
    }
    return Optional.empty();
  }

  private static YearMonth month(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return YearMonth.parse(value.trim(), MONTH);
    } catch (DateTimeParseException ex) {
      // @Pattern on InspectionSearchCriteria rejects these with a 400 before they reach here.
      return null;
    }
  }

  /**
   * Applies the ordering the form asked for.
   *
   * <p>The two orderings are legacy's, from {@code InspectionSearchForm.createSearch()}:
   *
   * <ul>
   *   <li>{@code structureIdDateSort} — structure name ascending, then inspection date descending.
   *       Legacy's default, selected by {@code reset()}.</li>
   *   <li>{@code projectBranchKmDateSort} — project file, road section and kilometre ascending, then
   *       inspection date descending.</li>
   * </ul>
   *
   * <p>Legacy carries a third, unreachable ordering for when {@code sortBy} is empty — district
   * code, road, road section, kilometre, then date descending. It is reproduced here as the fallback
   * for a caller that omits {@code sortBy}, which the screen never does but the API allows.
   *
   * <h3>Why the sort is here and not on the {@code Pageable}</h3>
   * Every key lives on a joined table, and Spring Data resolves a sort path with an <em>inner</em>
   * join. For the keys on {@code CROSSING_SITE} that is harmless — the join is already inner — but
   * the fallback ordering keys on the org unit, which is joined left. Ordering inside the
   * specification is what lets each join type be stated once, and lets the ordering reuse the joins
   * the projection already needs rather than adding more.
   *
   * <h3>Divergence 3 — the org unit</h3>
   * Legacy joins {@code CBR_ORG_UNIT}, a six-branch union whose {@code ORG_UNIT_NO} is not unique
   * across branches, and joins it <em>inner</em>. Two changes here, both following the precedent set
   * by {@link ca.bc.gov.nrs.cbr.model.v1.OrgUnitEntity}: the association reads the {@code ORG_UNIT}
   * table, because a {@code @ManyToOne} onto a non-unique key is a runtime failure; and the join is
   * left, because an inner join would silently drop every inspection whose site points at an org
   * unit the view has no branch for. Legacy drops those rows. Here they appear, with a blank
   * District Code — which is what the legacy screen shows for such a site anyway, when it shows it.
   *
   * <p>Skipped entirely for the count query, which has no ordering.
   */
  private static void order(
      Root<StructureInspectionEntity> root,
      From<?, ?> structure,
      From<?, ?> site,
      From<?, ?> orgUnit,
      CriteriaQuery<?> query,
      CriteriaBuilder builder,
      String sortBy) {
    if (!returnsEntities(query)) {
      return;
    }

    if (InspectionSearchCriteria.PROJECT_BRANCH_KM_DATE_SORT.equals(sortBy)) {
      query.orderBy(
          builder.asc(site.get(FOREST_FILE_ID)),
          builder.asc(site.get(ROAD_SECTION_ID)),
          builder.asc(site.get(KILOMETRES)),
          builder.desc(root.get(INSPECTION_DATE)));
      return;
    }
    if (StringUtils.hasText(sortBy)) {
      query.orderBy(
          builder.asc(structure.get(STRUCTURE_NAME)),
          builder.desc(root.get(INSPECTION_DATE)));
      return;
    }
    query.orderBy(
        builder.asc(orgUnit.get(ORG_UNIT_CODE)),
        builder.asc(site.get(ROAD_SECTION_ID)),
        builder.asc(site.get(KILOMETRES)),
        builder.desc(root.get(INSPECTION_DATE)));
  }

  /**
   * Whether this execution of the specification returns entities rather than a count.
   *
   * <p>Spring Data runs the page and the count from the same specification. A fetch join in a count
   * is invalid — Hibernate rejects it, because there is no entity to fetch into — and a count has no
   * ordering, so both are guarded on this.
   */
  private static boolean returnsEntities(CriteriaQuery<?> query) {
    return query != null
        && !Long.class.equals(query.getResultType())
        && !long.class.equals(query.getResultType());
  }

  /**
   * One join, fetched when the query returns entities.
   *
   * <p>Every association this specification touches is read by a results row, so when the query
   * projects entities each join is a fetch and the page is one statement. Left lazy they would be
   * the classic N+1 — a page of twenty inspections costing twenty extra selects per association,
   * invisible in a test with three rows and very visible on a district. The org unit was exactly
   * that: joined for the ordering but never fetched, so every row on every page went back for its
   * district code.
   *
   * <p>These are all to-one, so no row is duplicated and no {@code distinct} is needed, and the
   * fetch does not interfere with paging the way a collection fetch would.
   *
   * <p>The cast is safe and is the standard way to use a fetch as a join: Hibernate's
   * {@code Fetch} implementations are {@code Join}s. It is what lets one join serve the predicate,
   * the ordering and the projection instead of three.
   */
  private static From<?, ?> joinOrFetch(
      From<?, ?> parent, String attribute, JoinType type, boolean projecting) {
    return projecting
        ? (From<?, ?>) parent.fetch(attribute, type)
        : parent.join(attribute, type);
  }

  private static Optional<Predicate> contains(
      CriteriaBuilder builder, Expression<String> path, String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    return Optional.of(builder.like(path, WILDCARD + value.trim() + WILDCARD));
  }

  private static Optional<Predicate> equals(
      CriteriaBuilder builder, Expression<String> path, String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    return Optional.of(builder.equal(path, value.trim()));
  }

  /**
   * Equality against a numeric column.
   *
   * <p>Legacy binds the reviewer id as {@code Search.STRING_TYPE} against a {@code NUMBER(10)}
   * column, leaving Oracle to coerce it; the org units it binds as {@code INTEGER_TYPE} and wraps in
   * {@code TO_NUMBER}. Both are a number here.
   */
  private static Optional<Predicate> equalsNumber(
      CriteriaBuilder builder, Expression<Long> path, String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    try {
      return Optional.of(builder.equal(path, Long.valueOf(value.trim())));
    } catch (NumberFormatException ex) {
      // These come from <select> values and an id the UI does not let the user type. Treating a
      // malformed one as "unset" rather than throwing keeps a hand-written query parameter from
      // becoming a 500 — the same choice SiteSearchSpecifications makes.
      return Optional.empty();
    }
  }
}
