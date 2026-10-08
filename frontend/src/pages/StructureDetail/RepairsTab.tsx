import { Tools } from '@carbon/icons-react';
import {
  Checkbox,
  InlineNotification,
  Pagination,
  RadioButton,
  RadioButtonGroup,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useState } from 'react';

import ExternalLink from '@/components/core/ExternalLink';

import Card from './Card';
import { describe, money, number } from './format';
import UserAudits from './UserAudits';

import type { RepairView, StructureRepair } from './repairsResponse';
import type { FC } from 'react';

import { useStructureRepairs } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';

type Props = {
  structureId: string;
  /** True once the tab has been opened; nothing is fetched before. */
  opened: boolean;
};

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZES = [10, 20, 50];

/** The page of repairs, or a line saying there are none in this view. */
const RepairsTable: FC<{ rows: StructureRepair[]; view: RepairView }> = ({ rows, view }) => {
  if (rows.length === 0) {
    return (
      <p className="structure-detail__empty">
        {view === 'OUTSTANDING' ? 'No outstanding repairs.' : 'No repairs have been recorded.'}
      </p>
    );
  }
  return (
    // Eleven columns: the table scrolls inside its card rather than widening the page.
    <div className="structure-detail__table-scroll">
      <Table size="md" useZebraStyles aria-label="Repairs">
        <TableHead>
          <TableRow>
            <TableHeader className="structure-detail__nowrap">Repair Number</TableHeader>
            <TableHeader className="structure-detail__nowrap">Status</TableHeader>
            <TableHeader className="structure-detail__nowrap">Repair Type</TableHeader>
            <TableHeader className="structure-detail__nowrap">User Audits</TableHeader>
            <TableHeader className="structure-detail__nowrap">Inspection Date</TableHeader>
            <TableHeader className="structure-detail__nowrap">Priority</TableHeader>
            <TableHeader className="structure-detail__nowrap">Completed Date</TableHeader>
            <TableHeader className="structure-detail__nowrap">Estimate ($)</TableHeader>
            <TableHeader className="structure-detail__nowrap">Actual ($)</TableHeader>
            <TableHeader className="structure-detail__nowrap">Quantity</TableHeader>
            <TableHeader>Description</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((repair) => (
            <TableRow key={repair.id} data-testid={`structure-repair-${repair.id}`}>
              <TableCell className="structure-detail__nowrap">{number(repair.number)}</TableCell>
              <TableCell className="structure-detail__nowrap">{describe(repair.status)}</TableCell>
              <TableCell className="structure-detail__nowrap">{describe(repair.type)}</TableCell>
              <TableCell className="structure-detail__nowrap">
                <UserAudits item={repair} />
              </TableCell>
              <TableCell className="structure-detail__nowrap">
                {/* Its own tab, as the inspection table opens one. */}
                {repair.inspectionId ? (
                  <ExternalLink to={`/inspection/${repair.inspectionId}`}>
                    {formatShortDate(repair.inspectionDate) || repair.inspectionId}
                  </ExternalLink>
                ) : null}
              </TableCell>
              <TableCell className="structure-detail__nowrap">
                {describe(repair.priority)}
              </TableCell>
              <TableCell className="structure-detail__nowrap">
                {formatShortDate(repair.completedDate)}
              </TableCell>
              <TableCell className="structure-detail__nowrap">{money(repair.estimate)}</TableCell>
              <TableCell className="structure-detail__nowrap">{money(repair.actualCost)}</TableCell>
              <TableCell className="structure-detail__nowrap">
                {[number(repair.quantity), repair.quantity === null ? '' : repair.unit]
                  .filter(Boolean)
                  .join(' ')}
              </TableCell>
              <TableCell className="structure-detail__comment">{repair.description}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
};

/**
 * Legacy's Repairs tab (`repairTab.jsp`): the structure's repairs, outstanding ones by default or
 * all of them, a page at a time from the server. Read-only for now; legacy's Add, Edit (Level 1)
 * and Delete come with the page's editing.
 */
const RepairsTab: FC<Props> = ({ structureId, opened }) => {
  const [view, setView] = useState<RepairView>('OUTSTANDING');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  /** Legacy's "Show Inspections before the Superstructure Install Date". */
  const [includeBeforeInstall, setIncludeBeforeInstall] = useState(false);
  // Carbon's Pagination is one-based; the backend is zero-based.
  const loaded = useStructureRepairs(
    structureId,
    opened,
    view,
    page - 1,
    pageSize,
    includeBeforeInstall,
  );

  return (
    <div className="structure-detail__tab-panel" data-testid="structure-repairs-tab">
      <Card title="Repairs" icon={Tools} testId="structure-section-repairs">
        <div className="structure-detail__history-toggle">
          <RadioButtonGroup
            legendText="Choose Viewing Option"
            name="structure-repairs-view"
            orientation="horizontal"
            valueSelected={view}
            onChange={(selected) => {
              setView(selected as RepairView);
              setPage(1);
            }}
          >
            <RadioButton
              id="structure-repairs-outstanding"
              labelText="Show Outstanding"
              value="OUTSTANDING"
            />
            <RadioButton id="structure-repairs-all" labelText="All Items" value="ALL" />
          </RadioButtonGroup>
        </div>

        {/* As legacy's, and as the inspection table's: items raised by an inspection from before
            the superstructure went in are left out until asked for. Shown while there are any,
            or while they are being shown. */}
        {((loaded.data?.beforeInstallCount ?? 0) > 0 || includeBeforeInstall) && (
          <div className="structure-detail__history-toggle">
            <Checkbox
              id="structure-show-early-repairs"
              labelText={`Show repairs from before the superstructure was installed (${loaded.data?.beforeInstallCount ?? 0})`}
              checked={includeBeforeInstall}
              onChange={(_, { checked }) => {
                setIncludeBeforeInstall(checked);
                setPage(1);
              }}
            />
          </div>
        )}

        {loaded.isError && (
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="Repairs could not be loaded"
            subtitle={apiErrorMessage(loaded.error, 'Try again in a moment.')}
            data-testid="structure-repairs-error"
          />
        )}
        {!loaded.data && !loaded.isError && (
          <div data-testid="structure-repairs-loading">
            <SkeletonText paragraph lineCount={4} />
          </div>
        )}
        {loaded.data && (
          <>
            <RepairsTable rows={loaded.data.page.content} view={view} />
            <Pagination
              page={page}
              pageSize={pageSize}
              pageSizes={PAGE_SIZES}
              totalItems={loaded.data.page.totalElements}
              onChange={({ page: nextPage, pageSize: nextPageSize }) => {
                setPage(nextPage);
                setPageSize(nextPageSize);
              }}
            />
          </>
        )}
      </Card>
    </div>
  );
};

export default RepairsTab;
