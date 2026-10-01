/**
 * Sorting a server-paged results table by its headers — shared by Site Search and Inspection
 * Search.
 *
 * <p>Both are paged on the server, so a header sorts every match there rather than the rows on
 * screen. Neither legacy table sorted by header; each falls back to its legacy order when no
 * header is chosen, and that order breaks every tie when one is.
 */

/** A header the user sorted by, and which way. `null` where used means the default order. */
export type HeaderSort<Column extends string> = { column: Column; direction: 'ASC' | 'DESC' };

/**
 * What a click on a header does: a new column sorts ascending, the same column turns descending,
 * and a third click goes back to the default order — the cycle Carbon's own sortable tables use.
 */
export const nextSort = <Column extends string>(
  current: HeaderSort<Column> | null,
  column: Column,
): HeaderSort<Column> | null => {
  if (current?.column !== column) return { column, direction: 'ASC' };
  return current.direction === 'ASC' ? { column, direction: 'DESC' } : null;
};

/** A header's direction for Carbon: its own when it is the sorted column, none otherwise. */
export const directionOf = <Column extends string>(
  current: HeaderSort<Column> | null,
  column: Column,
): 'ASC' | 'DESC' | 'NONE' => (current?.column === column ? current.direction : 'NONE');

/** The query parameters a sort travels as — none at all for the default order. */
export const sortParams = <Column extends string>(
  sort: HeaderSort<Column> | null,
): Record<string, string> => (sort ? { sortBy: sort.column, sortDirection: sort.direction } : {});
