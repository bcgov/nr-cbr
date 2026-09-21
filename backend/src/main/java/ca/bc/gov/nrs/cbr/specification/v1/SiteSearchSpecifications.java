package ca.bc.gov.nrs.cbr.specification.v1;

import ca.bc.gov.nrs.cbr.model.v1.ClientPublicEntity;
import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Turns {@link SiteSearchCriteria} into a JPA {@link Specification} over {@link CrossingSiteEntity}.
 *
 * <p>This replaces {@code CBR.FIND_SITES_BY_CRITERIA}, which does not take the criteria as
 * parameters at all: the caller assembles a {@code WHERE} clause as a string, passes a
 * {@code CBR_VARCHAR2_ARRAY} of bind values and their count, and the two have to agree by position.
 * A clause and an array that disagree bind the wrong value to the wrong column and return
 * plausible-looking wrong rows rather than failing. The Criteria API cannot express that mistake —
 * each predicate carries its own value — which is the reason for moving off the procedure.
 *
 * <p>Each criterion below names the legacy operator it reproduces. Where this diverges from legacy
 * it says so and why; there are three such places, all in the kilometre bounds and the joins.
 */
public final class SiteSearchSpecifications {

  private SiteSearchSpecifications() {}

  private static final String WILDCARD = "%";

  /*
   * Entity attribute names.
   *
   * These are strings because the Criteria API takes strings, and a wrong one fails at runtime with
   * "Unable to locate Attribute" rather than at compile time. Naming each once means a rename in
   * CrossingSiteEntity has one place to follow rather than a dozen, and a typo is a compile error
   * here instead of a 500 on whichever criterion happened to carry it.
   *
   * The alternative is the generated JPA static metamodel (CrossingSiteEntity_.roadSectionId),
   * which would make them compile-checked outright. That needs hibernate-jpamodelgen wired into the
   * build alongside Lombok; worth doing if this file grows siblings, and overkill for one.
   */
  private static final String SITE_ID = "crossingSiteId";
  private static final String CROSSING_NAME = "crossingName";
  private static final String FOREST_FILE_ID = "forestFileId";
  private static final String ROAD_SECTION_ID = "roadSectionId";
  private static final String ROAD_SEGMENT_ID = "roadSegmentId";
  private static final String SITE_STATUS_CODE = "crossingSiteStatusCode";
  private static final String INSPECTION_STATUS_CODE = "structureInspectionStatusCode";
  private static final String SITE_TYPE_CODE = "crossingSiteTypeCode";
  private static final String SPECIAL_ACCESS_CODE = "specialAccessRqmtCode";
  private static final String CLIENT_NUMBER = "clientNumber";
  private static final String CLIENT_LOCATION_CODE = "clientLocnCode";
  private static final String ORG_UNIT_NO = "orgUnitNo";
  private static final String MANAGEMENT_ORG_UNIT_NO = "managementOrgUnitNo";
  private static final String CAPITAL_ROAD_IND = "capitalRoadInd";
  private static final String KILOMETRES = "pointOfCommencementDistance";
  private static final String USER_KM = "userKm";

  /* The associations, and the one column read through each. */
  private static final String ORG_UNIT = "orgUnit";
  private static final String ORG_UNIT_CODE = "orgUnitCode";
  private static final String ROAD_SECTION = "roadSection";
  private static final String ROAD_SECTION_NAME = "roadSectName";
  private static final String CLIENT_NAME = "clientName";
  private static final String STATUS = "status";

  /** The value {@code CAPITAL_ROAD_IND} carries when a road is a capital road. */
  private static final String YES = "Y";

  /** The site type that carries road and location detail — see {@link #incomplete}. */
  private static final String CROSSING = "CRS";

  /**
   * Builds the predicate for a set of criteria.
   *
   * <p>Returns a specification that matches everything when nothing is set — the legacy screen runs
   * with no criteria and returns every site in the province, and there is no scoping to narrow it
   * (cbr-auth-and-roles.local.md §3.3).
   */
  public static Specification<CrossingSiteEntity> matching(SiteSearchCriteria criteria) {
    return (root, query, builder) -> {
      List<Predicate> predicates = new ArrayList<>();

      scalarCriteria(builder, root, criteria, predicates);
      toggleCriteria(builder, root, criteria, predicates);
      maintainedBy(builder, query, root, criteria.primaryUserName()).ifPresent(predicates::add);
      joinedCriteriaAndProjection(root, query, builder, criteria, predicates);

      return allOf(builder, predicates);
    };
  }

