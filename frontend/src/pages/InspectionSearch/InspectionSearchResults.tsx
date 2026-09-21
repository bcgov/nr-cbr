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

import type { InspectionSearchResult } from './types';
import type { FC } from 'react';

import {
  inspectionStatusLabel,
  inspectionStatusTagType,
  isOffline,
} from '@/utils/inspectionStatus';

type Props = {
  results: InspectionSearchResult[];
  totalItems: number;
  /** True while a search is in flight. */
  loading?: boolean;
  page: number;
  pageSize: number;
  /** Delete is gated on the legacy `/deleteInspection` privilege — CBR_LEVEL_2 and above. */
  canDelete: boolean;
  onPageChange: (next: { page: number; pageSize: number }) => void;
  onDelete: (inspection: InspectionSearchResult) => void;
};

/**
 * `2026-01-31` → `2026/01/31`, the format legacy's `<fmt:formatDate pattern="yyyy/MM/dd">` prints.
 *
 * <p>Kept rather than folded into the app's `formatShortDate` ("Jan 31, 2026"): this column is read
 * down as a sequence of inspections, and a fixed-width numeric date sorts by eye where a month name
 * does not. Anything that is not an ISO date is passed through untouched, so a malformed value from
 * the server shows as itself instead of as an empty cell.
 *
 * <p><b>The date can be missing.</b> `STRUCTURE_INSPECTION.INSPECTION_DATE` is a nullable column and
 * real rows leave it unset. An earlier version took a plain `string` and called `.trim()` on it,
 * which turned one such row into a blank page for the whole application — the results table renders
 * inside the router, so a throw here escapes to the route's error boundary rather than to an empty
 * cell.
 */
const formatInspectionDate = (value: string | null): string => {
  const date = value?.trim();
  if (!date) {
    return '';
  }
  return /^\d{4}-\d{2}-\d{2}$/.test(date) ? date.replaceAll('-', '/') : date;
};

/**
 * Inspection Search results.
 *
 * <p>The same ten columns the legacy table shows, in the same order. Two behaviours turn on the
 * status code, and both are legacy's:
 *
 * <ul>
 *   <li>an `OFL` row is plain text, not a link — the inspection is checked out to a field device and
 *       there is nothing on the server to open;
 *   <li>an `OFL` row is the only one that offers delete, and only to a caller holding
 *       `/deleteInspection`. Rendering the control only when the privilege is held is the legacy
 *       rule carried forward: an action you cannot perform is never rendered, not disabled
 *       (cbr-navigation.local.md §2).
 * </ul>
 */
