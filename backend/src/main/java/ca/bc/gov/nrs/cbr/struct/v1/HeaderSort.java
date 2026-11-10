package ca.bc.gov.nrs.cbr.struct.v1;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.data.domain.Sort;

/**
 * One column a table is sorted by, and which way.
 *
 * <p>A table sorts by one column or several: the first decides, each next breaks the ties of the
 * ones before it, and the table's legacy order breaks whatever ties are left. The columns travel as
 * repeated {@code sortBy} parameters with a {@code sortDirection} for each, in the same order —
 * {@code sortBy=STATUS&sortDirection=ASC&sortBy=PRIORITY&sortDirection=DESC}.
 *
 * @param <C> the table's sortable columns
 */
public record HeaderSort<C extends Enum<C>>(C column, Sort.Direction direction) {

  /**
   * The columns as sent, each with its direction: paired by position, a missing or blank direction
   * ascending, a blank column skipped, and a column named twice taken at its first. None — no
   * {@code sortBy} at all — is legacy's order.
   */
  public static <C extends Enum<C>> List<HeaderSort<C>> of(
      List<C> columns, List<Sort.Direction> directions) {
    if (columns == null || columns.isEmpty()) {
      return List.of();
    }
    List<HeaderSort<C>> sort = new ArrayList<>();
    EnumSet<C> seen = null;
    for (int i = 0; i < columns.size(); i++) {
      C column = columns.get(i);
      if (column == null) {
        continue;
      }
      if (seen == null) {
        seen = EnumSet.noneOf(column.getDeclaringClass());
      }
      if (!seen.add(column)) {
        continue;
      }
      Sort.Direction direction =
          directions != null && i < directions.size() && directions.get(i) != null
              ? directions.get(i)
              : Sort.Direction.ASC;
      sort.add(new HeaderSort<>(column, direction));
    }
    return List.copyOf(sort);
  }

  /** Whether this column sorts descending. */
  public boolean descending() {
    return direction == Sort.Direction.DESC;
  }
}