  /**
   * Everything that reads a column of {@code CROSSING_SITE} directly.
   *
   * <p>No join is needed for any of these, which is why the Forest District and Management Area
   * filters read the {@code *_NO} columns rather than going through the org-unit associations.
   */
  private static void scalarCriteria(
      CriteriaBuilder builder,
      Root<CrossingSiteEntity> root,
      SiteSearchCriteria criteria,
      List<Predicate> predicates) {
    contains(builder, root.get(SITE_ID), criteria.siteId()).ifPresent(predicates::add);
    contains(builder, root.get(FOREST_FILE_ID), criteria.forestFileId()).ifPresent(predicates::add);
    contains(builder, root.get(ROAD_SECTION_ID), criteria.roadSectionId())
        .ifPresent(predicates::add);
    contains(builder, root.get(CROSSING_NAME), criteria.crossingName()).ifPresent(predicates::add);

    equals(builder, root.get(SITE_STATUS_CODE), criteria.siteStatusCode())
        .ifPresent(predicates::add);
    equals(builder, root.get(INSPECTION_STATUS_CODE), criteria.structureInspectionStatusCode())
        .ifPresent(predicates::add);
    equals(builder, root.get(SPECIAL_ACCESS_CODE), criteria.specialAccessCode())
        .ifPresent(predicates::add);
    equals(builder, root.get(SITE_TYPE_CODE), criteria.siteTypeCode()).ifPresent(predicates::add);
    equals(builder, root.get(CLIENT_NUMBER), criteria.clientNumber()).ifPresent(predicates::add);
    equals(builder, root.get(CLIENT_LOCATION_CODE), criteria.clientLocationCode())
        .ifPresent(predicates::add);

    equalsNumber(builder, root.get(ORG_UNIT_NO), criteria.orgUnit()).ifPresent(predicates::add);
    equalsNumber(builder, root.get(MANAGEMENT_ORG_UNIT_NO), criteria.managementOrgUnit())
        .ifPresent(predicates::add);

    range(builder, root.get(KILOMETRES), criteria.kiloStart(), criteria.kiloEnd())
        .ifPresent(predicates::add);
    range(builder, root.get(USER_KM), criteria.userKmStart(), criteria.userKmEnd())
        .ifPresent(predicates::add);
  }

  /**
   * The two checkbox criteria, both of which filter one way only.
   *
   * <p>"Capital Road" switched on means capital roads only; switched off it means "do not filter",
   * <em>not</em> "non-capital roads only". Legacy adds the criterion only when the box is ticked,
   * and the toggle's wording on both screens follows from that. "Incomplete Data?" is the same
   * shape over a disjunction rather than a column — see {@link #incomplete}.
   */
  private static void toggleCriteria(
      CriteriaBuilder builder,
      Root<CrossingSiteEntity> root,
      SiteSearchCriteria criteria,
      List<Predicate> predicates) {
    if (Boolean.TRUE.equals(criteria.capitalRoad())) {
      predicates.add(builder.equal(root.get(CAPITAL_ROAD_IND), YES));
    }
    if (Boolean.TRUE.equals(criteria.incomplete())) {
      predicates.add(incomplete(builder, root));
    }
  }

