import { Add, Edit, Tools, TrashCan } from '@carbon/icons-react';
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
import { describe, money, number } from './format';
import RepairDialog from './RepairDialog';
import UserAudits from './UserAudits';

import type { RepairView, StructureRepair } from './repairsResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useDeleteStructureRepair, useStructureRepairs } from '@/hooks/useStructureSearch';
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
type TableProps = {
  rows: StructureRepair[];
  view: RepairView;
  /** Offers Edit — Level 1 and up. */
  canEdit: boolean;
  /** Offers Delete — the destructive privilege. */
  canDelete: boolean;
  onEdit: (repair: StructureRepair) => void;
  onDelete: (repair: StructureRepair) => void;
};

const RepairsTable: FC<TableProps> = ({ rows, view, canEdit, canDelete, onEdit, onDelete }) => {
  // The Actions column only when there is an action to offer, as Site Search's.
  const hasActions = canEdit || canDelete;
  if (rows.length === 0) {
    return (
      <p className="structure-detail__empty">
        {view === 'OUTSTANDING' ? 'No outstanding repairs.' : 'No repairs have been recorded.'}
      </p>
    );
  }
  return (
    // Eleven columns: the headings wrap, and the short values keep one line, so the table and its
    // Actions fit the card; anything wider scrolls inside the card rather than widening the page.
    <div className="structure-detail__table-scroll structure-detail__repairs-scroll">
      <Table size="md" useZebraStyles aria-label="Repairs">
        <TableHead>
          <TableRow>
            <TableHeader>Repair Number</TableHeader>
            <TableHeader>Status</TableHeader>
            <TableHeader>Repair Type</TableHeader>
            <TableHeader>User Audits</TableHeader>
            <TableHeader>Inspection Date</TableHeader>
            <TableHeader>Priority</TableHeader>
            <TableHeader>Completed Date</TableHeader>
            <TableHeader>Estimate ($)</TableHeader>
            <TableHeader>Actual ($)</TableHeader>
            <TableHeader>Quantity</TableHeader>
            <TableHeader>Description</TableHeader>
            {/* Only for a user who may act, as the other tables' Actions columns: an action that
                cannot be performed is not shown, and an empty column is noise. */}
            {hasActions && <TableHeader>Actions</TableHeader>}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((repair) => (
            <TableRow key={repair.id} data-testid={`structure-repair-${repair.id}`}>
              <TableCell className="structure-detail__nowrap">{number(repair.number)}</TableCell>
              <TableCell className="structure-detail__nowrap">{describe(repair.status)}</TableCell>
              <TableCell>{describe(repair.type)}</TableCell>
              {/* Wraps, as Repair Type and Priority do: three columns let go of their single line so
                  the table, Actions included, fits its card. */}
              <TableCell>
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
              <TableCell>{describe(repair.priority)}</TableCell>
              <TableCell className="structure-detail__nowrap">
                {formatShortDate(repair.completedDate)}
              </TableCell>
              <TableCell className="structure-detail__nowrap">{money(repair.estimate)}</TableCell>
              <TableCell className="structure-detail__nowrap">{money(repair.actualCost)}</TableCell>
              {/* The number only, as legacy's column: the unit is shown beside Qty in the dialog. */}
              <TableCell className="structure-detail__nowrap">{number(repair.quantity)}</TableCell>
              <TableCell className="structure-detail__comment">{repair.description}</TableCell>
              {hasActions && (
                <TableCell className="structure-detail__nowrap">
                  {/* Side by side when the table has room, stacked when it has not — eleven
                      columns and both buttons in a row need a wide screen. */}
                  <div className="structure-detail__repair-actions">
                    {canEdit && (
                      // Ghost, the link-blue of an action that changes nothing until saved; Delete
                      // under it is the red one.
                      <Button
                        kind="ghost"
                        size="sm"
                        renderIcon={Edit}
                        data-testid={`structure-repair-edit-${repair.id}`}
                        onClick={() => onEdit(repair)}
                      >
                        Edit{' '}
                        <span className="cds--visually-hidden">repair {number(repair.number)}</span>
                      </Button>
                    )}
                    {/* danger--ghost, as every Delete in the app: red at rest and on hover. */}
                    {canDelete && (
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        renderIcon={TrashCan}
                        data-testid={`structure-repair-delete-${repair.id}`}
                        onClick={() => onDelete(repair)}
                      >
                        Delete{' '}
                        <span className="cds--visually-hidden">repair {number(repair.number)}</span>
                      </Button>
                    )}
                  </div>
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
 * Legacy's Repairs tab (`repairTab.jsp`): the structure's repairs, outstanding ones by default or
 * all of them, a page at a time from the server. Edit (Level 1 and up) and Delete (Level 2 and
 * up) from each row's Actions; Add (Level 1 and up) over the table.
 */
const RepairsTab: FC<Props> = ({ structureId, opened }) => {
  const { canEdit, canDelete } = useAuthorization();
  const { display } = useNotification();
  const deleteRepair = useDeleteStructureRepair(structureId);
  /** The repair whose delete is being confirmed, if any. */
  const [pendingDelete, setPendingDelete] = useState<StructureRepair | null>(null);
  /** The repair being edited, `'new'` while one is being added, or null. */
  const [editing, setEditing] = useState<StructureRepair | 'new' | null>(null);
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

        {/* Over the table, at its right, after the options that decide what it lists — as the
            Monitoring tab's Add. Only for a user who may add, as legacy's Level 1 button. */}
        {canEdit && (
          <div className="structure-detail__table-actions">
            <Button
              kind="tertiary"
              size="md"
              renderIcon={Add}
              data-testid="structure-repair-add"
              onClick={() => setEditing('new')}
            >
              Add repair
            </Button>
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
            <RepairsTable
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

      {editing !== null && (
        <RepairDialog
          key={editing === 'new' ? 'new' : editing.id}
          structureId={structureId}
          repair={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      )}

      {/* Legacy's window.confirm(), as the app's other deletes ask. */}
      <DestructiveModal
        open={pendingDelete !== null}
        title="Delete repair"
        message={`Are you sure you would like to delete repair ${number(
          pendingDelete?.number,
        )}? This cannot be undone.`}
        confirmButtonText="Delete"
        loading={deleteRepair.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const repair = pendingDelete;
          if (repair === null) return;
          deleteRepair.mutate(repair.id, {
            onSuccess: () => {
              setPendingDelete(null);
              display({
                kind: 'success',
                title: `Repair ${number(repair.number)} deleted`,
                timeout: 4000,
              });
            },
            onError: (error) => {
              setPendingDelete(null);
              // A toast, as the app's other deletes report a failure: the row is still there.
              display({
                kind: 'error',
                title: 'The repair was not deleted',
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

export default RepairsTab;
