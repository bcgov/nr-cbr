/**
 * Sorting a table by its headers — shared by every table that sorts.
 *
 * <p>A table sorts by one column or several. A click on a header adds that column after the ones
 * already sorted, so it breaks their ties; Clear sort empties the sort. The table's legacy order
 * breaks whatever ties are left, and is the whole order when no header is chosen. No legacy table
 * sorted by header.
 *
 * <p>A table paged on the server sorts there, every row rather than the rows on screen, and sends
 * its sort with `sortParams`; a table that holds every row sorts in the browser with `sortRows`.
 */

/** One column a table is sorted by, and which way. */
export type HeaderSort<Column extends string> = { column: Column; direction: 'ASC' | 'DESC' };

/**
 * The columns a table is sorted by, the first deciding and each next breaking the ties before it.
 * Empty is the table's default order.
 */
export type SortOrder<Column extends string> = HeaderSort<Column>[];

/**
 * What a click on a header does: its column joins the sort, added last, ascending; then is turned
 * descending where it stands; then is taken out, leaving the others as they were. Alone in the
 * sort, that is the cycle Carbon's own sortable tables use — ascending, descending, default.
 */
export const nextSort = <Column extends string>(
  current: SortOrder<Column>,
  column: Column,
): SortOrder<Column> => {
  const index = current.findIndex((sort) => sort.column === column);
  if (index === -1) return [...current, { column, direction: 'ASC' }];
  if (current[index].direction === 'ASC') {
    return current.map((sort, at) => (at === index ? { column, direction: 'DESC' } : sort));
  }
  return current.filter((_, at) => at !== index);
};

/** A header's direction for Carbon: its own when the table is sorted by it, none otherwise. */
export const directionOf = <Column extends string>(
  current: SortOrder<Column>,
  column: Column,
): 'ASC' | 'DESC' | 'NONE' => current.find((sort) => sort.column === column)?.direction ?? 'NONE';

/** A header's place in the sort, from 1, or 0 when the table is not sorted by it. */
export const positionOf = <Column extends string>(
  current: SortOrder<Column>,
  column: Column,
): number => current.findIndex((sort) => sort.column === column) + 1;

/**
 * The query parameters a sort travels as: `sortBy` and `sortDirection` repeated in the same order,
 * one pair a column — none at all for the default order.
 */
export const sortParams = <Column extends string>(
  sort: SortOrder<Column>,
): Record<string, string[]> =>
  sort.length === 0
    ? {}
    : {
        sortBy: sort.map((header) => header.column),
        sortDirection: sort.map((header) => header.direction),
      };

/**
 * A sort read back from where a page kept it, which may predate sorting by several columns: a
 * single `{ column, direction }`, a list of them, or nothing.
 */
export const toSortOrder = <Column extends string>(saved: unknown): SortOrder<Column> => {
  if (!saved) return [];
  const entries: unknown[] = Array.isArray(saved) ? saved : [saved];
  return entries.filter(
    (entry): entry is HeaderSort<Column> =>
      typeof entry === 'object' &&
      entry !== null &&
      typeof (entry as HeaderSort<Column>).column === 'string' &&
      ((entry as HeaderSort<Column>).direction === 'ASC' ||
        (entry as HeaderSort<Column>).direction === 'DESC'),
  );
};

/** What a row shows in a column, for sorting in the browser. Blank is null, undefined or ''. */
export type SortValue = string | number | null | undefined;

const isBlank = (value: SortValue): value is null | undefined | '' =>
  value === null || value === undefined || value === '';

/** Ascending: blanks after everything, as Oracle sorts them; text without regard to case. */
const ascending = (a: SortValue, b: SortValue): number => {
  if (isBlank(a) || isBlank(b)) return Number(isBlank(a)) - Number(isBlank(b));
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  const left = String(a).toUpperCase();
  const right = String(b).toUpperCase();
  return left < right ? -1 : Number(left > right);
};

/**
 * Sorting a table that holds every row in the browser — where the server's order is not needed
 * because nothing is paged away from it. The rows come in their default order; each column of the
 * sort breaks the ties of the one before, and the sort is stable, so the default order breaks
 * whatever ties are left. An empty sort returns them as they came.
 *
 * <p>Sorted as the server-paged tables are, so a table sorts the same whichever side does it: text
 * without regard to case, and a blank last ascending and first descending, as Oracle places a null.
 * Dates are ISO strings, which sort as text in date order.
 */
export const sortRows = <Row, Column extends string>(
  rows: readonly Row[],
  sort: SortOrder<Column>,
  valueOf: (row: Row, column: Column) => SortValue,
): Row[] => {
  if (sort.length === 0) return [...rows];
  return [...rows].sort((a, b) => {
    for (const { column, direction } of sort) {
      const order = ascending(valueOf(a, column), valueOf(b, column));
      if (order !== 0) return direction === 'ASC' ? order : -order;
    }
    return 0;
  });
};