  /**
   * The road section: filtered on by one criterion, displayed by every results row, joined once for
   * both.
   *
   * <p>LEFT, so a site with no road section is still a candidate for every other criterion; the
   * predicate then excludes it, which is what an inner join would have done anyway — but only for
   * this criterion rather than for the whole query.
   *
   * <p><b>One join, not two.</b> It used to be created twice — a plain join here and a fetch in the
   * projection — because {@code root.fetch(X)} is a new join every time and never reuses
   * {@code root.join(X)}. That put two left joins to {@code CBR_ROAD_SECTION_VW}, a materialized
   * view reached over a database link, in every search that filtered on Forest Service Road. The
   * same mistake in Inspection Search cost far more, because the join it duplicated carried a
   * correlated subquery.
   *
   * <p>It is joined at all only when something needs it: the projection always does, the filter
   * does when set, and a count query that does neither leaves the view alone.
   */
  private static void joinedCriteriaAndProjection(
      Root<CrossingSiteEntity> root,
      CriteriaQuery<?> query,
      CriteriaBuilder builder,
      SiteSearchCriteria criteria,
      List<Predicate> predicates) {
    boolean projecting = returnsEntities(query);
    boolean filtering = StringUtils.hasText(criteria.forestServiceRoad());
    if (!projecting && !filtering) {
      return;
    }

    From<?, ?> roadSection = joinOrFetch(root, ROAD_SECTION, projecting);
    if (filtering) {
      contains(builder, roadSection.get(ROAD_SECTION_NAME), criteria.forestServiceRoad())
          .ifPresent(predicates::add);
    }
    if (projecting) {
      fetchAndOrder(root, roadSection, query, builder);
    }
  }

  /** Every predicate that was set, or a tautology when none was — the unfiltered search. */
  private static Predicate allOf(CriteriaBuilder builder, List<Predicate> predicates) {
    return predicates.isEmpty()
        ? builder.conjunction()
        : builder.and(predicates.toArray(new Predicate[0]));
  }

  /**
   * "Incomplete Data?" — a site missing any of the fields that make it complete.
   *
   * <p>Not a column: legacy assembles this same disjunction inline in {@code OracleSiteDAO}, with a
   * comment warning that {@code Site.isComplete()} has to be kept in step with it. The shape is a
   * flat set of null checks, plus a nested group that applies only to crossings
   * ({@code CROSSING_SITE_TYPE_CODE = 'CRS'}), which carry road and location detail that other site
   * types do not.
   *
   * <p>These are the sites whose joins are empty, which is why every join in this query is a left
   * join. An inner join anywhere would quietly exclude most of what this filter exists to find.
   */
  private static Predicate incomplete(CriteriaBuilder builder, Root<CrossingSiteEntity> root) {
    Predicate crossingSpecific = builder.and(
        builder.equal(root.get(SITE_TYPE_CODE), CROSSING),
        builder.or(
            root.get(ROAD_SEGMENT_ID).isNull(),
            root.get(FOREST_FILE_ID).isNull(),
            root.get(ROAD_SECTION_ID).isNull(),
            root.get(CROSSING_NAME).isNull(),
            root.get(KILOMETRES).isNull()));

    return builder.or(
        root.get(SITE_STATUS_CODE).isNull(),
        root.get(INSPECTION_STATUS_CODE).isNull(),
        root.get(ORG_UNIT_NO).isNull(),
        root.get(SITE_TYPE_CODE).isNull(),
        crossingSpecific);
  }

  /**
   * Fetches what a results row displays, and applies legacy's sort: district code, then road, then
   * road section, then kilometre.
   *
   * <h3>Why the sort is here and not on the {@code Pageable}</h3>
   * Two of the four keys live on joined tables, and Spring Data resolves a sort path such as
   * {@code orgUnit.orgUnitCode} with an <em>inner</em> join — which would drop every site without an
   * org unit, including all the ones "Incomplete Data?" exists to find. Ordering inside the
   * specification is what lets the join type be stated.
   *
   * <h3>Why the joins are fetches</h3>
   * Three of the four values in a results row come from associations. Left lazy, a page of twenty
   * sites costs twenty extra selects per association after the one that fetched the page — the
   * classic N+1, invisible in a test with three rows and very visible on a district. Fetching them
   * alongside the page makes it one query. These are all to-one, so no row is duplicated and no
   * {@code distinct} is needed.
   *
   * <p>The fetch doubles as the join the ordering uses, rather than adding a second one for the same
   * association.
   *
   * <h3>Why both are skipped for the count query</h3>
   * A count has no ordering, and a fetch join in one is invalid — Hibernate rejects it, because
   * there is no entity to fetch into. Spring Data runs the count from this same specification, so
   * the guard is what lets one specification serve both. The guard itself is now on the caller,
   * which needs the same answer to decide whether the road section is a fetch or a plain join.
   *
   * @param roadSection the join {@code matching} already made, reused here rather than made again
   */
  private static void fetchAndOrder(
      Root<CrossingSiteEntity> root,
      From<?, ?> roadSection,
      CriteriaQuery<?> query,
      CriteriaBuilder builder) {
    From<?, ?> orgUnit = (From<?, ?>) root.fetch(ORG_UNIT, JoinType.LEFT);
    root.fetch(STATUS, JoinType.LEFT);

    query.orderBy(
        builder.asc(orgUnit.get(ORG_UNIT_CODE)),
        builder.asc(roadSection.get(ROAD_SECTION_NAME)),
        builder.asc(root.get(ROAD_SECTION_ID)),
        builder.asc(root.get(KILOMETRES)));
  }

