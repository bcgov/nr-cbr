import { TrashCan } from '@carbon/icons-react';
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
  TableRow,
  Tag,
} from '@carbon/react';
import { Link } from 'react-router-dom';

import type { SiteSearchResult, SiteSort, SiteSortColumn } from './types';
import type { FC } from 'react';

import { directionOf, nextSort } from '@/utils/headerSort';
import { siteStatusLabel, siteStatusTagType } from '@/utils/siteStatus';

type Props = {
  results: SiteSearchResult[];
  totalItems: number;
  /** True while a search is in flight. */
  loading?: boolean;
  page: number;
  pageSize: number;
  /** Delete is gated on the legacy `/deleteSite` privilege — CBR_LEVEL_2 and above. */
  canDelete: boolean;
  onPageChange: (next: { page: number; pageSize: number }) => void;
  onDelete: (site: SiteSearchResult) => void;
  /** The header the results are sorted by, or `null` for legacy's order. */
  sort?: SiteSort | null;
  onSortChange?: (next: SiteSort | null) => void;
};

/** The seven sortable columns, in the legacy table's order. */
const COLUMNS: { column: SiteSortColumn; label: string }[] = [
  { column: 'SITE_ID', label: 'Site #' },
  { column: 'DISTRICT', label: 'District Code' },
  { column: 'FOREST_SERVICE_ROAD', label: 'Forest Service Road' },
  { column: 'KILOMETRES', label: 'KM' },
  { column: 'CROSSING_NAME', label: 'Crossing Name' },
  { column: 'PROJECT_FILE', label: 'Project File ID#-Br.' },
  { column: 'STATUS', label: 'Status' },
];

/**
 * Site Search results.
 *
 * <p>The same seven columns the legacy table shows, in the same order, plus the Delete column that
 * legacy wraps in `<cbr:authorize grantedAction="/deleteSite">`. Rendering that column only when the
 * caller holds the privilege is the legacy rule carried forward: an action you cannot perform is
 * never rendered, not disabled (cbr-navigation.local.md §2).
 *
 * <p><b>Every header but Actions sorts</b>, which legacy's did not. On the server, not the page:
 * the results are paged there, so sorting only the rows on screen would order twenty sites out of
 * thousands and call it sorted.
 */
const SiteSearchResults: FC<Props> = ({
  results,
  totalItems,
  loading = false,
  page,
  pageSize,
  canDelete,
  onPageChange,
  onDelete,
  sort = null,
  onSortChange,
}) => {
  // The seven legacy columns, and Actions when the user may delete.
  const columnCount = canDelete ? 8 : 7;

  if (loading) {
    return (
      // A skeleton, not "No sites found." An empty table while a search is running says the search
      // finished and matched nothing, which is a different answer and one the user would act on.
      <DataTableSkeleton
        role="progressbar"
        aria-label="Searching"
        data-testid="site-search-loading"
        columnCount={columnCount}
        rowCount={5}
        showHeader={false}
        showToolbar={false}
      />
    );
  }

  return (
    <>
      <TableContainer
        // "Sites — 12 matches", the same shape nr-frep's search results use. The count is the server's
        // total, not the rows on screen: a page of 20 out of 2,000 would otherwise report 20.
        title={`Sites — ${totalItems} match${totalItems === 1 ? '' : 'es'}`}
        className="bordered-table"
        data-testid="site-search-results"
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
                  data-testid={`site-search-sort-${column}`}
                >
                  {label}
                </TableHeader>
              ))}
              {/* "Actions", not "Delete": the column is still gated on the destructive capability,
                    but naming it after its only current occupant would have to be renamed the moment
                    a second one lands. It stays hidden entirely when the user cannot delete — an
                    empty Actions column is noise, and legacy renders no column at all. */}
              {canDelete && <TableHeader>Actions</TableHeader>}
            </TableRow>
          </TableHead>
          <TableBody>
            {results.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columnCount}>No sites found.</TableCell>
              </TableRow>
            ) : (
              results.map((site) => (
                <TableRow key={site.id} data-testid={`site-row-${site.id}`}>
                  <TableCell>
                    <Link to={`/inventory/site/${site.id}`}>{site.id}</Link>
                  </TableCell>
                  {/* "DPG — Prince George Natural Resource District", as nr-frep renders an org
                      unit. Legacy shows the bare code and hides the name in a title attribute, which
                      its 7%-wide column forced — a tooltip only reaches a user who already suspects
                      there is more to see, and never reaches one reading on a touchscreen.

                      Joined rather than interpolated so a site with no org unit renders empty, and one
                      with a code but no name renders the code alone, instead of "DPG — undefined" or a
                      stray leading dash. */}
                  <TableCell>
                    {[site.orgUnitCode, site.orgUnitName].filter(Boolean).join(' — ')}
                  </TableCell>
                  <TableCell>{site.forestServiceRoad}</TableCell>
                  <TableCell className="site-search__numeric">
                    {site.pointOfCommencementDistance}
                  </TableCell>
                  <TableCell>{site.crossingName}</TableCell>
                  <TableCell>
                    {site.forestFileId}-{site.roadSectionId}
                  </TableCell>
                  <TableCell>
                    {/* A pill, as nr-frep renders a status. The colour comes from the code and the
                          label from the description — see utils/siteStatus for why that way round.
                          Nothing is rendered at all for a site with no status, rather than an empty
                          pill: "Incomplete Data?" exists to find those, and a bare outline would read
                          as a status whose name failed to load. */}
                    {site.crossingSiteStatusCode || site.crossingSiteStatusDescription ? (
                      <Tag type={siteStatusTagType(site.crossingSiteStatusCode)} size="sm">
                        {siteStatusLabel(
                          site.crossingSiteStatusCode,
                          site.crossingSiteStatusDescription,
                        )}
                      </Tag>
                    ) : null}
                  </TableCell>
                  {canDelete && (
                    <TableCell>
                      <Button
                        // danger--ghost rather than ghost with a recoloured icon: Carbon's danger
                        // kinds carry the whole interaction — red icon at rest, red fill on hover
                        // and focus — so the control reads as destructive before it is pressed, not
                        // only in the confirmation dialog that follows.
                        kind="danger--ghost"
                        size="sm"
                        hasIconOnly
                        renderIcon={TrashCan}
                        iconDescription={`Delete site ${site.id}`}
                        data-testid={`site-delete-${site.id}`}
                        onClick={() => onDelete(site)}
                      />
                    </TableCell>
                  )}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Legacy paginates server-side via its own <cbr:pagination> tag, which posts the form back with
          page/pageSize. Carbon's Pagination is the same contract in a different shape — the page and
          size come back out and the caller re-queries. */}
      <Pagination
        data-testid="site-search-pagination"
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

export default SiteSearchResults;
