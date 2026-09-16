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
      contains(builder, root.get("crossingSiteId"), criteria.siteId()).ifPresent(predicates::add);
      contains(builder, root.get("forestFileId"), criteria.forestFileId()).ifPresent(predicates::add);
      contains(builder, root.get("roadSectionId"), criteria.roadSectionId())
          .ifPresent(predicates::add);
      contains(builder, root.get("crossingName"), criteria.crossingName())
          .ifPresent(predicates::add);

      equals(builder, root.get("crossingSiteStatusCode"), criteria.siteStatusCode())
          .ifPresent(predicates::add);
      equals(builder, root.get("structureInspectionStatusCode"),
          criteria.structureInspectionStatusCode()).ifPresent(predicates::add);
      equals(builder, root.get("specialAccessRqmtCode"), criteria.specialAccessCode())
          .ifPresent(predicates::add);
      equals(builder, root.get("crossingSiteTypeCode"), criteria.siteTypeCode())
          .ifPresent(predicates::add);
      equals(builder, root.get("clientNumber"), criteria.clientNumber()).ifPresent(predicates::add);
      equals(builder, root.get("clientLocnCode"), criteria.clientLocationCode())
          .ifPresent(predicates::add);

      equalsNumber(builder, root.get("orgUnitNo"), criteria.orgUnit()).ifPresent(predicates::add);
      equalsNumber(builder, root.get("managementOrgUnitNo"), criteria.managementOrgUnit())
          .ifPresent(predicates::add);

      // Capital Road is a Y/N indicator, and the filter is one-way: switched on it means "capital
      // roads only", switched off it means "do not filter" — not "non-capital roads only". Legacy
      // adds the criterion only when the box is ticked, and the toggle's wording follows from that.
      if (Boolean.TRUE.equals(criteria.capitalRoad())) {
        predicates.add(builder.equal(root.get("capitalRoadInd"), "Y"));
      }

      range(builder, root.get("pointOfCommencementDistance"), criteria.kiloStart(),
          criteria.kiloEnd()).ifPresent(predicates::add);
      range(builder, root.get("userKm"), criteria.userKmStart(), criteria.userKmEnd())
          .ifPresent(predicates::add);

      if (Boolean.TRUE.equals(criteria.incomplete())) {
        predicates.add(incomplete(builder, root));
      }

      // The two joined criteria. LEFT so that a site with no road section or no client is still a
      // candidate for the other filters; the predicate itself then excludes it, which is what an
      // inner join would have done anyway — but only for these criteria, rather than for the whole
      // query.
      if (StringUtils.hasText(criteria.forestServiceRoad())) {
        From<?, ?> roadSection = root.join("roadSection", JoinType.LEFT);
        contains(builder, roadSection.get("roadSectName"), criteria.forestServiceRoad())
            .ifPresent(predicates::add);
      }
      if (StringUtils.hasText(criteria.primaryUserName())) {
        From<?, ?> client = root.join("client", JoinType.LEFT);
        contains(builder, client.get("clientName"), criteria.primaryUserName())
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
        builder.equal(root.get("crossingSiteTypeCode"), "CRS"),
        builder.or(
            root.get("roadSegmentId").isNull(),
            root.get("forestFileId").isNull(),
            root.get("roadSectionId").isNull(),
            root.get("crossingName").isNull(),
            root.get("pointOfCommencementDistance").isNull()));

    return builder.or(
        root.get("crossingSiteStatusCode").isNull(),
        root.get("structureInspectionStatusCode").isNull(),
        root.get("orgUnitNo").isNull(),
        root.get("crossingSiteTypeCode").isNull(),
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
    From<?, ?> orgUnit = (From<?, ?>) root.fetch("orgUnit", JoinType.LEFT);
    From<?, ?> roadSection = (From<?, ?>) root.fetch("roadSection", JoinType.LEFT);
    root.fetch("status", JoinType.LEFT);

    query.orderBy(
        builder.asc(orgUnit.get("orgUnitCode")),
        builder.asc(roadSection.get("roadSectName")),
        builder.asc(root.get("roadSectionId")),
        builder.asc(root.get("pointOfCommencementDistance")));
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
