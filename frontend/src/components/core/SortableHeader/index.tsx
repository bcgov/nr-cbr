import { Close } from '@carbon/icons-react';
import { Button, TableHeader } from '@carbon/react';

import type { SortOrder } from '@/utils/headerSort';

import { directionOf, nextSort, positionOf } from '@/utils/headerSort';

import './index.scss';

type Props<Column extends string> = {
  'column': Column;
  /** The columns the table is sorted by, first deciding; empty for its default order. */
  'sort': SortOrder<Column>;
  /** Absent where the table is shown without sorting: a plain header then. */
  'onSortChange'?: (next: SortOrder<Column>) => void;
  /** The header's text — plain, as it also names the column in the sort button's description. */
  'children': string;
  'className'?: string;
  'data-testid'?: string;
};

/**
 * A table header that sorts — every sortable table's, so they all sort the same way.
 *
 * <p>A click adds this column to the columns already sorted, to break their ties, or turns or takes
 * it out when it is sorted already (see `nextSort`). Sorted by more than one, only the arrows show
 * on screen; a screen reader also hears each sorted header's place in the order.
 */
/**
 * What the next click on a header does, for a screen reader. Carbon's own wording, but from the
 * text: Carbon writes the header's children into it, which turns anything beside the text into
 * "false" or "[object Object]". And a click on an unsorted header adds it once something else is
 * sorted.
 */
const describeClick = <Column extends string>(
  sort: SortOrder<Column>,
  label: string,
  direction: 'ASC' | 'DESC' | 'NONE',
): string => {
  if (direction === 'ASC') return `Click to sort rows by ${label} header in descending order`;
  if (direction === 'DESC') return `Click to unsort rows by ${label} header`;
  if (sort.length > 0) return `Click to add the ${label} header to the sort, in ascending order`;
  return `Click to sort rows by ${label} header in ascending order`;
};

const SortableHeader = <Column extends string>({
  column,
  sort,
  onSortChange,
  children,
  className,
  'data-testid': testId,
}: Props<Column>) => {
  const position = positionOf(sort, column);
  const direction = directionOf(sort, column);
  return (
    <TableHeader
      className={className}
      isSortable={Boolean(onSortChange)}
      isSortHeader={position > 0}
      sortDirection={direction}
      translateWithId={() => describeClick(sort, children, direction)}
      onClick={() => onSortChange?.(nextSort(sort, column))}
      data-testid={testId}
    >
      {children}
      {sort.length > 1 && position > 0 && (
        <span className="cds--visually-hidden">
          , sort {position} of {sort.length}
        </span>
      )}
    </TableHeader>
  );
};

/**
 * Back to the table's default order in one click — shown only while the table is sorted, as there
 * is nothing to clear otherwise. Placed above the table it clears, at its right.
 */
export const ClearSort = <Column extends string>({
  sort,
  onSortChange,
  'data-testid': testId,
}: {
  'sort': SortOrder<Column>;
  'onSortChange'?: (next: SortOrder<Column>) => void;
  'data-testid'?: string;
}) =>
  onSortChange && sort.length > 0 ? (
    <div className="clear-sort">
      <Button
        kind="ghost"
        size="sm"
        renderIcon={Close}
        onClick={() => onSortChange([])}
        data-testid={testId}
      >
        Clear sort
      </Button>
    </div>
  ) : null;

export default SortableHeader;