const InspectionSearchResults: FC<Props> = ({
  results,
  totalItems,
  loading = false,
  page,
  pageSize,
  canDelete,
  onPageChange,
  onDelete,
}) =>
  loading ? (
    // A skeleton, not "No inspections found." An empty table while a search is running says the
    // search finished and matched nothing, which is a different answer and one the user would act
    // on.
    <DataTableSkeleton
      role="progressbar"
      aria-label="Searching"
      data-testid="inspection-search-loading"
      columnCount={canDelete ? 11 : 10}
      rowCount={5}
      showHeader={false}
      showToolbar={false}
    />
  ) : (
    <>
      <TableContainer
        // "Inspections — 12 matches", the shape nr-frep's search results use. The count is the
        // server's total, not the rows on screen: a page of 20 out of 2,000 would report 20.
        title={`Inspections — ${totalItems} match${totalItems === 1 ? '' : 'es'}`}
        className="bordered-table"
        data-testid="inspection-search-results"
      >
        <Table useZebraStyles size="lg">
          <TableHead>
            <TableRow>
              <TableHeader>Id</TableHeader>
              <TableHeader>Inspection Date</TableHeader>
              <TableHeader>Status</TableHeader>
              <TableHeader>Site #</TableHeader>
              <TableHeader>Structure Name</TableHeader>
              <TableHeader>District Code</TableHeader>
              <TableHeader>Forest Service Road</TableHeader>
              <TableHeader>KM</TableHeader>
              <TableHeader>Crossing Name</TableHeader>
              <TableHeader>Project File ID#-Br.</TableHeader>
              {/* "Actions", not "Delete": the column is gated on the destructive capability, but
                  naming it after its only current occupant would have to be renamed the moment a
                  second one lands. Hidden entirely when the user cannot delete, as legacy does. */}
              {canDelete && <TableHeader>Actions</TableHeader>}
            </TableRow>
          </TableHead>
          <TableBody>
            {results.length === 0 ? (
              <TableRow>
                {/* Legacy's own wording, which tells the user what to do next rather than only what
                    happened. Its colspan is 7 against a 10-column table, so the message sits under
                    part of the row; this one spans the table. */}
                <TableCell colSpan={canDelete ? 11 : 10}>
                  No inspection record found that matches search criteria, please try again.
                </TableCell>
              </TableRow>
            ) : (
              results.map((inspection) => (
                <TableRow key={inspection.id} data-testid={`inspection-row-${inspection.id}`}>
                  <TableCell>
                    {isOffline(inspection.inspectionReportStatusCode) ? (
                      inspection.id
                    ) : (
                      <Link to={`/inspection/${inspection.id}`}>{inspection.id}</Link>
                    )}
                  </TableCell>
                  <TableCell>{formatInspectionDate(inspection.inspectionDate)}</TableCell>
                  <TableCell>
                    {/* A pill, as nr-frep renders a status. The colour comes from the code and the
                        label from the description — see utils/inspectionStatus. Nothing at all for a
                        row with neither, rather than an empty outline that would read as a status
                        whose name failed to load. */}
                    {inspection.inspectionReportStatusCode ||
                    inspection.inspectionReportStatusDescription ? (
                      <Tag
                        type={inspectionStatusTagType(inspection.inspectionReportStatusCode)}
                        size="sm"
                      >
                        {inspectionStatusLabel(
                          inspection.inspectionReportStatusCode,
                          inspection.inspectionReportStatusDescription,
                        )}
                      </Tag>
                    ) : null}
                  </TableCell>
                  {/* The site the structure sat at when the inspection happened, which is not
                      necessarily where it sits now — so it links to that site, not to the
                      structure's current one. */}
                  <TableCell>
                    {inspection.siteAtTimeOfInspection ? (
                      <Link to={`/inventory/site/${inspection.siteAtTimeOfInspection}`}>
                        {inspection.siteAtTimeOfInspection}
                      </Link>
                    ) : null}
                  </TableCell>
                  <TableCell>{inspection.structureName}</TableCell>
                  {/* "DPG — Prince George Natural Resource District", as nr-frep renders an org
                      unit. Legacy shows the bare code with the name in a title attribute, which its
                      6%-wide column forced — a tooltip only reaches a user who already suspects
                      there is more to see, and never reaches one reading on a touchscreen. */}
                  <TableCell>
                    {[inspection.orgUnitCode, inspection.orgUnitName].filter(Boolean).join(' — ')}
                  </TableCell>
                  <TableCell>{inspection.forestServiceRoad}</TableCell>
                  <TableCell className="inspection-search__numeric">
                    {inspection.pointOfCommencementDistance}
                  </TableCell>
                  <TableCell>{inspection.crossingName}</TableCell>
                  {/* "R00123-01". Joined rather than interpolated so a row missing one half shows
                      the other on its own instead of a stranded hyphen, and a row missing both
                      shows an empty cell — the same treatment the district column gets above. */}
                  <TableCell>
                    {[inspection.forestFileId, inspection.roadSectionId].filter(Boolean).join('-')}
                  </TableCell>
                  {canDelete && (
                    <TableCell>
                      {/* Only an offline inspection can be deleted from this screen — legacy wraps
                          the icon in both the status test and the privilege test. A row that is not
                          offline gets an empty cell rather than a disabled button, which would
                          invite a click that can never work. */}
                      {isOffline(inspection.inspectionReportStatusCode) && (
                        <Button
                          // danger--ghost rather than ghost with a recoloured icon: Carbon's danger
                          // kinds carry the whole interaction, so the control reads as destructive
                          // before it is pressed, not only in the dialog that follows.
                          kind="danger--ghost"
                          size="sm"
                          hasIconOnly
                          renderIcon={TrashCan}
                          iconDescription={`Delete offline inspection ${inspection.id}`}
                          data-testid={`inspection-delete-${inspection.id}`}
                          onClick={() => onDelete(inspection)}
                        />
                      )}
                    </TableCell>
                  )}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Legacy paginates server-side via its own <cbr:pagination> tag, which posts the form back
          with page/pageSize. Carbon's Pagination is the same contract in a different shape — the
          page and size come back out and the caller re-queries. */}
      <Pagination
        data-testid="inspection-search-pagination"
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

export default InspectionSearchResults;
