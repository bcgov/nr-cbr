import { Edit, TrashCan, View } from '@carbon/icons-react';
import {
  Button,
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

import DestructiveModal from '@/components/core/DestructiveModal';
import ExternalLink from '@/components/core/ExternalLink';

import Card from './Card';
import EditMonitorModal from './EditMonitorModal';
import { describe, number } from './format';
import UserAudits from './UserAudits';

import type { MonitorView, StructureMonitor } from './monitorsResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useDeleteStructureMonitor, useStructureMonitors } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';

type Props = {
  structureId: string;
  /** True once the tab has been opened; nothing is fetched before. */
  opened: boolean;
};

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZES = [10, 20, 50];

type TableProps = {
  rows: StructureMonitor[];
  view: MonitorView;
  /** Offers Edit — Level 1 and up. */
  canEdit: boolean;
  /** Offers Delete — the destructive privilege. */
  canDelete: boolean;
  onEdit: (monitor: StructureMonitor) => void;
  onDelete: (monitor: StructureMonitor) => void;
};

/** The page of monitoring items, or a line saying there are none in this view. */
const MonitorsTable: FC<TableProps> = ({ rows, view, canEdit, canDelete, onEdit, onDelete }) => {
  // The Actions column only when there is an action to offer, as Site Search's.
  const hasActions = canEdit || canDelete;
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
            {/* Only for a user who may act, as Site Search's is: an action that cannot be
                performed is not shown, and an empty column is noise. */}
            {hasActions && <TableHeader>Actions</TableHeader>}
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
              {hasActions && (
                <TableCell className="structure-detail__nowrap">
                  {canEdit && (
                    // Ghost, the link-blue of an action that changes nothing until saved; Delete
                    // beside it is the red one.
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={Edit}
                      data-testid={`structure-monitor-edit-${monitor.id}`}
                      onClick={() => onEdit(monitor)}
                    >
                      Edit{' '}
                      <span className="cds--visually-hidden">
                        monitoring item {number(monitor.number)}
                      </span>
                    </Button>
                  )}
                  {/* danger--ghost, as Site Search's delete: red at rest and on hover, so it reads
                      as destructive before it is pressed. */}
                  {canDelete && (
                    <Button
                      kind="danger--ghost"
                      size="sm"
                      renderIcon={TrashCan}
                      data-testid={`structure-monitor-delete-${monitor.id}`}
                      onClick={() => onDelete(monitor)}
                    >
                      Delete{' '}
                      <span className="cds--visually-hidden">
                        monitoring item {number(monitor.number)}
                      </span>
                    </Button>
                  )}
                </TableCell>
              )}
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
  const { canEdit, canDelete } = useAuthorization();
  /** The item being edited, if any; the dialog is mounted only then. */
  const [editing, setEditing] = useState<StructureMonitor | null>(null);
  const { display } = useNotification();
  const deleteMonitor = useDeleteStructureMonitor(structureId);
  /** The item whose delete is being confirmed, if any. */
  const [pendingDelete, setPendingDelete] = useState<StructureMonitor | null>(null);
  const [view, setView] = useState<MonitorView>('OUTSTANDING');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  /** Legacy's "Show Inspections before the Superstructure Install Date". */
  const [includeBeforeInstall, setIncludeBeforeInstall] = useState(false);
  // Carbon's Pagination is one-based; the backend is zero-based.
  const loaded = useStructureMonitors(
    structureId,
    opened,
    view,
    page - 1,
    pageSize,
    includeBeforeInstall,
  );

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

        {/* As legacy's, and as the inspection table's: items raised by an inspection from before
            the superstructure went in are left out until asked for. Shown while there are any,
            or while they are being shown. */}
        {((loaded.data?.beforeInstallCount ?? 0) > 0 || includeBeforeInstall) && (
          <div className="structure-detail__history-toggle">
            <Checkbox
              id="structure-show-early-monitors"
              labelText={`Show monitoring items from before the superstructure was installed (${loaded.data?.beforeInstallCount ?? 0})`}
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
            <MonitorsTable
              rows={loaded.data.page.content}
              view={view}
              canEdit={canEdit}
              canDelete={canDelete}
              onEdit={setEditing}
              onDelete={setPendingDelete}
            />
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

      {editing && (
        <EditMonitorModal
          key={editing.id}
          structureId={structureId}
          monitor={editing}
          onClose={() => setEditing(null)}
        />
      )}

      {/* Legacy's window.confirm(), as the app's other deletes ask. */}
      <DestructiveModal
        open={pendingDelete !== null}
        title="Delete monitoring item"
        message={`Are you sure you would like to delete monitoring item ${number(
          pendingDelete?.number,
        )}? This cannot be undone.`}
        confirmButtonText="Delete"
        loading={deleteMonitor.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const monitor = pendingDelete;
          if (monitor === null) return;
          deleteMonitor.mutate(monitor.id, {
            onSuccess: () => {
              setPendingDelete(null);
              display({
                kind: 'success',
                title: `Monitoring item ${number(monitor.number)} deleted`,
                timeout: 4000,
              });
            },
            onError: (error) => {
              setPendingDelete(null);
              // A toast, as Site Search reports a failed delete: nothing on screen changed, and the
              // row is still where the user left it.
              display({
                kind: 'error',
                title: 'The monitoring item was not deleted',
                subtitle: apiErrorMessage(
                  error,
                  'Try again, or contact support if this continues.',
                ),
                timeout: 0,
              });
            },
          });
        }}
      />
    </div>
  );
};

export default MonitoringTab;
