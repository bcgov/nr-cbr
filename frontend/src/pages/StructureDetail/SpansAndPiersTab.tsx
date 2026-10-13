import { Add, Column as PierIcon, Edit, Ruler, TrashCan } from '@carbon/icons-react';
import {
  Button,
  InlineNotification,
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

import Card from './Card';
import { describe, number } from './format';
import PierDialog from './PierDialog';
import SpanDialog from './SpanDialog';

import type { BridgePier, BridgeSpan } from './spansAndPiersResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import {
  useDeletePier,
  useDeleteSpan,
  useStructureSpansAndPiers,
} from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';

type Props = {
  structureId: string;
  /** Whether the structure has its bridge record, which spans and piers hang from. */
  hasBridgeRecord: boolean;
  /** True once the tab has been opened; nothing is fetched before. */
  opened: boolean;
};

/** What is being deleted, waiting on the user's confirmation. */
type PendingDelete = { kind: 'span'; item: BridgeSpan } | { kind: 'pier'; item: BridgePier };

/** Edit and Delete for one row: ghost and red ghost, as the app's other tables. */
const RowActions: FC<{
  kind: 'span' | 'pier';
  item: BridgeSpan | BridgePier;
  onEdit: () => void;
  onDelete: () => void;
}> = ({ kind, item, onEdit, onDelete }) => (
  <TableCell className="structure-detail__nowrap">
    <Button
      kind="ghost"
      size="sm"
      renderIcon={Edit}
      data-testid={`structure-${kind}-edit-${item.id}`}
      onClick={onEdit}
    >
      Edit{' '}
      <span className="cds--visually-hidden">
        {kind} {number(item.number)}
      </span>
    </Button>
    <Button
      kind="danger--ghost"
      size="sm"
      renderIcon={TrashCan}
      data-testid={`structure-${kind}-delete-${item.id}`}
      onClick={onDelete}
    >
      Delete{' '}
      <span className="cds--visually-hidden">
        {kind} {number(item.number)}
      </span>
    </Button>
  </TableCell>
);

/**
 * Legacy's Spans & Piers tab (`pierSpanTab.jsp`), shown for bridges only: the spans by span
 * number, then the piers by pier number with their type.
 *
 * <p>Level 2 adds, changes and deletes them, as legacy's buttons: "Add span" and "Add pier" in the
 * cards' headers, Edit and Delete on each row, each change in a dialog and written at once. A delete
 * asks first, as legacy's confirm(); one the bridge will not allow — an inspected span or pier, the
 * last span, or one the piers need — is refused with legacy's reason in a toast.
 */