  /**
   * "Designated Maintainer" — the sites whose client number belongs to a client whose name matches.
   *
   * <p>A subquery rather than a join, and the reason is the mapping this replaced.
   * {@code CrossingSiteEntity} used to carry a {@code @ManyToOne} to the client for this one
   * criterion. That association was unusable as written and expensive as configured:
   *
   * <ul>
   *   <li><b>It loaded on every row of every search, whether or not anyone filtered on it.</b> It
   *       was marked {@code FetchType.LAZY}, but it also carried {@code @NotFound(IGNORE)} — and
   *       Hibernate cannot honour both, because it has to look for the row before it can choose
   *       between an entity and a null. Nothing on either search screen displays a maintainer, so
   *       it was never fetched, so it loaded one select at a time: up to a full page of extra round
   *       trips, invisible on any page where the sites happened to share a client. Inspection Search
   *       inherited the same cost through the same mapping.</li>
   *   <li><b>It was keyed on {@code CLIENT_NUMBER} alone,</b> where the real constraint
   *       ({@code CRS_CL_FK1}) is the pair {@code (CLIENT_NUMBER, CLIENT_LOCN_CODE)}. Good enough to
   *       filter by name; not the right key to resolve <em>which</em> client, which is what a screen
   *       showing a maintainer would need. Legacy agrees: {@code site.jsp} fetches the maintainer
   *       with its own keyed request on both columns rather than joining it into the site query.</li>
   * </ul>
   *
   * <p>The predicate is unchanged in meaning. A site with no client number is excluded either way —
   * a null foreign key produces no join row, and {@code null IN (…)} is unknown — and so is one
   * whose client the view does not return. It is also safer in one respect: a left join to a view
   * that returned two rows for a client number would duplicate the site in the results and inflate
   * the total, which {@code IN} cannot do.
   */
  private static Optional<Predicate> maintainedBy(
      CriteriaBuilder builder,
      CriteriaQuery<?> query,
      Root<CrossingSiteEntity> root,
      String name) {
    if (!StringUtils.hasText(name)) {
      return Optional.empty();
    }
    Subquery<String> clients = query.subquery(String.class);
    Root<ClientPublicEntity> client = clients.from(ClientPublicEntity.class);
    clients.select(client.get(CLIENT_NUMBER))
        .where(builder.like(
            builder.upper(client.get(CLIENT_NAME)),
            WILDCARD + name.trim().toUpperCase(Locale.ROOT) + WILDCARD));

    return Optional.of(root.get(CLIENT_NUMBER).in(clients));
  }

  /**
   * Whether this execution of the specification returns entities rather than a count.
   *
   * <p>Spring Data runs the page and the count from the same specification. A fetch join in a count
   * is invalid — Hibernate rejects it — and a count has no ordering, so both are guarded on this.
   */
  private static boolean returnsEntities(CriteriaQuery<?> query) {
    return query != null
        && !Long.class.equals(query.getResultType())
        && !long.class.equals(query.getResultType());
  }

