package ca.bc.gov.nrs.cbr.specification.v1;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import org.springframework.util.StringUtils;

/**
 * The predicate shapes every search screen builds its {@code WHERE} clause from.
 *
 * <p>Each answers {@link Optional#empty()} for a criterion the user left blank, so a specification
 * adds only what was asked. Their meanings are legacy's {@code Search} operators, as Site Search
 * documents them: text is an unanchored, case-insensitive contains ({@code Search.LIKE}); codes and
 * numbers are exact; ranges are inclusive at both ends, in all three forms.
 *
 * <p>Shared by Site, Inspection and Structure Search, each of which once carried its own copy —
 * along with {@link #joinOrFetch}, which is not a predicate but is how each of them joins.
 */
final class SearchPredicates {

  private static final String WILDCARD = "%";

  private SearchPredicates() {}

  /**
   * Legacy's {@code Search.LIKE}: text containing the value, ignoring case.
   *
   * <p>The case folding is legacy's: {@code AbstractOracleDMLDAO.generateWhere} emits
   * {@code UPPER(col) LIKE UPPER('%'||?||'%')} for every criterion not declared case-sensitive, and
   * no search form declares one. Without it, typing {@code Deadman Creek} finds nothing, because
   * {@code CROSSING_NAME} holds {@code DEADMAN CREEK}.
   *
   * <p>No index is given up by wrapping the column: the leading wildcard already rules out a range
   * scan. The value is folded in Java with {@link Locale#ROOT} — a default-locale fold turns a
   * Turkish {@code i} into {@code İ} and stops matching.
   */
  static Optional<Predicate> contains(
      CriteriaBuilder builder, Expression<String> path, String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    return Optional.of(builder.like(
        builder.upper(path), WILDCARD + value.trim().toUpperCase(Locale.ROOT) + WILDCARD));
  }

  /** A code, exactly. */
  static Optional<Predicate> equalsText(
      CriteriaBuilder builder, Expression<String> path, String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    return Optional.of(builder.equal(path, value.trim()));
  }

  /**
   * An org unit number, exactly. A value that is not a number is treated as unset rather than as a
   * 500 — the form only ever sends one picked from a list.
   */
  static Optional<Predicate> equalsNumber(
      CriteriaBuilder builder, Expression<Long> path, String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    try {
      return Optional.of(builder.equal(path, Long.valueOf(value.trim())));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }

  /**
   * A decimal range, inclusive; either end may be open.
   *
   * <p><b>Inclusive in all three forms</b>, which legacy is not: with both bounds it emits
   * {@code BETWEEN}, but with one it emits a strict {@code >} or {@code <}, so "from 5 to 10" finds
   * a site at km 5 and "from 5" does not. Each bound also stands on its own — legacy guards the User
   * Km upper bound with a test on {@code kiloEnd}, the wrong getter, so "User Kilometres To" alone
   * is silently dropped.
   */
  static Optional<Predicate> decimalRange(
      CriteriaBuilder builder, Expression<BigDecimal> path, String from, String to) {
    return range(builder, path, decimal(from), decimal(to));
  }

  /** A whole-number range — a span of years — inclusive; either end may be open. */
  static Optional<Predicate> integerRange(
      CriteriaBuilder builder, Expression<Integer> path, String from, String to) {
    return range(builder, path, integer(from), integer(to));
  }

  /**
   * Whether this execution returns entities rather than a count. Spring Data runs both from one
   * specification, and a fetch or an ordering is invalid in the count.
   */
  static boolean returnsEntities(CriteriaQuery<?> query) {
    return query != null
        && !Long.class.equals(query.getResultType())
        && !long.class.equals(query.getResultType());
  }

  /**
   * One join, fetched when the query returns entities.
   *
   * <p>Every association a search joins is read by its results row, so when the query projects
   * entities each join is a fetch and the page is one statement. Left lazy they would be the
   * classic N+1 — a page of twenty rows costing twenty extra selects per association, invisible in
   * a test with three rows and very visible on a district. In a count, which cannot fetch, it is a
   * plain join, there for the predicates.
   *
   * <p>Only for to-one associations: those duplicate no row, need no {@code distinct}, and do not
   * interfere with paging the way a collection fetch would.
   *
   * <p>The cast is safe and is the standard way to use a fetch as a join: Hibernate's
   * {@code Fetch} implementations are {@code Join}s. It is what lets one join serve the predicate,
   * the ordering and the projection instead of three.
   *
   * @param type {@link JoinType#LEFT} for an association a row may lack — a site with no road, a
   *             structure with no site — so the row is kept rather than dropped
   */
  static From<?, ?> joinOrFetch(
      From<?, ?> parent, String attribute, JoinType type, boolean projecting) {
    return projecting
        ? (From<?, ?>) parent.fetch(attribute, type)
        : parent.join(attribute, type);
  }

  private static <T extends Comparable<? super T>> Optional<Predicate> range(
      CriteriaBuilder builder, Expression<T> path, T lower, T upper) {
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

  // A malformed bound reads as no bound: @Pattern on the criteria refuses it with a 400 first.
  private static BigDecimal decimal(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Integer integer(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
