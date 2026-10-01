import {
  DataTableSkeleton,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { Link } from 'react-router-dom';

import type { StructureSearchResult, StructureSort, StructureSortColumn } from './types';
import type { StructureLinkState } from '@/pages/StructureDetail';
import type { FC } from 'react';

import { directionOf, nextSort } from '@/utils/headerSort';

type Props = {
  results: StructureSearchResult[];
  totalItems: number;
  /** True while a search is in flight. */
  loading?: boolean;
  page: number;
  pageSize: number;
  onPageChange: (next: { page: number; pageSize: number }) => void;
  /** The header the results are sorted by, or `null` for legacy's order. */
  sort?: StructureSort | null;
  onSortChange?: (next: StructureSort | null) => void;
};

/** Every column sorts, on the server — the results are paged there. */
const COLUMNS: { column: StructureSortColumn; label: string }[] = [
  { column: 'STRUCTURE_NAME', label: 'Structure #' },
  { column: 'SITE_ID', label: 'Site #' },
  { column: 'DISTRICT', label: 'District Code' },
  { column: 'FOREST_SERVICE_ROAD', label: 'Forest Service Road' },
  { column: 'KILOMETRES', label: 'KM' },
  { column: 'CROSSING_NAME', label: 'Crossing Name' },
  { column: 'TYPE_CLASS', label: 'Type/Class' },
  { column: 'PROJECT_FILE', label: 'Project File ID#-Br.' },
  { column: 'MAINTAINER', label: 'Maintainer' },
];

const COLUMN_COUNT = COLUMNS.length;

/** "00001012-01 CANFOR CORPORATION"; blank parts drop out rather than leaving a stray dash. */
const maintainer = (row: StructureSearchResult): string =>
  [[row.clientNumber, row.clientLocationCode].filter(Boolean).join('-'), row.clientName]
    .filter(Boolean)
    .join(' ');

/**
 * Structure Search results.
 *
 * <p>Legacy's columns in legacy's order, with its second row — Client # and Client Name — folded
 * into one Maintainer column. Structure # opens the structure — a placeholder page for now — and
 * Site # opens the site, as it does on Site Search. Every header sorts, on the server.
 */
const StructureSearchResults: FC<Props> = ({
  results,
  totalItems,
  loading = false,
  page,
  pageSize,
  onPageChange,
  sort = null,
  onSortChange,
}) => {
  if (loading) {
    return (
      <DataTableSkeleton
        role="progressbar"
        aria-label="Searching"
        data-testid="structure-search-loading"
        columnCount={COLUMN_COUNT}
        rowCount={5}
        showHeader={false}
        showToolbar={false}
      />
    );
  }

  const matches = `${totalItems} match${totalItems === 1 ? '' : 'es'}`;

  return (
    <>
      <TableContainer
        title={`Structures — ${matches}`}
        className="bordered-table"
        data-testid="structure-search-results"
      >
        <Table useZebraStyles size="lg">
          <TableHead>
            <TableRow>
              {COLUMNS.map(({ column, label }) => (
                <TableHeader
                  key={column}
                  isSortable={Boolean(onSortChange)}
                  isSortHeader={sort?.column === column}
                  sortDirection={directionOf(sort, column)}
                  onClick={() => onSortChange?.(nextSort(sort, column))}
                  data-testid={`structure-search-sort-${column}`}
                >
                  {label}
                </TableHeader>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {results.length === 0 && (
              <TableRow>
                <TableCell colSpan={COLUMN_COUNT}>No structures found.</TableCell>
              </TableRow>
            )}
            {results.map((row) => (
              <TableRow key={row.id} data-testid={`structure-row-${row.id}`}>
                <TableCell>
                  <Link
                    to={`/inventory/structure/${row.id}`}
                    state={{ structureName: row.structureName } satisfies StructureLinkState}
                  >
                    {row.structureName || row.id}
                  </Link>
                </TableCell>
                <TableCell>
                  {row.siteId && <Link to={`/inventory/site/${row.siteId}`}>{row.siteId}</Link>}
                </TableCell>
                <TableCell>
                  {[row.orgUnitCode, row.orgUnitName].filter(Boolean).join(' — ')}
                </TableCell>
                <TableCell>{row.forestServiceRoad}</TableCell>
                <TableCell className="structure-search__numeric">{row.kilometres}</TableCell>
                <TableCell>{row.crossingName}</TableCell>
                <TableCell>{row.structureTypeClass}</TableCell>
                <TableCell>
                  {[row.forestFileId, row.roadSectionId].filter(Boolean).join('-')}
                </TableCell>
                <TableCell>{maintainer(row)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <Pagination
        data-testid="structure-search-pagination"
        page={page}
        pageSize={pageSize}
        pageSizes={[20, 50, 100]}
        totalItems={totalItems}
        onChange={({ page: nextPage, pageSize: nextPageSize }) =>
          onPageChange({ page: nextPage, pageSize: nextPageSize })
        }
      />
    </>
  );
};

export default StructureSearchResults;
