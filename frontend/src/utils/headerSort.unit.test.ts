import { describe, expect, it } from 'vitest';

import {
  directionOf,
  nextSort,
  positionOf,
  sortParams,
  sortRows,
  toSortOrder,
  type SortOrder,
} from './headerSort';

type Row = { id: string; name: string | null; count: number | null };

const rows: Row[] = [
  { id: 'a', name: 'bravo', count: 3 },
  { id: 'b', name: null, count: 10 },
  { id: 'c', name: 'Alpha', count: null },
  { id: 'd', name: 'alpha', count: 3 },
];

const ids = (sorted: Row[]) => sorted.map((row) => row.id);
const valueOf = (row: Row, column: 'name' | 'count') => row[column];

describe('a click on a header', () => {
  it('cycles one column ascending, descending, then back to the default order', () => {
    const first = nextSort<'name' | 'count'>([], 'name');
    expect(first).toEqual([{ column: 'name', direction: 'ASC' }]);
    const second = nextSort(first, 'name');
    expect(second).toEqual([{ column: 'name', direction: 'DESC' }]);
    expect(nextSort(second, 'name')).toEqual([]);
  });

  it('adds its column last, then turns it descending in place, then takes it out', () => {
    const name: SortOrder<'name' | 'count'> = [{ column: 'name', direction: 'DESC' }];

    const added = nextSort(name, 'count');
    expect(added).toEqual([
      { column: 'name', direction: 'DESC' },
      { column: 'count', direction: 'ASC' },
    ]);
    const turned = nextSort(added, 'count');
    expect(turned).toEqual([
      { column: 'name', direction: 'DESC' },
      { column: 'count', direction: 'DESC' },
    ]);
    expect(nextSort(turned, 'count')).toEqual(name);
  });

  it('turns an earlier column without moving it', () => {
    const both: SortOrder<'name' | 'count'> = [
      { column: 'name', direction: 'ASC' },
      { column: 'count', direction: 'ASC' },
    ];
    expect(nextSort(both, 'name')).toEqual([
      { column: 'name', direction: 'DESC' },
      { column: 'count', direction: 'ASC' },
    ]);
  });
});

describe('a header', () => {
  const both: SortOrder<'name' | 'count' | 'id'> = [
    { column: 'name', direction: 'DESC' },
    { column: 'count', direction: 'ASC' },
  ];

  it('knows its direction and its place in the sort', () => {
    expect(directionOf(both, 'name')).toBe('DESC');
    expect(directionOf(both, 'id')).toBe('NONE');
    expect(positionOf(both, 'count')).toBe(2);
    expect(positionOf(both, 'id')).toBe(0);
  });

  it('travels as sortBy and sortDirection pairs, in order — none for the default', () => {
    expect(sortParams(both)).toEqual({
      sortBy: ['name', 'count'],
      sortDirection: ['DESC', 'ASC'],
    });
    expect(sortParams([])).toEqual({});
  });
});

describe('a kept sort', () => {
  it('reads back a single one, kept before several columns could sort, as a list of one', () => {
    expect(toSortOrder({ column: 'name', direction: 'DESC' })).toEqual([
      { column: 'name', direction: 'DESC' },
    ]);
  });

  it('reads back a list, dropping anything that is not a sort', () => {
    expect(
      toSortOrder([{ column: 'name', direction: 'ASC' }, { column: 'count' }, 'nonsense', null]),
    ).toEqual([{ column: 'name', direction: 'ASC' }]);
    expect(toSortOrder(null)).toEqual([]);
    expect(toSortOrder(undefined)).toEqual([]);
  });
});

describe('sorting in the browser', () => {
  it('sorts text without regard to case, the default order breaking ties, blanks last', () => {
    expect(ids(sortRows(rows, [{ column: 'name', direction: 'ASC' }], valueOf))).toEqual([
      'c',
      'd',
      'a',
      'b',
    ]);
  });

  it('reverses for descending, a blank first as Oracle places a null', () => {
    expect(ids(sortRows(rows, [{ column: 'name', direction: 'DESC' }], valueOf))).toEqual([
      'b',
      'a',
      'c',
      'd',
    ]);
  });

  it('sorts numbers as numbers, not as text', () => {
    expect(ids(sortRows(rows, [{ column: 'count', direction: 'ASC' }], valueOf))).toEqual([
      'a',
      'd',
      'b',
      'c',
    ]);
  });

  it("breaks the first column's ties with the next", () => {
    // a and d tie on 3; by name descending, "bravo" before "alpha".
    expect(
      ids(
        sortRows(
          rows,
          [
            { column: 'count', direction: 'ASC' },
            { column: 'name', direction: 'DESC' },
          ],
          valueOf,
        ),
      ),
    ).toEqual(['a', 'd', 'b', 'c']);
    // by name ascending, "alpha" before "bravo".
    expect(
      ids(
        sortRows(
          rows,
          [
            { column: 'count', direction: 'ASC' },
            { column: 'name', direction: 'ASC' },
          ],
          valueOf,
        ),
      ),
    ).toEqual(['d', 'a', 'b', 'c']);
  });

  it('returns the rows as they came, and leaves them alone, with no sort', () => {
    const sorted = sortRows(rows, [], valueOf);
    expect(ids(sorted)).toEqual(['a', 'b', 'c', 'd']);
    expect(sorted).not.toBe(rows);
  });
});