const SpansAndPiersTab: FC<Props> = ({ structureId, hasBridgeRecord, opened }) => {
  const loaded = useStructureSpansAndPiers(structureId, opened);
  // Level 2, as legacy's /level2Access on every one of the tab's buttons.
  const { canDelete: canChange } = useAuthorization();
  const { display } = useNotification();
  /** The open dialog, if any: a row to edit, or `'new'` to add one. */
  const [editingSpan, setEditingSpan] = useState<BridgeSpan | 'new' | null>(null);
  const [editingPier, setEditingPier] = useState<BridgePier | 'new' | null>(null);
  const [pendingDelete, setPendingDelete] = useState<PendingDelete | null>(null);
  const deleteSpan = useDeleteSpan(structureId);
  const deletePier = useDeletePier(structureId);
  const deleting = deleteSpan.isPending || deletePier.isPending;

  if (loaded.isError) {
    return (
      <InlineNotification
        kind="error"
        lowContrast
        hideCloseButton
        title="Spans and piers could not be loaded"
        subtitle={apiErrorMessage(loaded.error, 'Try again in a moment.')}
        data-testid="structure-spans-piers-error"
      />
    );
  }

  if (!loaded.data) {
    // The test id on a wrapper: Carbon repeats a paragraph skeleton's props on every line.
    return (
      <div data-testid="structure-spans-piers-loading">
        <SkeletonText paragraph lineCount={6} />
      </div>
    );
  }

  const { spans, piers } = loaded.data;

  return (
    <div className="structure-detail__tab-panel" data-testid="structure-spans-piers-tab">
      <Card
        title="Spans"
        icon={Ruler}
        testId="structure-section-spans"
        action={
          canChange && (
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Add}
              data-testid="structure-span-add"
              onClick={() => setEditingSpan('new')}
            >
              Add span
            </Button>
          )
        }
      >
        <p className="structure-detail__note structure-detail__note--first">
          Span # is numbered Left Bank to Right Bank (Looking Downstream).
        </p>
        {spans.length === 0 ? (
          <p className="structure-detail__empty">No spans.</p>
        ) : (
          <Table
            size="md"
            useZebraStyles
            aria-label="Spans"
            className="structure-detail__bridge-table"
          >
            <TableHead>
              <TableRow>
                <TableHeader>Span #</TableHeader>
                <TableHeader>Length (metres)</TableHeader>
                {canChange && <TableHeader>Actions</TableHeader>}
              </TableRow>
            </TableHead>
            <TableBody>
              {spans.map((span) => (
                <TableRow key={span.id} data-testid={`structure-span-${span.id}`}>
                  <TableCell>{number(span.number)}</TableCell>
                  <TableCell>{number(span.lengthMetres)}</TableCell>
                  {canChange && (
                    <RowActions
                      kind="span"
                      item={span}
                      onEdit={() => setEditingSpan(span)}
                      onDelete={() => setPendingDelete({ kind: 'span', item: span })}
                    />
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <Card
        title="Piers"
        icon={PierIcon}
        testId="structure-section-piers"
        action={
          canChange && (
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Add}
              data-testid="structure-pier-add"
              onClick={() => setEditingPier('new')}
            >
              Add pier
            </Button>
          )
        }
      >
        {piers.length === 0 ? (
          <p className="structure-detail__empty">No piers.</p>
        ) : (
          <Table
            size="md"
            useZebraStyles
            aria-label="Piers"
            className="structure-detail__bridge-table"
          >
            <TableHead>
              <TableRow>
                <TableHeader>Pier #</TableHeader>
                <TableHeader>Type</TableHeader>
                {canChange && <TableHeader>Actions</TableHeader>}
              </TableRow>
            </TableHead>
            <TableBody>
              {piers.map((pier) => (
                <TableRow key={pier.id} data-testid={`structure-pier-${pier.id}`}>
                  <TableCell>{number(pier.number)}</TableCell>
                  <TableCell>{describe(pier.type)}</TableCell>
                  {canChange && (
                    <RowActions
                      kind="pier"
                      item={pier}
                      onEdit={() => setEditingPier(pier)}
                      onDelete={() => setPendingDelete({ kind: 'pier', item: pier })}
                    />
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      {editingSpan !== null && (
        <SpanDialog
          key={editingSpan === 'new' ? 'new' : editingSpan.id}
          structureId={structureId}
          span={editingSpan === 'new' ? null : editingSpan}
          spans={spans}
          hasBridgeRecord={hasBridgeRecord}
          onClose={() => setEditingSpan(null)}
        />
      )}
      {editingPier !== null && (
        <PierDialog
          key={editingPier === 'new' ? 'new' : editingPier.id}
          structureId={structureId}
          pier={editingPier === 'new' ? null : editingPier}
          piers={piers}
          spanCount={spans.length}
          hasBridgeRecord={hasBridgeRecord}
          onClose={() => setEditingPier(null)}
        />
      )}

      {/* Legacy's confirm(), as the app's other deletes ask. */}
      <DestructiveModal
        open={pendingDelete !== null}
        title={pendingDelete?.kind === 'pier' ? 'Delete pier' : 'Delete span'}
        message={`Are you sure you would like to delete ${pendingDelete?.kind ?? 'span'} ${number(
          pendingDelete?.item.number,
        )}? This cannot be undone.`}
        confirmButtonText="Delete"
        loading={deleting}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const target = pendingDelete;
          if (target === null) return;
          const label = `${target.kind === 'span' ? 'Span' : 'Pier'} ${number(target.item.number)}`;
          const mutation = target.kind === 'span' ? deleteSpan : deletePier;
          mutation.mutate(target.item.id, {
            onSuccess: () => {
              setPendingDelete(null);
              display({ kind: 'success', title: `${label} deleted`, timeout: 4000 });
            },
            onError: (error) => {
              setPendingDelete(null);
              // A toast, as the app's other deletes report one: the row is still where it was, and
              // a refusal's detail is legacy's reason — "Span can't be deleted, it is associated
              // with an inspection."
              display({
                kind: 'error',
                title: `${label} was not deleted`,
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

export default SpansAndPiersTab;
