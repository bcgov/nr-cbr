import { View } from '@carbon/icons-react';
import {
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
import { describe, number } from './format';
import UserAudits from './UserAudits';

import type { MonitorView, StructureMonitor } from './monitorsResponse';
import type { FC } from 'react';

import { useStructureMonitors } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';

type Props = {
  structureId: string;
  /** True once the tab has been opened; nothing is fetched before. */
  opened: boolean;
};

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZES = [10, 20, 50];

/** The page of monitoring items, or a line saying there are none in this view. */
const MonitorsTable: FC<{ rows: StructureMonitor[]; view: MonitorView }> = ({ rows, view }) => {
  if (rows.length === 0) {
    return (
      <p className="structure-detail__empty">
        {view === 'OUTSTANDING'
          ? 'No outstanding monitoring items.'
          : 'No monitoring items have been recorded.'}
      </p>
    );
  }
  return (
    <div className="structure-detail__table-scroll">
      <Table size="md" useZebraStyles aria-label="Monitoring">
        <TableHead>
          <TableRow>
            <TableHeader className="structure-detail__nowrap">Monitor Number</TableHeader>
            <TableHeader className="structure-detail__nowrap">Status</TableHeader>
            <TableHeader className="structure-detail__nowrap">User Audits</TableHeader>
            <TableHeader className="structure-detail__nowrap">Inspection Date</TableHeader>
            <TableHeader>Description</TableHeader>
            <TableHeader>Monitoring Frequency</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((monitor) => (
            <TableRow key={monitor.id} data-testid={`structure-monitor-${monitor.id}`}>
              <TableCell className="structure-detail__nowrap">{number(monitor.number)}</TableCell>
              <TableCell className="structure-detail__nowrap">{describe(monitor.status)}</TableCell>
              <TableCell className="structure-detail__nowrap">
                <UserAudits item={monitor} />
              </TableCell>
              <TableCell className="structure-detail__nowrap">
                {/* Its own tab, as the inspection table opens one. */}
                {monitor.inspectionId ? (
                  <ExternalLink to={`/inspection/${monitor.inspectionId}`}>
                    {formatShortDate(monitor.inspectionDate) || monitor.inspectionId}
                  </ExternalLink>
                ) : null}
              </TableCell>
              <TableCell className="structure-detail__comment">{monitor.description}</TableCell>
              <TableCell className="structure-detail__comment">
                {[describe(monitor.frequency), monitor.frequencyComment]
                  .filter(Boolean)
                  .join(' — ')}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
};

/**
 * Legacy's Monitoring tab (`monitorTab.jsp`): the structure's monitoring items, outstanding ones by
 * default or all of them, a page at a time from the server. Read-only for now; legacy's Add, Edit
 * (Level 1) and Delete come with the page's editing.
 *
 * <p>Legacy's one viewing option drove both this tab and Repairs; each tab has its own here, so
 * choosing All on one does not change what the other lists.
 */
const MonitoringTab: FC<Props> = ({ structureId, opened }) => {
  const [view, setView] = useState<MonitorView>('OUTSTANDING');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  // Carbon's Pagination is one-based; the backend is zero-based.
  const loaded = useStructureMonitors(structureId, opened, view, page - 1, pageSize);

  return (
    <div className="structure-detail__tab-panel" data-testid="structure-monitoring-tab">
      <Card title="Monitoring" icon={View} testId="structure-section-monitoring">
        <div className="structure-detail__history-toggle">
          <RadioButtonGroup
            legendText="Choose Viewing Option"
            name="structure-monitors-view"
            orientation="horizontal"
            valueSelected={view}
            onChange={(selected) => {
              setView(selected as MonitorView);
              setPage(1);
            }}
          >
            <RadioButton
              id="structure-monitors-outstanding"
              labelText="Show Outstanding"
              value="OUTSTANDING"
            />
            <RadioButton id="structure-monitors-all" labelText="All Items" value="ALL" />
          </RadioButtonGroup>
        </div>

        {loaded.isError && (
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="Monitoring items could not be loaded"
            subtitle={apiErrorMessage(loaded.error, 'Try again in a moment.')}
            data-testid="structure-monitors-error"
          />
        )}
        {!loaded.data && !loaded.isError && (
          <div data-testid="structure-monitors-loading">
            <SkeletonText paragraph lineCount={4} />
          </div>
        )}
        {loaded.data && (
          <>
            <MonitorsTable rows={loaded.data.content} view={view} />
            <Pagination
              page={page}
              pageSize={pageSize}
              pageSizes={PAGE_SIZES}
              totalItems={loaded.data.totalElements}
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

export default MonitoringTab;
