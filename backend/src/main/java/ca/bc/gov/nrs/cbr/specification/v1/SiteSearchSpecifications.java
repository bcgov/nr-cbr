package ca.bc.gov.nrs.cbr.specification.v1;

import ca.bc.gov.nrs.cbr.model.v1.CrossingSiteEntity;
import ca.bc.gov.nrs.cbr.struct.v1.SiteSearchCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

  /** Legacy's {@code Search.LIKE} is an unanchored, case-sensitive contains. */
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
  private static final String CLIENT = "client";
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

      // Straight scalar columns on CROSSING_SITE. No join is needed for any of these, which is why
      // the Forest District and Management Area filters read the *_NO columns rather than going
      // through the org-unit associations.
      contains(builder, root.get(SITE_ID), criteria.siteId()).ifPresent(predicates::add);
      contains(builder, root.get(FOREST_FILE_ID), criteria.forestFileId()).ifPresent(predicates::add);
      contains(builder, root.get(ROAD_SECTION_ID), criteria.roadSectionId())
          .ifPresent(predicates::add);
      contains(builder, root.get(CROSSING_NAME), criteria.crossingName())
          .ifPresent(predicates::add);

      equals(builder, root.get(SITE_STATUS_CODE), criteria.siteStatusCode())
          .ifPresent(predicates::add);
      equals(builder, root.get(INSPECTION_STATUS_CODE),
          criteria.structureInspectionStatusCode()).ifPresent(predicates::add);
      equals(builder, root.get(SPECIAL_ACCESS_CODE), criteria.specialAccessCode())
          .ifPresent(predicates::add);
      equals(builder, root.get(SITE_TYPE_CODE), criteria.siteTypeCode())
          .ifPresent(predicates::add);
      equals(builder, root.get(CLIENT_NUMBER), criteria.clientNumber()).ifPresent(predicates::add);
      equals(builder, root.get(CLIENT_LOCATION_CODE), criteria.clientLocationCode())
          .ifPresent(predicates::add);

      equalsNumber(builder, root.get(ORG_UNIT_NO), criteria.orgUnit()).ifPresent(predicates::add);
      equalsNumber(builder, root.get(MANAGEMENT_ORG_UNIT_NO), criteria.managementOrgUnit())
          .ifPresent(predicates::add);

      // Capital Road is a Y/N indicator, and the filter is one-way: switched on it means "capital
      // roads only", switched off it means "do not filter" — not "non-capital roads only". Legacy
      // adds the criterion only when the box is ticked, and the toggle's wording follows from that.
      if (Boolean.TRUE.equals(criteria.capitalRoad())) {
        predicates.add(builder.equal(root.get(CAPITAL_ROAD_IND), YES));
      }

      range(builder, root.get(KILOMETRES), criteria.kiloStart(),
          criteria.kiloEnd()).ifPresent(predicates::add);
      range(builder, root.get(USER_KM), criteria.userKmStart(), criteria.userKmEnd())
          .ifPresent(predicates::add);

      if (Boolean.TRUE.equals(criteria.incomplete())) {
        predicates.add(incomplete(builder, root));
      }

      // The two joined criteria. LEFT so that a site with no road section or no client is still a
      // candidate for the other filters; the predicate itself then excludes it, which is what an
      // inner join would have done anyway — but only for these criteria, rather than for the whole
      // query.
      if (StringUtils.hasText(criteria.forestServiceRoad())) {
        From<?, ?> roadSection = root.join(ROAD_SECTION, JoinType.LEFT);
        contains(builder, roadSection.get(ROAD_SECTION_NAME), criteria.forestServiceRoad())
            .ifPresent(predicates::add);
      }
      if (StringUtils.hasText(criteria.primaryUserName())) {
        From<?, ?> client = root.join(CLIENT, JoinType.LEFT);
        contains(builder, client.get(CLIENT_NAME), criteria.primaryUserName())
            .ifPresent(predicates::add);
      }

      fetchAndOrder(root, query, builder);

      return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(new Predicate[0]));
    };
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
   * the guard is what lets one specification serve both.
   */
  private static void fetchAndOrder(
      Root<CrossingSiteEntity> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
    if (query == null || Long.class.equals(query.getResultType())
        || long.class.equals(query.getResultType())) {
      return;
    }
    From<?, ?> orgUnit = (From<?, ?>) root.fetch(ORG_UNIT, JoinType.LEFT);
    From<?, ?> roadSection = (From<?, ?>) root.fetch(ROAD_SECTION, JoinType.LEFT);
    root.fetch(STATUS, JoinType.LEFT);

    query.orderBy(
        builder.asc(orgUnit.get(ORG_UNIT_CODE)),
        builder.asc(roadSection.get(ROAD_SECTION_NAME)),
        builder.asc(root.get(ROAD_SECTION_ID)),
        builder.asc(root.get(KILOMETRES)));
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
