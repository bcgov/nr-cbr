import {
  Button,
  DataTableSkeleton,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableBatchActions,
  TableRow,
  TableSelectRow,
  TableToolbar,
} from '@carbon/react';
import { Link } from 'react-router-dom';

import ExternalLink from '@/components/core/ExternalLink';

import type {
  SelectedStructure,
  StructureSearchResult,
  StructureSort,
  StructureSortColumn,
} from './types';
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
  /**
   * The ids of the ticked structures, across every page — or `undefined` when the user may not
   * select at all, which leaves the checkbox column off the table.
   */
  selected?: ReadonlyMap<string, SelectedStructure>;
  onToggle?: (row: StructureSearchResult) => void;
  onClearSelection?: () => void;
  /** Archive and Delete — the legacy `/deleteStructure` gate, Level 2 and above. */
  canDelete?: boolean;
  onArchive?: () => void;
  onDelete?: () => void;
  onUpdateRepairResponsibility?: () => void;
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

/** "00001012-01 CANFOR CORPORATION"; blank parts drop out rather than leaving a stray dash. */
const maintainer = (row: StructureSearchResult): string =>
  [[row.clientNumber, row.clientLocationCode].filter(Boolean).join('-'), row.clientName]
    .filter(Boolean)
    .join(' ');

/** "1 structure selected", "3 structures selected" — Carbon's own wording says "items". */
const selectedText = (id: string, state?: Record<string, unknown>): string => {
  const count = Number(state?.totalSelected ?? 0);
  if (id === 'carbon.table.batch.cancel') return 'Clear selection';
  return `${count} structure${count === 1 ? '' : 's'} selected`;
};

/**
 * Structure Search results.
 *
 * <p>Legacy's columns in legacy's order, with its second row — Client # and Client Name — folded
 * into one Maintainer column. Structure # opens the structure — a placeholder page for now — and
 * Site # opens the site, as it does on Site Search. Every header sorts, on the server.
 *
 * <p><b>A checkbox per row</b> for a role that may act on structures, as legacy's column is gated
 * on `/deleteStructure` or `/updateRepairResponsibility` — Level 1 and above. No select-all, as in
 * legacy. Unlike legacy, a tick survives paging and sorting, so the bar above the table counts
 * every ticked structure, including those on pages not in view.
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
  selected,
  onToggle,
  onClearSelection,
  canDelete = false,
  onArchive,
  onDelete,
  onUpdateRepairResponsibility,
}) => {
  const selectable = selected !== undefined;
  const columnCount = COLUMNS.length + (selectable ? 1 : 0);

  if (loading) {
    return (
      <DataTableSkeleton
        role="progressbar"
        aria-label="Searching"
        data-testid="structure-search-loading"
        columnCount={columnCount}
        rowCount={5}
        showHeader={false}
        showToolbar={false}
      />
    );
  }

  const matches = `${totalItems} match${totalItems === 1 ? '' : 'es'}`;
  // An action needs something to act on; legacy's buttons accepted none and answered "No
  // structures have been selected." from the server.
  const nothingSelected = !selected || selected.size === 0;

  return (
    <>
      <TableContainer
        className="bordered-table structure-search__results"
        data-testid="structure-search-results"
      >
        {/* Carbon's own header classes, rendered here rather than through `title` because its
            header has no slot for actions beside the title. */}
        <div className="cds--data-table-header structure-search__results-header">
          <h4 className="cds--data-table-header__title" id="structure-search-results-title">
            Structures — {matches}
          </h4>
          {selectable && (
            <div className="structure-search__bulk-actions">
              {canDelete && (
                <>
                  <Button
                    kind="tertiary"
                    size="md"
                    disabled={nothingSelected}
                    onClick={onArchive}
                    data-testid="structure-search-archive"
                  >
                    Archive
                  </Button>
                  <Button
                    kind="danger--tertiary"
                    size="md"
                    disabled={nothingSelected}
                    onClick={onDelete}
                    data-testid="structure-search-delete"
                  >
                    Delete
                  </Button>
                </>
              )}
              <Button
                kind="tertiary"
                size="md"
                disabled={nothingSelected}
                onClick={onUpdateRepairResponsibility}
                data-testid="structure-search-update-repair-responsibility"
              >
                Update Repair Responsibility
              </Button>
            </div>
          )}
        </div>
        {/* Only while something is ticked. Carbon's toolbar holds a full-height row even with its
            bar hidden, which left an empty band between the title and the headers. */}
        {selectable && selected.size > 0 && (
          <TableToolbar aria-label="Selected structures">
            <TableBatchActions
              shouldShowBatchActions
              totalSelected={selected.size}
              onCancel={() => onClearSelection?.()}
              translateWithId={selectedText}
              data-testid="structure-search-selection"
            />
          </TableToolbar>
        )}
        <Table useZebraStyles size="lg" aria-labelledby="structure-search-results-title">
          <TableHead>
            <TableRow>
              {selectable && (
                // No select-all, as in legacy — the header cell only names the column for a
                // screen reader.
                <TableHeader>
                  <span className="cds--visually-hidden">Select</span>
                </TableHeader>
              )}
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
                <TableCell colSpan={columnCount}>No structures found.</TableCell>
              </TableRow>
            )}
            {results.map((row) => (
              <TableRow
                key={row.id}
                data-testid={`structure-row-${row.id}`}
                isSelected={selected?.has(row.id)}
              >
                {selectable && (
                  <TableSelectRow
                    id={`structure-select-${row.id}`}
                    name="selectedStructures"
                    aria-label={`Select structure ${row.structureName || row.id}`}
                    checked={selected.has(row.id)}
                    onSelect={() => onToggle?.(row)}
                  />
                )}
                <TableCell>
                  <Link
                    to={`/inventory/structure/${row.id}`}
                    state={{ structureName: row.structureName } satisfies StructureLinkState}
                  >
                    {row.structureName || row.id}
                  </Link>
                </TableCell>
                <TableCell>
                  {/* A new tab, as on Structure Detail, so the results stay where they are. */}
                  {row.siteId && (
                    <ExternalLink to={`/inventory/site/${row.siteId}`}>{row.siteId}</ExternalLink>
                  )}
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
