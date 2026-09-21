package ca.bc.gov.nrs.cbr.specification.v1;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Records the SQL Hibernate actually emits, so a test can assert on its shape.
 *
 * <p>Counting statements catches an N+1; it does not catch a query that joins the same table twice,
 * which costs just as much and shows up nowhere else. Inspecting the statement is the only way to
 * see that, short of reading the log by hand — and the duplicate-join bug this exists to prevent
 * survived a full test suite and was found only in a browser's network tab, at eighteen seconds.
 *
 * <p>Registered through {@code hibernate.session_factory.statement_inspector}, which takes a class
 * name and instantiates it, hence the static collector.
 */
public class CapturingStatementInspector implements StatementInspector {

  private static final List<String> STATEMENTS = new ArrayList<>();

  @Override
  public String inspect(String sql) {
    synchronized (STATEMENTS) {
      STATEMENTS.add(sql);
    }
    return sql;
  }

  static void clear() {
    synchronized (STATEMENTS) {
      STATEMENTS.clear();
    }
  }

  /** The last statement containing {@code marker}, or an empty string. */
  static String lastContaining(String marker) {
    synchronized (STATEMENTS) {
      return STATEMENTS.stream().filter(sql -> sql.contains(marker)).reduce((a, b) -> b).orElse("");
    }
  }

  /** Every statement issued, in order — for diagnosing an unexpected count. */
  static List<String> all() {
    synchronized (STATEMENTS) {
      return List.copyOf(STATEMENTS);
    }
  }

  /** How many statements were issued. */
  static int count() {
    synchronized (STATEMENTS) {
      return STATEMENTS.size();
    }
  }

  /** How many times {@code fragment} occurs in {@code sql}. */
  static int occurrences(String sql, String fragment) {
    int found = 0;
    int at = sql.indexOf(fragment);
    while (at >= 0) {
      found++;
      at = sql.indexOf(fragment, at + fragment.length());
    }
    return found;
  }
}
