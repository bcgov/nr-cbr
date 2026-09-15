import { TrashCan } from '@carbon/icons-react';
import {
  Button,
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

import type { SiteSearchResult } from './types';
import type { FC } from 'react';

type Props = {
  results: SiteSearchResult[];
  totalItems: number;
  page: number;
  pageSize: number;
  /** Delete is gated on the legacy `/deleteSite` privilege — CBR_LEVEL_2 and above. */
  canDelete: boolean;
  onPageChange: (next: { page: number; pageSize: number }) => void;
  onDelete: (site: SiteSearchResult) => void;
};

/**
 * Site Search results.
 *
 * <p>The same seven columns the legacy table shows, in the same order, plus the Delete column that
 * legacy wraps in `<cbr:authorize grantedAction="/deleteSite">`. Rendering that column only when the
 * caller holds the privilege is the legacy rule carried forward: an action you cannot perform is
 * never rendered, not disabled (cbr-navigation.local.md §2).
 */
const SiteSearchResults: FC<Props> = ({
  results,
  totalItems,
  page,
  pageSize,
  canDelete,
  onPageChange,
  onDelete,
}) => (
  <>
    <TableContainer title="Sites" className="bordered-table" data-testid="site-search-results">
      <Table useZebraStyles size="lg">
        <TableHead>
          <TableRow>
            <TableHeader>Site #</TableHeader>
            <TableHeader>District Code</TableHeader>
            <TableHeader>Forest Service Road</TableHeader>
            <TableHeader>KM</TableHeader>
            <TableHeader>Crossing Name</TableHeader>
            <TableHeader>Project File ID#-Br.</TableHeader>
            <TableHeader>Status</TableHeader>
            {canDelete && <TableHeader>Delete</TableHeader>}
          </TableRow>
        </TableHead>
        <TableBody>
          {results.length === 0 ? (
            <TableRow>
              <TableCell colSpan={canDelete ? 8 : 7}>No sites found.</TableCell>
            </TableRow>
          ) : (
            results.map((site) => (
              <TableRow key={site.id} data-testid={`site-row-${site.id}`}>
                <TableCell>
                  <Link to={`/inventory/site/${site.id}`}>{site.id}</Link>
                </TableCell>
                {/* Legacy shows the code and puts the full district name in a title attribute —
                    the column is 7% wide and the names do not fit. */}
                <TableCell title={site.orgUnitName}>{site.orgUnitCode}</TableCell>
                <TableCell>{site.forestServiceRoad}</TableCell>
                <TableCell className="site-search__numeric">
                  {site.pointOfCommencementDistance}
                </TableCell>
                <TableCell>{site.crossingName}</TableCell>
                <TableCell>
                  {site.forestFileId}-{site.roadSectionId}
                </TableCell>
                <TableCell>{site.crossingSiteStatusDescription}</TableCell>
                {canDelete && (
                  <TableCell>
                    <Button
                      kind="ghost"
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

export default SiteSearchResults;