  /**
   * One left join, fetched when the query returns entities.
   *
   * <p>The cast is safe and is the standard way to use a fetch as a join: Hibernate's {@code Fetch}
   * implementations are {@code Join}s. It is what lets one join serve both the predicate and the
   * projection instead of creating a second one.
   */
  private static From<?, ?> joinOrFetch(
      Root<CrossingSiteEntity> root, String attribute, boolean projecting) {
    return projecting
        ? (From<?, ?>) root.fetch(attribute, JoinType.LEFT)
        : root.join(attribute, JoinType.LEFT);
  }

  /**
   * Legacy's {@code Search.LIKE}: an unanchored, case-insensitive contains.
   *
   * <p><b>The case folding is legacy's, and it was missing here.</b>
   * {@code AbstractOracleDMLDAO.generateWhere} emits
   * {@code UPPER(col) LIKE UPPER('%'||?||'%')} for every criterion not declared case-sensitive,
   * and nothing on either search form declares one — {@code SearchCriteriaDTO.caseSensitive} is a
   * primitive that defaults to {@code false}, and the only two calls that set it true are
   * {@code EQUALS} predicates in an internal count, which never reach this branch at all.
   *
   * <p>Without it these matched only the stored casing. That is invisible on a code column, which
   * is upper-case either way, and wrong on every name: typing {@code Deadman Creek} found nothing,
   * because {@code CROSSING_NAME} holds {@code DEADMAN CREEK}.
   *
   * <p><b>No index is given up by wrapping the column.</b> The leading wildcard already rules out a
   * range scan, so these were full scans before {@code UPPER} was applied and are full scans after.
   *
   * <p>The value is folded in Java rather than by a second {@code UPPER} in SQL. It is the same
   * comparison, one function call cheaper per row, and {@link Locale#ROOT} keeps it so — a
   * default-locale fold turns a Turkish {@code i} into {@code İ} and stops matching.
   */
  private static Optional<Predicate> contains(
      CriteriaBuilder builder, Expression<String> path, String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    return Optional.of(builder.like(
        builder.upper(path), WILDCARD + value.trim().toUpperCase(Locale.ROOT) + WILDCARD));
  }

  private static Optional<Predicate> equals(
      CriteriaBuilder builder, Expression<String> path, String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    return Optional.of(builder.equal(path, value.trim()));
  }

  private static Optional<Predicate> equalsNumber(
      CriteriaBuilder builder, Expression<Long> path, String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    try {
      return Optional.of(builder.equal(path, Long.valueOf(value.trim())));
    } catch (NumberFormatException ex) {
      // Legacy's SiteSearchForm rejects a non-numeric org unit in validate(); by the time a value
      // reaches a query it has been checked. Treating it as "unset" rather than throwing keeps a
      // malformed query parameter from becoming a 500.
      return Optional.empty();
    }
  }

  /**
   * A kilometre range. <b>Inclusive at both ends, in all three forms.</b>
   *
   * <p>Legacy is inconsistent: with both bounds filled it emits {@code BETWEEN}, which includes
   * them, but with one bound filled it emits {@code Search.GREATER_THAN} / {@code LESS_THAN} —
   * literally {@code ">"} and {@code "<"}. So "from 5 to 10" finds a site at km 5 and "from 5" does
   * not. Nothing depends on the asymmetry and no user could predict it.
   *
   * <p>Legacy also guards the User Km upper bound with a test on {@code kiloEnd} rather than
   * {@code userKmEnd} — the wrong getter, copy-pasted — so "User Kilometres To" on its own is
   * silently dropped. Here each bound stands on its own, which is the whole point of passing them
   * through one helper.
   */
  private static Optional<Predicate> range(
      CriteriaBuilder builder, Expression<BigDecimal> path, String from, String to) {
    BigDecimal lower = decimal(from);
    BigDecimal upper = decimal(to);
    if (lower != null && upper != null) {
      return Optional.of(builder.between(path, lower, upper));
    }
    if (lower != null) {
      return Optional.of(builder.greaterThanOrEqualTo(path, lower));
    }
    if (upper != null) {
      return Optional.of(builder.lessThanOrEqualTo(path, upper));
    }
    return Optional.empty();
  }

  private static BigDecimal decimal(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException ex) {
      // @Pattern on SiteSearchCriteria rejects these with a 400 before they reach here.
      return null;
    }
  }
}
