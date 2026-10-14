import { Add, RecentlyViewed, TrashCan } from '@carbon/icons-react';
import {
  Button,
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
import ReadOnlyField from '@/components/core/ReadOnlyField';

import Card from './Card';
import ReplacementDialog from './ReplacementDialog';
import StructureLinks from './StructureLinks';

import type { StructureRef } from './structureResponse';
import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useDeleteReplacement } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';

type Props = {
  structureId: string;
  structureName: string | null;
  /** The structures this one replaced. */
  replaced: StructureRef[];
  /** The structures that replaced this one. */
  replacedBy: StructureRef[];
  /** True while a card on the tab is being edited, when Add and Delete wait. */
  otherEditing: boolean;
};

const nameOf = (structure: StructureRef) => structure.structureName || structure.id;

/**
 * The Details tab's Replacement History, as legacy: its pop-up's table — this structure, then each
 * structure it replaced, with a Delete for a P.Eng (`structure.jsp:4575-4650`) — and, from the
 * page's fieldset, the structures that replaced this one, as links. Those are recorded and deleted
 * from their own pages, as legacy.
 *
 * <p>A P.Eng adds a replacement from the header — legacy's "Structure # replaces Structure #" form
 * — and deletes one from its row. Each is written at once; there is no edit, as legacy had none.
 */
const ReplacementHistoryCard: FC<Props> = ({
  structureId,
  structureName,
  replaced,
  replacedBy,
  otherEditing,
}) => {
  const { isPeng } = useAuthorization();
  const { display } = useNotification();
  const [adding, setAdding] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<StructureRef | null>(null);
  const deleteReplacement = useDeleteReplacement(structureId);

  return (
    <Card
      title="Replacement History"
      icon={RecentlyViewed}
      testId="structure-section-replacement-history"
      action={
        isPeng && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Add}
            disabled={otherEditing || !structureName}
            data-testid="replacement-add"
            onClick={() => setAdding(true)}
          >
            Add replacement
          </Button>
        )
      }
    >
      {replaced.length === 0 ? (
        <p className="structure-detail__empty">
          There are no replacement details for this structure.
        </p>
      ) : (
        <div className="structure-detail__replacement-table">
          <Table size="md" useZebraStyles aria-label="Replacement history">
            <TableHead>
              <TableRow>
                <TableHeader>This structure was replaced by structure #</TableHeader>
                <TableHeader>This structure replaced structure #</TableHeader>
                {isPeng && <TableHeader>Actions</TableHeader>}
              </TableRow>
            </TableHead>
            <TableBody>
              {replaced.map((older) => (
                <TableRow key={older.id} data-testid={`replacement-${older.id}`}>
                  <TableCell>{structureName}</TableCell>
                  <TableCell>
                    <ExternalLink to={`/inventory/structure/${older.id}`}>
                      {nameOf(older)}
                    </ExternalLink>
                  </TableCell>
                  {isPeng && (
                    <TableCell className="structure-detail__nowrap">
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        renderIcon={TrashCan}
                        disabled={otherEditing}
                        data-testid={`replacement-delete-${older.id}`}
                        onClick={() => setPendingDelete(older)}
                      >
                        Delete <span className="cds--visually-hidden">replacement</span>
                      </Button>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <div className="structure-detail__fields">
        <ReadOnlyField
          label="This structure was replaced by structure #"
          value={replacedBy.length > 0 ? <StructureLinks structures={replacedBy} /> : null}
        />
      </div>

      {adding && structureName && (
        <ReplacementDialog
          structureId={structureId}
          structureName={structureName}
          replaced={replaced}
          replacedBy={replacedBy}
          onClose={() => setAdding(false)}
        />
      )}

      {/* Asked first, as legacy's "Are you sure…". */}
      <DestructiveModal
        open={pendingDelete !== null}
        title="Delete replacement"
        message={
          pendingDelete
            ? `Are you sure you would like to delete the replacement of ${nameOf(
                pendingDelete,
              )} by ${structureName}? This cannot be undone.`
            : ''
        }
        confirmButtonText="Delete"
        loading={deleteReplacement.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const target = pendingDelete;
          if (target === null) return;
          deleteReplacement.mutate(target.id, {
            onSuccess: () => {
              setPendingDelete(null);
              display({ kind: 'success', title: 'Replacement deleted', timeout: 4000 });
            },
            onError: (error) => {
              setPendingDelete(null);
              display({
                kind: 'error',
                title: 'The replacement was not deleted',
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
    </Card>
  );
};

export default ReplacementHistoryCard;
